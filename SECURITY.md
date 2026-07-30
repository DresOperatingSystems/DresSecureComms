# Security Notes for DresSecureComms

**Applies to:** DresSecureComms 1.8.1 (versionCode 12) and later. Last revised July 2026.

DresSecureComms is an offline-first, de-Googled, root-friendly FOSS app. Its only network call is
to the VirusTotal API, made solely when the user manually runs a scan with their own API key. This
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

- **File Scan transmits file contents only when you ask it to.** Scanning computes a SHA-256 of the
  file or installed package locally and sends only that digest to the VirusTotal file report endpoint,
  so scanning a private document does not disclose it to a third party. If VirusTotal has no record of
  that digest the app offers to upload the file, one file at a time, behind a prompt that states the
  file leaves the device and that VirusTotal shares what it receives with its partners. No upload
  happens without that confirmation and a sweep of installed apps never uploads. Digests are also
  de-duplicated within a run so the same file is never queried twice. See `scan/FileScanner.kt` and
  `net/VirusTotalClient.kt`.
- **App enumeration uses scoped visibility, not `QUERY_ALL_PACKAGES`.** The device scan lists apps
  through a `<queries>` declaration for launcher intents and `queryIntentActivities`, so the app
  sees the launchable packages it needs without holding the blanket package-visibility permission
  that store reviewers treat as an anti-feature.
- **Call screening is local only.** `CallScreeningService` decides using on-device rules, the user's
  own contacts, and a locally stored block list encrypted with the Android Keystore. No number is
  sent off the device, and there is no cloud or crowd-sourced reputation service in the path. Every
  automatic block is recorded with its reason and is reversible from Settings. See
  `CallScreenService.kt`, `scan/SpamFilter.kt`, and `data/SpamStore.kt`.
- **Per-contact message keys.** Message passphrases are stored per contact in a file encrypted with
  an Android Keystore key rather than in plain SharedPreferences, and the sender's number selects
  which key to try, falling back to the previous shared key so existing threads keep opening. The
  keys are shared secrets by design, so they are held encrypted at rest rather than as
  non-exportable Keystore keys, which could not be shared between two devices. See
  `crypto/ContactKeys.kt`.

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
- **Cloud or crowd-sourced spam reputation: not implemented.** Every number-reputation service worth
  using requires sending the caller's number, and often the user's own, to a third party. That is
  exactly the disclosure this app exists to avoid, so screening is limited to local heuristics and a
  user-controlled block list. The trade-off is that a brand new spam number will ring once before
  the user blocks it.
- **Local malware signature database: not yet bundled.** Version one of File Scan uses VirusTotal's
  aggregate of seventy or more engines rather than shipping a signature set that would be stale the
  week after release. The cost is that scanning needs a network connection and an API key. An
  offline signature mode is planned for version two.
- **Certificate Transparency enforcement: not bundled.** CT is the modern alternative to pinning but
  needs a regularly updated log list and only became native on Android around API 35/36. On minSdk
  24 the dependable baseline is HTTPS plus the system CA store.

