# Changelog

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
