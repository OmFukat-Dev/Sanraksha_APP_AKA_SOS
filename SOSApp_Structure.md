# SOSApp (Sanraksha Alert) - Complete File Structure

## Project Structure
```
SOSApp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/sanraksha/sosapp/
│   │   │   │   ├── activities/
│   │   │   │   │   ├── LoginActivity.kt
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── ContactsActivity.kt
│   │   │   │   │   ├── ProfileActivity.kt
│   │   │   │   │   └── SettingsActivity.kt
│   │   │   │   ├── database/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── User.kt
│   │   │   │   │   ├── Contact.kt
│   │   │   │   │   ├── UserDao.kt
│   │   │   │   │   └── ContactDao.kt
│   │   │   │   ├── services/
│   │   │   │   │   └── SOSMonitoringService.kt
│   │   │   │   ├── utils/
│   │   │   │   │   ├── PrefManager.kt
│   │   │   │   │   ├── PermissionHelper.kt
│   │   │   │   │   ├── SOSTriggerManager.kt
│   │   │   │   │   ├── ShakeDetector.kt
│   │   │   │   │   ├── VoiceDetector.kt
│   │   │   │   │   ├── SoundDetector.kt
│   │   │   │   │   ├── LocationHelper.kt
│   │   │   │   │   ├── SMSHelper.kt
│   │   │   │   │   └── EncryptionUtils.kt
│   │   │   │   └── adapters/
│   │   │   │       └── ContactsAdapter.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_login.xml
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   ├── activity_contacts.xml
│   │   │   │   │   ├── activity_profile.xml
│   │   │   │   │   ├── activity_settings.xml
│   │   │   │   │   ├── dialog_create_user.xml
│   │   │   │   │   ├── dialog_forgot_id.xml
│   │   │   │   │   ├── dialog_add_contact.xml
│   │   │   │   │   └── item_contact.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── themes.xml
│   │   │   │   │   └── styles.xml
│   │   │   │   ├── drawable/
│   │   │   │   │   ├── ic_sos.xml
│   │   │   │   │   ├── ic_home.xml
│   │   │   │   │   ├── ic_contacts.xml
│   │   │   │   │   ├── ic_profile.xml
│   │   │   │   │   └── ic_settings.xml
│   │   │   │   ├── raw/
│   │   │   │   │   └── siren_sound.mp3
│   │   │   │   ├── menu/
│   │   │   │   │   └── bottom_navigation.xml
│   │   │   │   └── xml/
│   │   │   │       └── network_security_config.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle (app level)
│   └── build.gradle (project level)
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── settings.gradle
└── README.md
```

## All Files Listed Below (30+ files total)