# SOSApp (Sanraksha Alert) - Emergency Safety Application

## Overview
Sanraksha Alert is a professional Android emergency safety application designed for quick SOS alerts in life-threatening situations. It's a local-first app with no server dependency, using Room Database for secure data storage.

## Features

### 🔐 Authentication
- **Unique ID Login**: Secure login with unique 6-digit ID (SRK + random)
- **Create Account**: Register with name and 10-digit phone number
- **Forgot ID**: Retrieve unique ID via SMS using registered phone
- **OTP Verification**: 3 failed login attempts trigger OTP (demo mode)

### 🚨 SOS Triggers
- **Manual SOS Button**: Big red button with 30-second countdown
- **Shake Detection**: 3 quick shakes with adjustable sensitivity (Low/Medium/High)
- **Voice Recognition**: Continuous listening for "help" or "SOS" keywords
- **Sound Detection**: Scream detection (>2000 dB amplitude)

### 📱 Emergency Response
- **SMS Alerts**: Sends location-based SOS to all emergency contacts
- **Auto-Call**: Automatically calls first emergency contact
- **Siren Sound**: 30-second looping alarm (siren_sound.mp3)
- **GPS Location**: Real-time location sharing via Google Maps link

### 👥 Contact Management
- **Add/Edit/Delete**: Manage emergency contacts with name and phone
- **Swipe to Delete**: Quick gesture-based deletion
- **Empty State**: User-friendly message when no contacts exist

### 👤 Profile Management
- **Personal Info**: Name, phone, email, blood group, age
- **Editable Fields**: Update profile information anytime
- **Data Persistence**: All data stored locally in Room DB

### ⚙️ Settings
- **Safety Mode**: Master toggle for all monitoring features
- **Shake Sensitivity**: Adjustable slider (Low/Medium/High)
- **SOS Mode**: Voice-only emergency mode
- **Dark/Light Theme**: Instant theme switching
- **Share App**: Share via social platforms
- **Rate App**: Direct link to Play Store
- **Privacy Policy**: Opens privacy URL
- **Logout**: Clear session and return to login

### 🔔 Background Monitoring
- **Foreground Service**: Runs continuously with notification
- **Sticky Service**: Auto-restarts after system kill
- **Battery Optimized**: Efficient sensor usage

## Technical Stack

### Architecture
- **Pattern**: MVVM-like with Activities + Utils + Room DB
- **Language**: Kotlin 1.9.0
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

### Dependencies
```gradle
// Core
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1
com.google.android.material:material:1.11.0

// Room Database
androidx.room:room-runtime:2.6.1
androidx.room:room-ktx:2.6.1

// Coroutines
kotlinx-coroutines-android:1.7.3

// Location Services
play-services-location:21.1.0
```

### Database Schema
```kotlin
// Users Table
uniqueId: String (PK)
name: String
phone: String? (nullable, no UNIQUE constraint)
email: String?
bloodGroup: String?
age: Int?
createdAt: Long

// Contacts Table
id: Int (PK, auto-increment)
userId: String (FK → Users.uniqueId, CASCADE delete)
name: String
phone: String
createdAt: Long
```

## Project Structure
```
app/src/main/
├── java/com/sanraksha/sosapp/
│   ├── activities/
│   │   ├── LoginActivity.kt
│   │   ├── MainActivity.kt
│   │   ├── ContactsActivity.kt
│   │   ├── ProfileActivity.kt
│   │   └── SettingsActivity.kt
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── User.kt
│   │   ├── Contact.kt
│   │   ├── UserDao.kt
│   │   └── ContactDao.kt
│   ├── services/
│   │   └── SOSMonitoringService.kt
│   ├── utils/
│   │   ├── PrefManager.kt
│   │   ├── PermissionHelper.kt
│   │   ├── LocationHelper.kt
│   │   ├── SMSHelper.kt
│   │   ├── ShakeDetector.kt
│   │   ├── VoiceDetector.kt
│   │   ├── SoundDetector.kt
│   │   ├── SOSTriggerManager.kt
│   │   └── EncryptionUtils.kt
│   └── adapters/
│       └── ContactsAdapter.kt
├── res/
│   ├── layout/ (9 XML files)
│   ├── values/ (strings, colors, themes)
│   ├── drawable/ (5 vector icons)
│   ├── menu/ (bottom_navigation.xml)
│   └── raw/ (siren_sound.mp3 - **REQUIRED**)
└── AndroidManifest.xml
```

## Setup Instructions

### 1. Import Project
1. Open Android Studio (Arctic Fox or later)
2. File → Open → Select project folder
3. Wait for Gradle sync

### 2. Add Siren Sound
**CRITICAL**: Add `siren_sound.mp3` to `app/src/main/res/raw/`
- File must be named exactly `siren_sound.mp3`
- Format: MP3, recommended 30 seconds loop
- Without this file, app will crash on SOS trigger

### 3. Configure Permissions
All permissions are declared in `AndroidManifest.xml`:
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `SEND_SMS`
- `CALL_PHONE`
- `RECORD_AUDIO`
- `FOREGROUND_SERVICE`
- `POST_NOTIFICATIONS`

### 4. Build & Run
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Run app
adb shell am start -n com.sanraksha.sosapp/.activities.LoginActivity
```

## Testing Guide

### First-Time Setup
1. Launch app → Login screen
2. Click "Create New Account"
3. Enter name and 10-digit phone
4. Save the generated Unique ID (e.g., SRK123456)
5. Auto-login to Main screen

### SOS Testing
1. **Add Emergency Contact**:
    - Navigate to Contacts tab
    - Click "Add Contact"
    - Enter name and phone
    - Save

2. **Enable Safety Mode**:
    - Go to Main screen
    - Toggle "Safety Mode" ON
    - Enable desired triggers (Shake/Voice/Sound)

3. **Test Manual SOS**:
    - Press big red SOS button
    - 30-second countdown starts
    - Cancel or wait for trigger
    - Check SMS sent and call initiated

4. **Test Shake Detection**:
    - Shake device 3 times quickly
    - SOS triggers automatically

5. **Test Voice Recognition**:
    - Say "help" or "SOS" clearly
    - Voice detector triggers SOS

### Emulator Limitations
- **SMS**: Won't send on emulator (Toast shows message)
- **Call**: May not work (use real device)
- **Shake**: Use emulator controls (Extended Controls → Virtual Sensors)
- **Voice**: Requires real device microphone
- **Location**: Use mock location in emulator settings

### Device Testing (Recommended)
- Use Android 8.0+ device
- Grant all permissions when prompted
- Test in safe environment
- Verify SMS/call with test contacts

## Known Issues & Fixes

### Issue 1: App Crashes on Create Account
**Cause**: Phone UNIQUE constraint in old DB schema
**Fix**: Already fixed - phone is now nullable without UNIQUE

### Issue 2: No SMS Sent
**Cause**: Permission denied or emulator limitation
**Fix**:
- Check SMS permission granted
- Use real device for testing
- Check Toast message for fallback

### Issue 3: Shake Not Detected
**Cause**: Low sensitivity or sensor issue
**Fix**:
- Go to Settings → Increase sensitivity to High
- Shake more vigorously (3 quick shakes)
- Check device has accelerometer

### Issue 4: Voice Not Recognized
**Cause**: Background noise or permission issue
**Fix**:
- Grant RECORD_AUDIO permission
- Speak clearly in quiet environment
- Check microphone working

## Security & Privacy

### Data Storage
- **Local Only**: All data stored in Room DB (SQLite)
- **No Cloud**: Zero server communication
- **Encryption**: Phone numbers encrypted with AES-128
- **Session**: Managed via SharedPreferences

### Permissions
- **Runtime**: All dangerous permissions requested at runtime
- **Minimal**: Only essential permissions declared
- **Transparent**: Clear permission dialogs with explanations

### Privacy Features
- **No Vibration**: As per user request
- **No Analytics**: No tracking or data collection
- **No Internet**: Works offline (except SMS/call)

## Performance

### App Size
- **APK Size**: ~5-10 MB (without siren_sound.mp3)
- **RAM Usage**: ~50-80 MB active
- **Battery**: Optimized foreground service

### Optimization
- **Coroutines**: All DB operations on IO dispatcher
- **Lazy Loading**: Activities load data on-demand
- **Efficient Sensors**: Low-pass filter for shake detection
- **Service Management**: Stops when Safety Mode OFF

## Future Enhancements

### Planned Features
1. **Fake Calculator Disguise**: Hidden app launcher
2. **Fall Detection**: Accelerometer-based fall detection
3. **Live Location Tracking**: Continuous location updates
4. **Video Recording**: Auto-record on SOS trigger
5. **Cloud Backup**: Optional encrypted cloud sync
6. **Multi-Language**: Support for regional languages
7. **Wearable Support**: Smartwatch integration

### Technical Improvements
1. **Jetpack Compose**: Migrate UI to Compose
2. **Hilt DI**: Dependency injection
3. **WorkManager**: Background task scheduling
4. **ML Kit**: Advanced sound/voice recognition
5. **Firebase**: Optional cloud features

## Troubleshooting

### Build Errors
```bash
# Gradle sync failed
File → Invalidate Caches → Restart

# Dependency resolution error
./gradlew clean build --refresh-dependencies

# Kotlin version mismatch
Update kotlin_version in build.gradle
```

### Runtime Errors
```bash
# Check logs
adb logcat | grep "sanraksha"

# Clear app data
adb shell pm clear com.sanraksha.sosapp

# Reinstall
adb uninstall com.sanraksha.sosapp
./gradlew installDebug
```

## Support

### Contact
- **Developer**: Sanraksha Team
- **Email**: support@sanrakshaalert.com
- **Website**: https://sanrakshaalert.com
- **Privacy**: https://sanrakshaalert.com/privacy

### Reporting Issues
1. Go to Settings → Share App
2. Include device model and Android version
3. Describe steps to reproduce
4. Attach logcat if possible

## License
Copyright © 2024 Sanraksha Alert. All rights reserved.

## Acknowledgments
- Material Design 3 by Google
- Room Persistence Library
- Kotlin Coroutines
- FusedLocationProviderClient

---

**⚠️ IMPORTANT NOTES:**
1. **Add `siren_sound.mp3`** to `app/src/main/res/raw/` before building
2. **Test on real device** for full functionality
3. **Grant all permissions** for complete feature access
4. **Use responsibly** - This is an emergency safety tool

**🚨 Emergency Use Only**: This app is designed for genuine emergencies. Misuse may result in false alarms and legal consequences.