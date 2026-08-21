# DresSecureComms

DresSecureComms is a free, open source, deGoogled secure communications app. It brings
private messaging, calling, contacts, threat scanning, and location protection together in
one place, with no Google services, no Firebase, no analytics, and no trackers. Messaging
and calls work fully offline on your normal carrier line.

## What it is

It is a core part of [DresOS, the Android defensive security system](https://github.com/The-DresOS-Foundation/DresOS-The-Android-Defensive-Security-System).
Within DresOS, DresSecureComms takes over much of the day to day protection of the device,
replacing several separate tools with a single hardened app so that messaging, calling, contacts, and on device safety checks all run through one place you can trust.

## Download

>[<img src="https://img.shields.io/badge/Download-GitHub%20Releases-blue?style=for-the-badge&logo=github" height="50">](https://github.com/DresOperatingSystems/DresSecureComms/releases/tag/v1.8.1)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="50">](https://apt.izzysoft.de/packages/com.dresos.dressecurecomms)
[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="50">](https://obtainium.imranr.dev/redirect?url=https://github.com/DresOperatingSystems/DresSecureComms/releases/tag/v1.8.1)

## What it does

- **Messages.** A private SMS client. Send and receive normal texts, or switch on
  encryption for a message. Encrypted messages are readable only by another person who
  runs this app and shares the same key, and keys are set per contact so every person can
  have their own code. Starting a new message suggests your contacts as you type. Set it as
  your default SMS app so texts arrive inside it, and delete whole conversations when you
  want them gone.
- **Calls.** Dial a number or call a saved contact over your carrier line. As your default
  phone app it shows its own in-call screen with mute, speaker, keypad, hold, add call, and
  hang up, and the screen turns off when you hold the phone to your ear. It keeps a
  searchable call history you can clear per call or all at once, and you can block or
  unblock any number straight from the log.
- **Spam Shield.** Set as your caller ID and spam app, it screens incoming calls before
  the phone rings. Premium rate numbers are rejected, short codes are silenced, and callers
  faking the first digits of your own number are flagged. You can also silence anyone not
  in your contacts, or reject withheld numbers. Saved contacts always ring through, and
  everything it blocks is listed in Settings so you can undo it. It runs entirely on the
  device, so no number is ever sent anywhere to be checked.
- **Contacts.** An encrypted on device contacts vault. Add and edit contacts with a name,
  number, and email, or import from the device. Nothing leaves the phone.
- **Threat Scan.** Check a link against VirusTotal and get a clear safe, suspicious, or
  dangerous verdict before you open it.
- **File Scan.** Check a single file, one app, or every app on your device against VirusTotal.
  Only the SHA-256 fingerprint is sent, and that comes back checked against seventy or more
  antivirus engines at once. When VirusTotal has never seen a file, the app offers to upload
  that one file for analysis, and it always asks first. A free API key allows four
  lookups a minute, so a full sweep of your apps paces itself and can be stopped at any
  point. Offline signature scanning is what the next version adds.
- **Metadata Wipe.** Strip GPS and other hidden EXIF data from a photo before you share it.
- **Geo Spoofer.** Set a mock GPS location, or have the app pick a random one, to keep your
  real location private from apps that read it.
- **App Lock.** Lock the app behind your fingerprint or device PIN.
- **Block Screenshots.** An optional setting that stops screenshots, screen recording, and
  recents previews everywhere in the app.

## How the encryption works

Message encryption is symmetric AES-256-GCM. The key is a passphrase you agree, by hand,
with the person you are talking to, in person or over another channel. Never send a key by
plain text. Keys are set per contact from the conversation menu, so each person can have
their own code; the shared key in Settings still covers anyone you have not set one for,
and messages encrypted with it still open. There is no account and no key server. Encrypted
messages carry a short marker so the app knows to decode them; to anyone else, including
your carrier, they are unreadable text. Calls over the carrier line are ordinary phone
calls and are not end to end encrypted, and the app says so plainly.

Contacts and the sent message log are encrypted at rest using a key held in the Android
Keystore, so they stay protected on the device.

Per contact keys landed in 1.8.0. The next step is widening encryption beyond messages and
the contact vault to more of the app.

## Setting it as your default apps

Because the app is installed outside the Play Store, Android first puts SMS and phone
access behind Restricted settings. Allow it once:

1. Open Settings, then Apps, then DresSecureComms.
2. Tap the three dot menu in the top right.
3. Tap Allow restricted settings.

Then, inside the app, open Settings and Default apps and choose to set it as your default
SMS app, your default phone app, and your caller ID and spam app. Setting it as the default
SMS app is what lets it receive, show, and delete your text messages.

For the geo spoofer, turn on Developer options, open Select mock location app, and pick
DresSecureComms.

Also you may experience some issues with SMS and Calling due to the encryption and security unless you make it your default app

## Permissions and why

- **RECEIVE_SMS, READ_SMS, SEND_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH**: to act as the
  default SMS app: receive, read, send, and manage your texts.
- **CALL_PHONE, READ_PHONE_STATE, MANAGE_OWN_CALLS, ANSWER_PHONE_CALLS**: to place and
  handle calls as the default phone app.
- **READ_CALL_LOG, WRITE_CALL_LOG**: to show your call history and let you delete it.
- **READ_CONTACTS**: optional, only to import contacts into the encrypted vault.
- **ACCESS_FINE_LOCATION, ACCESS_MOCK_LOCATION**: for the geo spoofer. Mock location is a
  no-op permission that only makes the app selectable in Developer options.
- **USE_BIOMETRIC**: for the app lock.
- **POST_NOTIFICATIONS**: to show message and call notifications.
- **INTERNET**: used only by Threat Scan and File Scan to reach VirusTotal. Nothing else
  goes online. File Scan sends a fingerprint, and sends a file only when you pick that one
  file and confirm the upload.

## Privacy

No accounts. No analytics. No advertising. No Google or third-party services beyond the
VirusTotal lookup you trigger yourself in Threat Scan. Your messages, contacts, and call
history stay on your device.

## License

Apache-2.0. Copyright © 2026 The DresOS Foundation.

## Privacy & Permissions
DresSecureComms requires SMS, Phone, and Location permissions to act as your secure, offline default Dialer and SMS app. We do not collect data. 
Read our full [Privacy Policy](privacy_policy.md). 

Our app now gets called malware by Google play protect this is because of Googles misbehaviour when it comes to 3rd party apps especially if they're focused on securing you and being open sourced, they have also started this type of misbehaviour with GrapheneOS by banning revolut, we want to assure you that our application is clean and has been verified by IzzyOnDroid themselves along with it being whitelisted by some of the biggest security vendors that being Bitdefender, Avast, AVG and McAfee. If you have any prior worries to installing then please send us an email at security@dresos.org. Also we think Google is doing this to us because we have just hit 12k downloads and at least 10k of them are on DresSecureComms, so hopefully that gives you some insight.

## Donate

> **Help fund future development.** DresOS is built by a small open source team in our spare time. If our guide, Magisk modules or app saved you a weekend of research, please tip the jar. Funds go to test devices, dev stations, and developer time on updates and future projects.

[![Please Help fund future projects and keep this one going](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/dresos)


## Thanks

A huge thank you to [Fossify](https://github.com/FossifyOrg). We used Fossify Messages, Fossify Phone and Fossify Contacts as references while building this app, and without them the MMS robustness and several other quality of life improvements would not have been possible.

A huge thank you must also go to [Fake Traveler](https://github.com/mcastillof/FakeTraveler). Our mock location components are built on references from their application.

A huge thank you must also go to [URL Check](https://github.com/TrianguloY/URLCheck), As we used their scan part that connects to VirusTotal and built our URL scan engine up from that.

A huge thank you must also go to [Hypatia](https://github.com/Divested-Mobile/Hypatia). Hypatia is the on device scanner our File Scan is set to replace inside DresOS, and it set the bar for what a scanner on a de Googled phone should do. Version one of our engine checks fingerprints against VirusTotal; the offline signature scanning Hypatia does so well is what version two brings.


IF THERE IS A PROBLEM WITH THE APP OR YOU HAVE A FEATURE REQUEST STOP OPENING ISSUES ON GITHUB AS THEY GO UNANSWERED MOST OF THE TIME, SO EMAIL US security@dresos.org
