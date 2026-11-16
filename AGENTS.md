# Session EQ

Independent Android session equalizer. The implementation is Java with Android
framework views and DynamicsProcessing; there are no application dependencies.

- Read README.md and THIRD_PARTY_NOTICES.md before changing branding or attribution.
- Keep the screenshot Rock preset unchanged unless the user explicitly requests it.
  AIMP Windows Rock is a separate preset.
- Preserve light/dark themes and the selected black sliders in an orange disk on
  a dark background. Use the documented AIMP asset gradient; do not restore E.
- Preserve the event-based lifecycle. No periodic status/audio-session polling,
  wake locks or foreground service when OFF or without native effects.
- Do not change filter parameters while doing unrelated UI work.
- AIMP must never be offered by player discovery or connected by incoming OPEN
  or effect-panel requests. Preserve SessionPolicy checks on all attach paths.
- Player discovery is on demand and requires optional one-time DUMP access.
  Never add background scanning, notification access, automatic permission grants
  or manual session-ID entry to the normal picker flow.
- Run `./gradlew verifyCore :app:assembleDebug` for implementation changes.
- Actual audio behavior and battery use require device testing; distinguish local
  checks from measured device results.
- Before any ADB interaction with a device, check `adb devices -l` and use only an
  authorized available device. The user installs APK updates unless asked otherwise.
- Keep credentials, signing keys, APK output, SDK paths and original AIMP archives
  out of source control. APK files belong in build artifacts/releases.
- Use real paragraphs in Git commit messages, never literal `\\n` escapes.
