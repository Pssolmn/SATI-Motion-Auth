# SATI (สติ) - Security Awareness Through Interaction 🛡️

![SATI Banner](<img width="2880" height="1800" alt="Banner (ทำใหม่แน่ครับ)" src="https://github.com/user-attachments/assets/d3dcfd78-7e02-430f-8e25-dfbd5aad2854" />

)

> **"The Final Defense Against Remote Access Scams."**

---

### ⚠️ Prototype & Concept Release
**Note:** This project is currently a **Proof-of-Concept (PoC)** prototype designed to demonstrate the potential of **Motion Authentication**. It is not yet a production-ready banking application but serves as a functional demo to showcase how sensor-based security can prevent remote fraud.

---

## 📱 Why SATI? (The Problem with Biometrics)
In the age of remote access scams, standard biometrics (Fingerprint/FaceID) are no longer enough.
* **The Problem:** Biometrics rely on **Muscle Memory**. We tap without thinking. If a scammer is remotely controlling your screen, you might unconsciously authenticate out of habit.
* **The Solution:** SATI demands **Active Physical Interaction**. By forcing the user to perform specific, random physical actions (shaking, tilting, rotating), we create a **"Mindful Pause."** This breaks the victim's trance and makes it technically impossible for a remote hacker to bypass the security, as they cannot physically move the victim's device.

---

## 🚀 Current Demo Features
The current `.apk` demonstrates the core philosophy with these working modules:
* **🚫 Anti-Remote Shield:** Automatically detects remote control tools/screen recording and blacks out the screen (`FLAG_SECURE`).
* **👋 Shake-to-Verify:** Utilizes the Accelerometer and Gyroscope to verify physical presence.
* **💣 Time-Bomb Challenge:** A 20-second countdown interface to create urgency for legitimate users while blocking slow remote attackers.
* **🔒 Persistence Lockout:** Smart cool-down system to prevent brute-force attacks.

---

## 🔮 Future Roadmap: The "SATI" Ecosystem
We are developing a suite of sensor-based authentication methods to replace traditional PINs and Biometrics for high-security transactions:

### 1. 🎨 Gesture & Shape Recognition
Instead of tapping a static PIN pad (which can be screen-recorded), the user draws a specific shape or "Imperfect Pattern" on the screen. The system recognizes the unique stroke and shape, making it harder for bots to replicate.

### 2. 🔐 The Gyroscope Vault (Safe Dial)
Turning the smartphone into a virtual bank vault dial. The user must rotate their wrist (Z-axis) clockwise and counter-clockwise to specific degrees to unlock, using haptic feedback as a guide. Impossible to "shoulder surf."

### 3. 🔦 Light Sensor Tapping
Using the ambient light sensor to detect specific "tapping" rhythms (like Morse code) against the phone's receiver. A discreet way to unlock the phone in dark environments without touching the screen.

### 4. 🗣️ Catchphrase & Rhythm Unlock
Verifying identity through a combination of voice stress analysis and tapping rhythms, ensuring the user is not under duress.

---

## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Modern UI)
* **Sensors:** Android SensorManager (Accelerometer, Gyroscope, Light Sensor)
* **Security:** WindowManager Flags, SharedPreferences encryption logic.

---

## 📥 Try the Demo
Experience the "Mindful Pause" yourself. PIN is "711520"
[**Download Prototype APK**](https://drive.google.com/file/d/1ZdEYE31qTBHPE2MnBlLowosuV3wouTGA/view?fbclid=IwY2xjawPL82ZleHRuA2FlbQIxMABicmlkETFuNGdnbEFVNDVmMlNldnVRc3J0YwZhcHBfaWQQMjIyMDM5MTc4ODIwMDg5MgABHh45cB6V-9Ibhm-SOz-2mrjfnIJptNjRAfTekzi5Qw-zy7z89eC8Sbkjl8A8_aem_fc8uLCzWei-3d-WW0PVslg)

---

## 👥 Created By
This project was created by:
* **Passakorn Songchim** - [LinkedIn Profile](https://www.linkedin.com/in/passakorn-songchim-5641582b6/)
* **Siriwat Jitmano** - [LinkedIn Profile](https://www.linkedin.com/in/siriwat-jitmano-2005553a3/)
* **Jakkapong Kampha** - [LinkedIn Profile](https://www.linkedin.com/in/%E0%B8%88%E0%B8%B1%E0%B8%81%E0%B8%A3%E0%B8%9E%E0%B8%87%E0%B8%A9%E0%B9%8C-%E0%B8%84%E0%B8%B3%E0%B8%A0%E0%B8%B2-8a42593a5/)

---
*Developed for [Samsung × KBTG Digital Fraud Cybersecurity Hackathon]*
