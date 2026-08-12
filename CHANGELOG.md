# Changelog

## 1.8.4

### Fixed
- The geo spoofer holding your location instead of losing it. It used to set the position once, so the moment an app like Google Maps refreshed from its own sources it overwrote it and your real location came back. It now keeps reapplying the position for as long as spoofing is on, so it stays put. Thank you to the user who recorded this next to another spoofing app and showed the difference.
- The speakerphone sometimes not turning on during a call. The app was switching audio to the speaker without first putting the phone into its in call audio state, so depending on timing and device the request was ignored. It now sets that state when the call screen opens and puts it back when the call ends, which is what makes the speaker toggle work reliably. Turning the speaker off now also hands back to a wired headset if one is plugged in, instead of always forcing the earpiece.

### Security
- Closed one of the app's internal receivers to the app itself. It had been reachable by other apps on the device even though the component that drives it only ever sends to this app by name, so nothing outside the app had any reason to reach it. No other app can trigger it now.
- Guarded the screen that other apps hand a phone number or message to when they ask this app to start a new message. A malformed request from another app can no longer crash it.

## 1.8.2

### Fixed
- Messages from one particular contact still arriving in the notification and never appearing in the conversation. The 1.8.1 fix treated this as the app needing to match phone numbers itself and stitch several conversations back together, and that was the wrong diagnosis. Android already decides which conversation a message belongs to, in the system messaging database, using its own rules for what counts as the same number. The app was second guessing that with its own matching and then searching for messages across whichever conversations it thought were related, and it also stopped searching after a fixed number of records, so a contact with a long history could fall outside the search entirely while everyone else looked fine. The app now asks Android which conversation a message belongs to and reads that conversation directly, the same way the system messaging app does. No number matching of its own, no stitching, and no limit on how far back it will look.
- The conversation list is now read from Android's own conversation index rather than assembled by trawling every message on the device. It is faster on a large message history and it can no longer miss a conversation.
- Incoming messages are no longer stored at all if the app is not currently the default messaging app, which prevents a half stored message that shows in the notification and nowhere else.

### Added
- A conversation diagnostic. Press and hold a conversation and choose "Why are messages missing" to get a plain text report showing which conversation Android assigned the messages to, which one the app is reading, what the number looks like as actually stored, and the last few incoming messages the app handled. It can be copied and sent to security@dresos.org. If the problem happens again this tells us exactly where the mismatch is instead of leaving us guessing.

## 1.8.1

### Added
- File Scan can now send a file to VirusTotal when VirusTotal has never seen it. Until now it told you the file was unknown and left you there, which is the most common result for anything built outside the Play Store. The upload is per file, you are asked every time, and the prompt says plainly that the file leaves your phone and that VirusTotal keeps what it is sent and shares it with the antivirus companies it works with. Nothing is ever uploaded on its own and a sweep of your installed apps never uploads anything. Fingerprint checking is unchanged and is still what happens by default.
- You can now scan one app on its own instead of sweeping all of them, and after a full sweep the apps VirusTotal has never seen are listed so you can send one for analysis.
- The geo spoofer now sets the network location as well as GPS, so apps that read your rough location instead of a satellite fix see the spoofed position too.

### Fixed
- Messages you received sometimes showed up only in the notification and never in the conversation. Android can file one person under two separate conversation ids when their number reaches you in one format and you saved it in another, with the country code against without it for instance, and the app was only ever reading one of them. Conversations are now matched on the number itself, so everything with that person appears in a single thread whichever format it arrived in, and threads that had already been split are shown joined back together without your messages being moved or rewritten.
- Incoming calls no longer play the message notification sound just before the ringtone. The call notification was going out on a channel that carried Android's default notification sound, so you heard that first and the ringtone second.
- The screen now always comes back on after you move the phone away from your ear. The app had been asking Android to hold the screen off until the proximity sensor reported clear, which never happens on phones that use virtual proximity sensing instead of a real sensor, so the screen stayed dark and you had to wake it to end the call. It now brings the screen straight back, only blanks the screen in the first place when the phone exposes a real proximity sensor, and abandons blanking for the rest of the call if it detects the phone being moved while the screen is off. There is also a new switch under Settings, Calls, to turn the whole thing off.
- Conversations now show the contact name for people saved in your phone's own contacts and not only in the encrypted vault, which is what the notification had been showing all along. The conversation title does the same.
- A message that fails to send is now marked as failed and you are told about it, instead of the app recording it as sent and saying nothing. Sending is confirmed by the network now rather than assumed the moment you press send.
- Replying straight from a notification now shows up in the conversation, and a reply longer than one message is split properly instead of being dropped.
- A message can no longer be filed twice if Android delivers it twice.
- Deleting a conversation now clears every thread that belongs to that person, not just the one that happened to be open.

## 1.8.0

### Added
- File Scan engine, version one. This replaces the Hypatia anti malware scan within The DresOS Android Defensive Security System, and version two adds the offline signature scanning Hypatia is known for. It is going to set the bar for what an on device scanner has to do. This first version checks a single file, or every app on your device, by fingerprint: the file never leaves the phone, only its SHA-256 goes out, and it comes back checked against seventy or more antivirus engines at once. A free VirusTotal key allows four lookups a minute, so a full sweep of your apps paces itself and can be stopped at any point. Offline signature scanning, which is Hypatia's own approach, is what version two adds.
- Spam Shield. Incoming calls are screened before your phone rings. Premium rate numbers are rejected, short codes are silenced, and callers faking the first digits of your own number are flagged. You can also silence anyone not in your contacts or reject withheld numbers. Saved contacts always ring through. Everything it blocks is listed in Settings so you can undo any of it, and the whole thing runs on the device, so no number is ever sent anywhere to be checked.
- Encryption keys are now per contact. Set a code for one person from the conversation menu and it is used only for them, instead of one code shared with everybody. The old shared key still covers anyone you have not set a code for, and messages encrypted with it still open.
- Block or unblock any number straight from the call log.
- Starting a new message now suggests your contacts as you type, from both the encrypted vault and the device, so you no longer have to go and look a number up first.

### Fixed
- The screen now turns off when you hold the phone to your ear during a call, and comes back when you take it away.
- The option to save a number to your contacts is now hidden once that number is already saved, in conversations, the call log, and the in-call screen.

## 1.7.0

### Added
- Search in your messages, contacts, and call history. Start typing to filter the list by name or number. 
- Timestamps throughout: the conversation list shows when each thread was last active, each message in a thread shows its time, and the call log shows how long each answered call lasted.
- The dialer now shows matching numbers from your history as you type, so you can tap a result instead of typing the whole number.
- Dialer conveniences: paste a copied number straight into the dialer, a backspace key to delete the last digit, and long press backspace to clear the whole number.
- On the in-call screen, the keypad now shows the digits you press, with paste and backspace, so you can see and edit what you send to phone menus and PINs.
(thank you to all those who put in feature requests)

## 1.6.3

### Fixed
- Receiving a picture now works and no longer crashes the app. Incoming pictures are downloaded through Android's own MMS handling, which fixes both the crash and the picture never arriving, and any remaining download failure is contained instead of taking the app down.
- Pictures you send now actually reach the other person. They are compressed to a carrier-safe size before sending, so they are no longer silently dropped in transit for being too large.

### Changed
- Pictures and group messages (MMS) are now always sent unencrypted so they arrive readable on any phone. Encryption still applies to SMS text. When you send a picture with encryption switched on, the app tells you it is going unencrypted.

## 1.6.1

### Fixed
- The URL scanner no longer freezes. It had been polling VirusTotal every few seconds for up to a minute, which stalled the scan and tripped the free tier rate limit. It now checks the link in a single request the way VirusTotal expects: if the link has been seen before the result comes back immediately, otherwise it is submitted and you tap Scan again in about a minute.
- Save to contacts now works. It asks for a name and saves the number into your encrypted contacts vault; before, it tried to hand off to an external contacts app that does not exist on a de-Googled device, so nothing happened.

### Added
- If the app ever crashes, you can now send us the full error in one tap: a Share report button on the crash screen, and a Share crash log option in Settings, both attach the complete report to an email. This is the single most useful thing you can do to help us fix MMS problems.
- During a call you can copy the number or save it to your contacts.
- In a conversation you can call the number, save it to your contacts, or copy it, and long pressing a message copies its text.
- Call log entries and conversations now open a full action menu on tap or long press: Call, Message, Save to contacts, Copy number, and Delete.
- A new File Scan section. Its engine is being built from Hypatia's scanner and will arrive in a later update.

## 1.6.0

### Added
- Incoming picture messages are now fully supported. Photos download automatically, appear inside the conversation, raise a notification, and picture-only conversations show up in the message list.

### Fixed
- Sending a photo or a group message no longer crashes. The bundled MMS classes were being rewritten by the code optimiser and colliding with Android's own framework classes at send time.
- Messages you send now appear in the conversation straight away and remain in your history, whether or not the app is set as the default SMS app.

### Changed
- The in-call keypad and the call control buttons are now much larger and easier to tap.
- Message notifications now carry a Copy action, and tapping a notification opens the conversation.

### Project
- Renamed to The DresOS Foundation and moved to the new organisation. The site is now at dresos.org and contact is security@dresos.org.

## 1.5.3

### Security
- The SMS shared key and the VirusTotal API key are now encrypted at rest with the Android
  Keystore rather than stored in plaintext preferences. Existing values migrate transparently
  on first read, so no re-entry is needed.

### FOSS
- The app now declares open_source and open_source_license metadata, so app managers such as
  Inure correctly identify DresSecureComms as free and open source.

## 1.5.2

### Fixes
- Incoming SMS no longer posts a separate notification per message segment. A multi-part
  text now arrives as a single notification, and further messages from the same sender
  update that conversation's notification instead of stacking new ones.
- The incoming call screen now appears over the lock screen and turns the screen on in
  every app state. It is launched through a full-screen call notification rather than a
  plain activity start, which the system reliably surfaces over the keyguard.
- Call history now shows the saved contact name in place of the raw number.
- The incoming call screen now shows the saved contact name in place of the raw number.
- Message list, call history, and the incoming call screen now share one number-to-name
  matcher that tolerates differences in formatting (country code, spacing, leading zero),
  so more contacts resolve correctly.

### Robustness
- Call history and message queries are now bounded so a very long history cannot pull an
  unbounded number of rows into memory.
- Call state in CallManager is marked volatile for safe access across the telecom and UI
  threads.
- Contact name resolution for lists runs off the main thread.

## Coming soon
- Importing your own PGP, age or SSH ED25519 keys to sign your messages.
- A spam call shield that recognises and blocks known spam and scam callers before your
  phone rings.
- Stronger encryption across more of the app, beyond messages and the contact vault.
- The next Threat Scan engine, now close to complete, extending scanning to media, files,
  APKs, ZIPs, and effectively any content.
- Several smaller refinements throughout.
