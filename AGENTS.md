# Session EQ

Independent Android session equalizer. The implementation is Java with Android
framework views and DynamicsProcessing; there are no application dependencies.

- Read README.md and THIRD_PARTY_NOTICES.md before changing branding or attribution.
- Keep the screenshot Rock preset unchanged unless the user explicitly requests it.
  AIMP Windows Rock is a separate preset.
- Preserve light/dark themes and the selected black sliders in an orange disk on
  a dark background. Use the documented AIMP asset gradient; do not restore E.
- Preserve the event-based lifecycle. No periodic status/audio-session polling,
  wake locks or foreground service when OFF. The user-authorized per-player
  auto-connect mode may retain a quiet foreground service with no effects while
  ON and waiting for selected players; do not scan repeatedly during that wait.
- Do not change filter parameters while doing unrelated UI work.
- AIMP OPEN/external-panel requests must not create effects. Preserve SessionPolicy.
- DUMP-based discovery was resumed at the user's request for version 0.5.2.
  Keep scans on demand and keep ordinary external-player integration usable
  without DUMP. Do not require discovery for the ON/OFF toggle.
- Run `./gradlew verifyCore :app:assembleDebug` for implementation changes.
- Actual audio behavior and battery use require device testing; distinguish local
  checks from measured device results.
- Before any ADB interaction with a device, check `adb devices -l` and use only an
  authorized available device. The user installs APK updates unless asked otherwise.
- Keep credentials, signing keys, APK output, SDK paths and original AIMP archives
  out of source control. APK files belong in build artifacts/releases.
- Use real paragraphs in Git commit messages, never literal `\\n` escapes.
