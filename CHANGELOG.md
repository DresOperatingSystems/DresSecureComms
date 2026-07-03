# Changelog

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

## Coming soon
- Importing your own PGP, age or SSH ED25519 keys to sign your messages.

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
- A spam call shield that recognises and blocks known spam and scam callers before your
  phone rings.
- Stronger encryption across more of the app, beyond messages and the contact vault.
- The next Threat Scan engine, now close to complete, extending scanning to media, files,
  APKs, ZIPs, and effectively any content.
- Several smaller refinements throughout.
