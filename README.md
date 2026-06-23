# DresSecureComms

DresSecureComms is a free, open-source, de-Googled secure communications app. It brings
private messaging, calling, contacts, threat scanning, and location protection together in
one place, with no Google services, no Firebase, no analytics, and no trackers. Messaging
and calls work fully offline on your normal carrier line.

It is a core part of [DresOS, the Android defensive security system](https://github.com/DresOperatingSystems/DresOS-The-Android-Defensive-Security-System).
Within DresOS, DresSecureComms takes over much of the day-to-day protection of the device,
replacing several separate tools with a single hardened app so that messaging, calling,
contacts, and on-device safety checks all run through one place you can trust.

[![Please Help fund future projects and keep this one going](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/dresos)

> **Help fund Us.** DresOS is built by a small open source team in our spare time. If you enjoy this work then please tip the jar at [ko-fi.com/dresos](https://ko-fi.com/dresos). Funds go to test devices, hosting, and developer time on projects.

## What it does

- **Messages.** A private SMS client. Send and receive normal texts, or switch on
  encryption for a message. Encrypted messages are readable only by another person who
  runs this app and shares the same key. Set it as your default SMS app so texts arrive
  inside it, and delete whole conversations when you want them gone.
- **Calls.** Dial a number or call a saved contact over your carrier line. As your default
  phone app it shows its own in-call screen with mute, speaker, keypad, hold, add call, and
  hang up, and it keeps a call history you can clear per call or all at once.
- **Caller ID and spam.** Can be selected as your caller ID and spam app.
- **Contacts.** An encrypted on-device contacts vault. Add and edit contacts with a name,
  number, and email, or import from the device. Nothing leaves the phone.
- **Threat Scan.** Check a link against VirusTotal and get a clear safe, suspicious, or
  dangerous verdict before you open it. Scanning of media, files, APKs, ZIPs, and more is
  on the way; that engine is under wraps for now.
- **Metadata Wipe.** Strip GPS and other hidden EXIF data from a photo before you share it.
- **Geo Spoofer.** Set a mock GPS location, or have the app pick a random one, to keep your
  real location private from apps that read it.
- **App Lock.** Lock the app behind your fingerprint or device PIN.
- **Block Screenshots.** An optional setting that stops screenshots, screen recording, and
  recents previews everywhere in the app.

## How the encryption works

Message encryption is symmetric AES-256-GCM. The key is a passphrase you set in Settings
and share, by hand, with the person you are talking to. There is no account and no key
server. Encrypted messages carry a short marker so the app knows to decode them; to anyone
else, including your carrier, they are unreadable text. Calls over the carrier line are
ordinary phone calls and are not end-to-end encrypted, and the app says so plainly.

Contacts and the sent-message log are encrypted at rest using a key held in the Android
Keystore, so they stay protected on the device.

## Setting it as your default apps

Because the app is installed outside the Play Store, Android first puts SMS and phone
access behind Restricted settings. Allow it once:

1. Open Settings, then Apps, then DresSecureComms.
2. Tap the three-dot menu in the top right.
3. Tap Allow restricted settings.

Then, inside the app, open Settings and Default apps and choose to set it as your default
SMS app, your default phone app, and your caller ID and spam app. Setting it as the default
SMS app is what lets it receive, show, and delete your text messages.

For the geo spoofer, turn on Developer options, open Select mock location app, and pick
DresSecureComms.

## Permissions and why

- **RECEIVE_SMS, READ_SMS, SEND_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH** — to act as the
  default SMS app: receive, read, send, and manage your texts.
- **CALL_PHONE, READ_PHONE_STATE, MANAGE_OWN_CALLS, ANSWER_PHONE_CALLS** — to place and
  handle calls as the default phone app.
- **READ_CALL_LOG, WRITE_CALL_LOG** — to show your call history and let you delete it.
- **READ_CONTACTS** — optional, only to import contacts into the encrypted vault.
- **ACCESS_FINE_LOCATION, ACCESS_MOCK_LOCATION** — for the geo spoofer. Mock location is a
  no-op permission that only makes the app selectable in Developer options.
- **USE_BIOMETRIC** — for the app lock.
- **POST_NOTIFICATIONS** — to show message and call notifications.
- **INTERNET** — used only by Threat Scan to reach VirusTotal. Nothing else goes online.

## Privacy

No accounts. No analytics. No advertising. No Google or third-party services beyond the
VirusTotal lookup you trigger yourself in Threat Scan. Your messages, contacts, and call
history stay on your device.

## License

Apache-2.0. Copyright © 2026 DresOS.
