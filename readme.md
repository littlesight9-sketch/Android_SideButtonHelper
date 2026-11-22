# Side Button Helper

**Replace broken power and volume buttons on any Android phone — no root, no third-party apps.**

Built out of necessity: when a phone's physical side buttons stop working, you lose the ability to lock/unlock the screen and control volume. This app restores both using gestures and an on-screen control, entirely through official Android APIs.

---

## ✨ Features

### Screen Control
- **Tap-to-sleep** — double or triple tap anywhere on the home/lock screen to lock the phone
- **Shake-to-wake** — shake the phone to turn the screen back on, even while fully locked
- **Double-tap-to-wake** — two quick taps on the phone body wake the screen while it's asleep
- Configurable sensitivity for both gestures
- Works with Doze and low-power sensors — the accelerometer is registered as a wake-up sensor with batching disabled, so taps are still seen after the screen has been off for a while

### Volume Control
- **Floating volume bubble** — a small draggable on-screen button, always accessible, that expands into sliders for **Ring, Notification, Alarm, Media, and Call** volume
- Per-stream mute toggles
- Silent / Vibrate / Normal ringer mode switch
- Persistent notification with quick +/- controls

### Safety & Reliability
- Gesture auto-disabled during calls and while the camera is open
- Survives reboot and background app-killing (auto-restart watchdog)
- Manufacturer-specific setup guidance for phones that restrict background apps (realme, Oppo, OnePlus, Xiaomi/Redmi/POCO, Vivo/iQOO, Huawei/Honor, Asus)
- Home-screen status shows whether the app is allowed to keep running in the background, with a one-tap fix
- Safe uninstall flow — cleanly revokes permissions before uninstalling

### Built for Real Use
- Guided, plain-language onboarding for every permission
- English / Hindi language support
- Simple, large-button home screen
- Built-in test mode to try gestures safely before relying on them

---

## 📱 Tested device notes

Built and tuned on a **realme C21Y** (Android 11 Go Edition, realme UI 2.0 Go). Because realme UI pauses background apps aggressively, tick these once after setup:

1. **Auto-start** — home screen → *Background Setup Guide* → enable Auto-start for SideButtonHelper
2. **Battery** — Settings → Battery → App Battery Management → SideButtonHelper → *Allow background activity*
3. **Battery optimization** — home screen → *Allow Background Running* (status should read *Allowed*)

With those set, double-tap and shake wake the screen from a fully off state. On Go-edition phones the first tap after a long idle may need to be a little firmer; use the gesture sensitivity slider in Advanced Settings to tune it.

---

## 🛠 Tech Stack

| | |
|---|---|
| Language | Java |
| Min SDK | 26 (Android 8.0) |
| Build | Gradle (Kotlin DSL) |
| UI | XML layouts + ViewBinding + Material Components |
| Dependencies | AndroidX / Google official libraries only — **zero third-party code** |

---

## 🔐 Permissions Used

| Permission | Why |
|---|---|
| Accessibility Service | Detects the lock gesture and locks the screen |
| Display over other apps | Powers the floating volume bubble |
| Usage Access | Restricts tap detection to the home/lock screen |
| Device Admin | Fallback screen-lock method on Android 8.x |
| Do Not Disturb access | Lets the app set Ring/Notification volume to silent |
| Ignore battery optimizations | Keeps shake detection running reliably |
| Post notifications | Shows the persistent volume control notification |

Every permission is requested with an explanation, one at a time, during setup — nothing is granted silently.

---

## 🚀 Getting Started

1. Clone the repo:
   ```bash
   git clone https://github.com/<your-username>/SideButtonHelper.git
   ```
2. Open the project folder in **Android Studio**
3. Let Gradle sync
4. Run on a physical device (recommended — shake detection and overlays don't behave realistically on emulators)
5. Complete the in-app setup wizard to grant permissions

---

## 📁 Project Structure

```
app/src/main/java/com/example/sidebuttonhelper/
├── MainActivity.java              → home screen
├── admin/                         → Device Admin (legacy lock fallback)
├── onboarding/                    → guided permission setup wizard
├── service/                       → tap detection, shake detection, boot persistence
├── volume/                        → volume bubble, controller, notification
├── settings/                      → user preferences
└── ui/                            → test mode, OEM guidance, uninstall helper
```

---

## 🧪 Testing Notes

Shake detection and real double-tap-to-lock behavior only work correctly on a physical device. Emulator testing works for UI flows and can simulate shake via **Extended Controls → Virtual Sensors → Accelerometer**.

