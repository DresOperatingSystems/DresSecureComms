# Security Notes for DresSecureComms

DresSecureComms is an offline-first, de-Googled, root-friendly FOSS app. Its only network call is
to the VirusTotal API, made solely when the user manually scans a URL with their own API key. This
document records how the project responds to static-analysis findings (CodeQL, mobsfscan) and to
antivirus false positives, so reviewers and scanners can see what was fixed and what was
deliberately not implemented, with reasons.

## Fixed in code

- **Biometric App Lock is cryptographically bound (CodeQL java/android/insecure-local-authentication).**
  Unlocking is not a callback flag. A marker is encrypted at enrollment behind a biometric prompt
  and must be decrypted on every unlock using an Android Keystore key created with
  `setUserAuthenticationRequired(true)` and `setInvalidatedByBiometricEnrollment(true)`. A spoofed
  `onAuthenticationSucceeded` or a Frida hook never causes the OS to release the key, so the decrypt
  throws and unlock fails closed. Devices without a class-3 biometric fall back to the device
  keyguard. See `crypto/AppLockManager.kt` and `MainActivity.kt`.
- **Message KDF upgraded to PBKDF2-HMAC-SHA256 (mobsfscan, SHA-1 weakness).** All new encrypted
  messages use PBKDF2-HMAC-SHA256 with AES-256-GCM and a fresh per-message salt. Legacy messages
  (prefix `DSC1:`) are still decryptable with the old SHA-1 path so existing data is not lost;
  SHA-1 is never used to encrypt new data. See `crypto/SmsCrypto.kt`.
- **Locked screen does not lay out content (mobsfscan hidden-UI).** The root view is `GONE` while
  locked, not `INVISIBLE`, and `FLAG_SECURE` keeps it out of screenshots and the recents preview.
- **Tapjacking protection.** `filterTouchesWhenObscured` is set on every activity that applies the
  security policy, so touches delivered while the window is obscured by another app are dropped.
- **HTTPS enforced.** A network security config disables cleartext traffic for the VirusTotal call.
- **Release signing and APK hygiene.** Builds are signed with a real release certificate (full
  Distinguished Name, no "Unknown" fields) and omit the Google dependency-metadata blob via
  `dependenciesInfo { includeInApk = false; includeInBundle = false }`.

## Deliberately not implemented, with rationale

- **"Hardcoded key" in `res/xml/preferences.xml` (mobsfscan): false positive.** The matched `key`
  attributes are AndroidX `Preference` identifiers (for example `default_sms`), used to address
  SharedPreferences entries. They are not credentials or cryptographic keys. No secret material is
  stored in resources; all secret keys are generated at runtime in the Android Keystore.
- **SafetyNet / Play Integrity attestation: not implemented.** These require Google Play Services
  and a Google-attested device. DresSecureComms ships no Google Play Services by design and targets
  de-Googled ROMs (DresOS, CalyxOS, GrapheneOS, LineageOS, /e/OS). Adding attestation would break the app on its
  intended platforms and reintroduce the exact Google dependency the project exists to avoid. Its
  absence does not affect the at-rest threat model, which is covered by hardware Keystore keys and a
  biometric-bound CryptoObject.
- **Root detection: not implemented.** The app is intentionally root-friendly; its users routinely
  run rooted or custom devices. Blocking rooted devices would deny service to legitimate users and
  provides no protection against a determined local attacker.
- **TLS certificate / public-key pinning: not implemented.** The only endpoint is the third-party
  VirusTotal API, whose certificate lifecycle we do not control. Pinning would risk a self-inflicted
  outage on every certificate rotation, fixable only by an app update. We enforce HTTPS via the
  network security config and trust the system CA store instead.
- **Certificate Transparency enforcement: not bundled.** CT is the modern alternative to pinning but
  needs a regularly updated log list and only became native on Android around API 35/36. On minSdk
  24 the dependable baseline is HTTPS plus the system CA store.

