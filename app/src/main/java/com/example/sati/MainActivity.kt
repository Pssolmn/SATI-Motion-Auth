package com.example.sati

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.sati.ui.theme.SATITheme
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler

enum class Screen {
    Login, Home, Verify
}

// SharedPreferences Helper
class SecurityPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cookie_bank_security", Context.MODE_PRIVATE)
    
    fun getFailedAttempts(): Int = prefs.getInt("failed_attempts", 0)
    
    fun getLockoutTimestamp(): Long = prefs.getLong("lockout_timestamp", 0L)
    
    fun incrementFailedAttempts() {
        val currentAttempts = getFailedAttempts() + 1
        if (currentAttempts >= 3) {
            // Lockout for 60 seconds (1 minute for demo)
            val lockoutTime = System.currentTimeMillis() + 60_000L
            prefs.edit()
                .putInt("failed_attempts", 0)
                .putLong("lockout_timestamp", lockoutTime)
                .apply()
        } else {
            prefs.edit().putInt("failed_attempts", currentAttempts).apply()
        }
    }
    
    fun resetFailedAttempts() {
        prefs.edit()
            .putInt("failed_attempts", 0)
            .putLong("lockout_timestamp", 0L)
            .apply()
    }
    
    fun isLockedOut(): Boolean {
        val lockoutTimestamp = getLockoutTimestamp()
        return lockoutTimestamp > 0 && System.currentTimeMillis() < lockoutTimestamp
    }
    
    fun getRemainingLockoutTime(): Long {
        val lockoutTimestamp = getLockoutTimestamp()
        return if (lockoutTimestamp > 0) {
            (lockoutTimestamp - System.currentTimeMillis()).coerceAtLeast(0)
        } else {
            0
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Security Feature: Prevent screen recording/remote access
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        // Initialize Sensor Manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        // Initialize Vibrator
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        // Initialize ToneGenerator for beep sounds
        val toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
        
        enableEdgeToEdge()
        setContent {
            SATITheme {
                CookieBankApp(
                    sensorManager = sensorManager,
                    accelerometer = accelerometer,
                    vibrator = vibrator,
                    toneGenerator = toneGenerator,
                    lifecycleOwner = this@MainActivity
                )
            }
        }
    }
}

@Composable
fun CookieBankApp(
    sensorManager: SensorManager,
    accelerometer: Sensor?,
    vibrator: Vibrator,
    toneGenerator: ToneGenerator,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    val securityPrefs = remember { SecurityPreferences(context) }
    
    var currentScreen by remember { mutableStateOf(Screen.Login) }
    var balance by remember { mutableStateOf(1_000_000L) }
    var transferAmount by remember { mutableStateOf(0L) }
    
    // State for VerifyScreen
    var targetShakeCount by remember { mutableStateOf(0) }
    var currentShakeCount by remember { mutableStateOf(0) }
    var sensorX by remember { mutableStateOf(0f) }
    var sensorY by remember { mutableStateOf(0f) }
    var shakeListenerRef by remember { mutableStateOf<ShakeDetector?>(null) }
    
    // Register/unregister sensor based on screen and lifecycle
    LaunchedEffect(currentScreen, shakeListenerRef, lifecycleOwner.lifecycle.currentState) {
        if (currentScreen == Screen.Verify && accelerometer != null && shakeListenerRef != null) {
            sensorManager.registerListener(
                shakeListenerRef,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
        } else {
            shakeListenerRef?.let { sensorManager.unregisterListener(it) }
        }
    }
    
    // Lifecycle observer to unregister sensor when activity is paused/stopped
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                shakeListenerRef?.let { sensorManager.unregisterListener(it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            shakeListenerRef?.let { sensorManager.unregisterListener(it) }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            Screen.Login -> PinLoginScreen(
                onLoginSuccess = { currentScreen = Screen.Home },
                securityPrefs = securityPrefs
            )
            Screen.Home -> HomeScreen(
                balance = balance,
                securityPrefs = securityPrefs,
                onTransferClick = { amount ->
                    transferAmount = amount
                    targetShakeCount = (1..5).random()
                    currentShakeCount = 0
                    sensorX = 0f
                    sensorY = 0f
                    currentScreen = Screen.Verify
                }
            )
            Screen.Verify -> VerifyScreen(
                targetShakeCount = targetShakeCount,
                currentShakeCount = currentShakeCount,
                onShakeCountChange = { currentShakeCount = it },
                sensorX = sensorX,
                sensorY = sensorY,
                onSensorDataChange = { x, y ->
                    sensorX = x
                    sensorY = y
                },
                vibrator = vibrator,
                toneGenerator = toneGenerator,
                securityPrefs = securityPrefs,
                transferAmount = transferAmount,
                onVerificationSuccess = {
                    balance -= transferAmount
                    securityPrefs.resetFailedAttempts()
                    Toast.makeText(
                        context,
                        "Transfer successful! ${formatNumber(transferAmount)} 🍪 sent.",
                        Toast.LENGTH_LONG
                    ).show()
                    shakeListenerRef?.let { sensorManager.unregisterListener(it) }
                    shakeListenerRef = null
                    currentScreen = Screen.Home
                },
                onVerificationFailed = {
                    securityPrefs.incrementFailedAttempts()
                    Toast.makeText(context, "Transaction Failed", Toast.LENGTH_LONG).show()
                    shakeListenerRef?.let { sensorManager.unregisterListener(it) }
                    shakeListenerRef = null
                    currentScreen = Screen.Home
                },
                onShakeListenerCreated = { shakeListenerRef = it },
                onBackClick = {
                    shakeListenerRef?.let { sensorManager.unregisterListener(it) }
                    shakeListenerRef = null
                    currentScreen = Screen.Home
                }
            )
        }
    }
}

@Composable
fun PinLoginScreen(
    onLoginSuccess: () -> Unit,
    securityPrefs: SecurityPreferences
) {
    var pinInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val correctPin = "711520"
    
    // Check lockout status
    val isLockedOut = securityPrefs.isLockedOut()
    val remainingTime = securityPrefs.getRemainingLockoutTime()
    
    LaunchedEffect(remainingTime) {
        if (remainingTime > 0) {
            delay(1000)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A5F),
                        Color(0xFF2C5282)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = "Cookie Bank",
            modifier = Modifier.size(80.dp),
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Cookie Bank",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // PIN Dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(horizontal = 8.dp)
                        .background(
                            color = if (index < pinInput.length) Color.White else Color(0x33FFFFFF),
                            shape = CircleShape
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Lockout Message
        if (isLockedOut) {
            val secondsRemaining = (remainingTime / 1000).toInt()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4444)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Account Locked until ${formatTime(remainingTime)}",
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Numeric Keypad
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1-3
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            ).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { digit ->
                        KeypadButton(
                            text = digit,
                            enabled = !isLockedOut,
                            onClick = {
                                if (pinInput.length < 6) {
                                    pinInput += digit
                                }
                            }
                        )
                    }
                }
            }
            
            // Row 4: 0 and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                KeypadButton(
                    text = "0",
                    enabled = !isLockedOut,
                    onClick = {
                        if (pinInput.length < 6) {
                            pinInput += "0"
                        }
                    }
                )
                KeypadButton(
                    text = "⌫",
                    enabled = !isLockedOut && pinInput.isNotEmpty(),
                    onClick = {
                        if (pinInput.isNotEmpty()) {
                            pinInput = pinInput.dropLast(1)
                        }
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        // Verify PIN when 6 digits entered
        LaunchedEffect(pinInput) {
            if (pinInput.length == 6) {
                if (pinInput == correctPin) {
                    securityPrefs.resetFailedAttempts()
                    delay(300)
                    onLoginSuccess()
                } else {
                    Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    pinInput = ""
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(80.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) Color.White else Color(0x33FFFFFF),
            disabledContainerColor = Color(0x33FFFFFF)
        )
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color(0xFF1E3A5F) else Color(0x66FFFFFF)
        )
    }
}

@Composable
fun HomeScreen(
    balance: Long,
    securityPrefs: SecurityPreferences,
    onTransferClick: (Long) -> Unit
) {
    var transferAmount by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    // Check lockout status
    val isLockedOut = securityPrefs.isLockedOut()
    val remainingTime = remember { mutableStateOf(securityPrefs.getRemainingLockoutTime()) }
    
    // Update countdown timer
    LaunchedEffect(isLockedOut) {
        while (isLockedOut && remainingTime.value > 0) {
            delay(1000)
            remainingTime.value = securityPrefs.getRemainingLockoutTime()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A5F),
                        Color(0xFF2C5282)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo/Icon
        Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = "Cookie Bank",
            modifier = Modifier.size(80.dp),
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Welcome Text
        Text(
            text = "Welcome, User",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Balance Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your Balance",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${formatNumber(balance)} 🍪",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A5F)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Transfer Amount Input
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Transfer Amount",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = transferAmount,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            transferAmount = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount (🍪)") },
                    placeholder = { Text("Enter amount") },
                    enabled = !isLockedOut,
                    singleLine = true
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Lockout Message
        if (isLockedOut) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4444)),
                shape = RoundedCornerShape(16.dp)
            ) {
    Text(
                    text = "Try again in ${(remainingTime.value / 1000).toInt()}s",
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Transfer Button
        Button(
            onClick = {
                val amount = transferAmount.toLongOrNull() ?: 0L
                if (amount > 0 && amount <= balance) {
                    onTransferClick(amount)
                } else {
                    Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !isLockedOut && transferAmount.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFD700),
                disabledContainerColor = Color(0x66FFD700)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = null,
                tint = if (isLockedOut) Color(0x66FFFFFF) else Color(0xFF1E3A5F)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Transfer Cookies",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLockedOut) Color(0x66FFFFFF) else Color(0xFF1E3A5F)
            )
        }
    }
}

@Composable
fun VerifyScreen(
    targetShakeCount: Int,
    currentShakeCount: Int,
    onShakeCountChange: (Int) -> Unit,
    sensorX: Float,
    sensorY: Float,
    onSensorDataChange: (Float, Float) -> Unit,
    vibrator: Vibrator,
    toneGenerator: ToneGenerator,
    securityPrefs: SecurityPreferences,
    transferAmount: Long,
    onVerificationSuccess: () -> Unit,
    onVerificationFailed: () -> Unit,
    onShakeListenerCreated: (ShakeDetector) -> Unit,
    onBackClick: () -> Unit
) {
    BackHandler {
        onBackClick() // Use Function Cancel On Screen
    }
    val context = LocalContext.current
    var timeRemaining by remember { mutableStateOf(20) } // 20 seconds countdown
    val scope = rememberCoroutineScope()
    
    // Heartbeat animation - speed increases as time runs out
    val pulseSpeed = remember(timeRemaining) {
        if (timeRemaining <= 5) 300f // Fast pulse when time is low
        else if (timeRemaining <= 10) 500f
        else 800f // Normal pulse
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = pulseSpeed.toInt(),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Create shake detector
    val currentShakeCountState = rememberUpdatedState(currentShakeCount)
    val targetShakeCountState = rememberUpdatedState(targetShakeCount)
    val onShakeCountChangeState = rememberUpdatedState(onShakeCountChange)
    val onVerificationCompleteState = rememberUpdatedState(onVerificationSuccess)
    
    val shakeDetector = remember {
        ShakeDetector(
            onShakeDetected = {
                // Short vibration (100ms)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
                
                // Short beep sound
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                
                val current = currentShakeCountState.value
                val target = targetShakeCountState.value
                
                val newCount = current + 1
                onShakeCountChangeState.value(newCount)
                
                // Check if complete
                if (newCount >= target) {
                    // Success vibration pattern
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0, 100, 50, 100, 50, 200),
                                -1
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 100, 50, 100, 50, 200), -1)
                    }
                    
                    // Success beep
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                    
                    // Execute completion
                    onVerificationCompleteState.value()
                }
            },
            onSensorData = { x, y ->
                onSensorDataChange(x, y)
            }
        )
    }
    
    // Register the shake detector
    LaunchedEffect(Unit) {
        onShakeListenerCreated(shakeDetector)
    }
    
    // Timer countdown
    LaunchedEffect(Unit) {
        while (timeRemaining > 0 && currentShakeCount < targetShakeCount) {
            delay(1000)
            timeRemaining--
        }
        
        // Timer ended - verification failed
        if (timeRemaining == 0 && currentShakeCount < targetShakeCount) {
            // Long vibration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
            
            onVerificationFailed()
        }
    }
    
    // Light tick vibration on every pulse beat
    LaunchedEffect(pulseScale) {
        if (pulseScale > 1.1f) { // Near peak of pulse
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        }
    }
    
    // Map sensor values to rotation angles
    val rotationX = remember(sensorY) {
        (sensorY / 10f).coerceIn(-1f, 1f) * 30f
    }
    
    val rotationY = remember(sensorX) {
        (sensorX / 10f).coerceIn(-1f, 1f) * 30f
    }
    
    // Green pulsing background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00FF00).copy(alpha = 0.3f + (pulseScale - 1f) * 0.2f),
                        Color(0xFF00AA00).copy(alpha = 0.2f + (pulseScale - 1f) * 0.15f),
                        Color(0xFF004400)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Back Button
            TextButton(
                onClick = onBackClick,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = "← Cancel",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Glassmorphism Card (Black with alpha)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pulseScale),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x80000000) // Black with alpha
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Timer Display
                    Text(
                        text = "${timeRemaining}s",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "SECURITY VERIFICATION",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Shake your device to verify",
                        fontSize = 14.sp,
                        color = Color(0xCCFFFFFF),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Fake 3D Phone Model
            Surface(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        this.rotationX = rotationX
                        this.rotationY = rotationY
                    },
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = "Phone Model",
                    modifier = Modifier.fillMaxSize(),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Shake Progress Display
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x33FFFFFF)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Shake Required",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$currentShakeCount / $targetShakeCount",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    // Progress indicator
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = if (targetShakeCount > 0) {
                            (currentShakeCount.toFloat() / targetShakeCount.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Color(0xFFFFD700),
                        trackColor = Color(0x33FFFFFF)
                    )
                }
            }
        }
    }
}

// Enhanced Shake Detection Class
class ShakeDetector(
    private val onShakeDetected: () -> Unit,
    private val onSensorData: (Float, Float) -> Unit
) : SensorEventListener {
    private var lastShakeTime: Long = 0
    private val debounceTime = 100L // 400ms debounce
    private val shakeThreshold = 1.5f // G-force threshold
    private val gravity = 9.81f // Standard gravity
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Pass X and Y values for rotation
            onSensorData(x, y)
            
            // Calculate G-force magnitude
            val gForce = sqrt((x * x + y * y + z * z)) / gravity
            
            val currentTime = System.currentTimeMillis()
            
            // Check for strong shake (G-force > threshold) with debounce
            if (gForce > shakeThreshold && (currentTime - lastShakeTime) > debounceTime) {
                lastShakeTime = currentTime
                onShakeDetected()
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}

// Helper function to format numbers with commas
fun formatNumber(number: Long): String {
    return number.toString().reversed().chunked(3).joinToString(",").reversed()
}

// Helper function to format time
fun formatTime(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${remainingSeconds}s"
    }
}
