# Privacy Policy for DresSecureComms

**Last Updated:** June 2026

DresSecureComms is a free, open-source, and de-Googled secure communications application built for the DresOS defensive security ecosystem. We believe in absolute user privacy. 

### 1. No Data Collection or Tracking
DresSecureComms does not collect, store, or transmit any personal data, telemetry, or analytics. There are no user accounts, no key servers, and no background tracking. All your messages, call logs, and contacts remain strictly on your physical device.

### 2. Permission Justifications
Because DresSecureComms is designed to replace your device's default Dialer and SMS applications to provide a secure, offline-first environment, it requires sensitive Android permissions. Here is exactly why each is needed:

* **SMS & MMS Permissions (`RECEIVE_SMS`, `SEND_SMS`, `READ_SMS`, etc.):** Required to act as your default SMS application. This allows the app to receive, display, and optionally encrypt your text messages locally.
* **Phone & Call Log Permissions (`CALL_PHONE`, `READ_CALL_LOG`, `READ_PHONE_STATE`, etc.):** Required to act as your default Phone/Dialer application and Caller ID/Spam shield. This allows the app to place carrier calls and manage your local call history.
* **Contacts (`READ_CONTACTS`):** Used *only* if you explicitly choose to import your device's contacts into the app's encrypted local vault.
* **Location (`ACCESS_FINE_LOCATION`, `ACCESS_MOCK_LOCATION`):** Used exclusively for the "Geo Spoofer" privacy feature, allowing you to mask your real GPS coordinates from other invasive apps on your device.
* **Internet (`INTERNET`):** The app is entirely offline for messaging and calling. The Internet permission is used **strictly and solely** to connect to the VirusTotal API when you manually use the "Threat Scan" feature to check a URL.
* **Biometrics (`USE_BIOMETRIC`):** Used to unlock the app via the App Lock feature.

### 3. Cryptography and Security
* **Local Encryption:** Your contacts vault and sent-message logs are encrypted at rest using keys generated and stored securely in the hardware-backed **Android Keystore**.
* **Message Encryption:** Optional peer-to-peer message encryption uses **AES-256-GCM**. The encryption key is a passphrase you set and share out-of-band. The app never transmits this key.
* **Biometric Hardening:** The App Lock feature uses Android's `BiometricPrompt` tied directly to a cryptographic `CryptoObject` in the Keystore, ensuring the app cannot be bypassed by memory hooking tools.

### 4. Third-Party Services
DresSecureComms contains **zero** third-party trackers, ads, or Firebase/Google Play Services. The only external network connection made is to `virustotal.com`, and only when you manually paste a URL into the Threat Scanner using your own personal API key.

### 5. Open Source
DresSecureComms is fully open-source. You can audit the code, verify the permissions, and build the app yourself from our official GitHub repository.

**Source Code:** [https://github.com/DresOperatingSystems/DresSecureComms](https://github.com/DresOperatingSystems/DresSecureComms)
**Project Website:** [https://dresoperatingsystems.github.io/](https://dresoperatingsystems.github.io/)
