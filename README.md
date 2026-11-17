# Session EQ

An independent, open-source 20-band Android equalizer for audio sessions from
other music players. Inspired by the AIMP equalizer interface, implemented in
Java using Android DynamicsProcessing.

![Session EQ equalizer interface in dark and light themes](design/interface-preview.png)

*Interface preview rendered from the app's layout, colors and Rock preset;
not a device screenshot.*

## Features

- 20 frequency bands, ±15 dB gain; balance and preamp.
- A curve with horizontally draggable band points and precise numeric input.
- Light/dark/system themes and adaptive icons with black sliders in an orange disk.
- Screenshot-based Rock, Flat, 24 separately labelled AIMP Windows presets,
  and saved user presets.
- ON/OFF, optional stereo-linked limiter, and AUTO preamp headroom compensation.
- External-player effect-panel integration, session OPEN/CLOSE handling,
  manual session ID entry, and readable diagnostics.
- Event-based status updates with no periodic background polling, wake locks,
  network access, microphone permission or Accessibility service.

## Compatibility and limitations

Version 0.6.0 includes optional DUMP-based player discovery at the user's request.
Start playback, open Settings → Connect audio session, then choose a player.
AIMP is excluded. Scans run only on request and recheck the selected player.
The existing ON/OFF toggle and external-player integration work without DUMP;
explicit numeric session entry remains an advanced fallback.

After installing the APK, grant discovery access once over USB debugging:

```sh
adb shell pm grant dev.local.sessioneq android.permission.DUMP
```

The app displays whether access is granted in connection diagnostics. Discovery
reads AudioService through an in-memory pipe with concurrent reading, a 2 MiB
limit and a five-second timeout; it does not upload or log the full dump.
If AudioService dumping calls routing APIs requiring system permissions,
the app falls back to the native `media.audio_flinger` dump using the same DUMP
permission. Only current active output-track tables with explicit UID/session
identity are accepted; historical/local logs and recording tracks are excluded.
No routing or other privileged permission is requested.
Denied access is shown as the actual error; DUMP setup is offered only when
the permission is missing. The Android dump format is
device-dependent. There is no background polling, root or microphone access.
Package visibility is limited to launcher apps and AIMP; ambiguous shared UIDs,
other user profiles, calls and non-playing streams are not offered.

Android 9+ (API 28). Actual support depends on the device's DynamicsProcessing
implementation and the source player's audio output path. Creating an enabled
object does not by itself prove that every output is processed.

Select Session EQ as an external equalizer in the music player, then restart
playback. If the player directs session notifications to a different equalizer,
use Settings → Connect audio session and enter the **current** session ID.
Session IDs can change; a previously observed ID is not a permanent player ID.

A stopped background service may show a notification asking you to reconnect
when a new session arrives. Tap it to resume: Android restricts starting
foreground services from background receivers.

The original Rock values from the reference screenshot remain separate from
`AIMP Windows · Rock`. Equal numeric settings do not guarantee identical sound
to AIMP: the filter implementation and limiter differ.

**AUTO currently compensates preamp headroom.** It does not implement AIMP's
preset selection by track genre. It reduces input gain by the largest positive
band boost (including preamp), while preserving the displayed preamp value.

## Idle behavior

The settings screen uses a bound service. With no auto-connect selection, native
effects and the foreground service are retained only while effects are attached
and ON. OFF releases effects and the foreground service. Closing the final audio
session removes the started service unless auto-connect is armed.

In the player picker, check **Auto** for each app that should connect automatically.
Selection is stored by package name, never by audio-session ID. Checked apps stay
in the picker while stopped so they can be unchecked. Selecting Auto enables EQ;
for an already playing app its sessions are rechecked and attached. Tapping a row
still connects that player once. Unchecking prevents future automatic connections;
the current connection remains until CLOSE, OFF or Stop.

When ON with at least one selected app, a quiet foreground notification remains
while waiting. Incoming OPEN events from selected players attach their current
session IDs, and CLOSE releases their native effects. No timed scans or wake
locks are added. Other players may show the existing tap-to-connect notification;
AIMP remains blocked. Player integration without OPEN/CLOSE still needs manual
discovery. DUMP is needed for discovery, not for receiving session events.

OFF and Stop suspend waiting and processing; player selections are preserved.
Turning ON or reopening an ON, armed app resumes the service. The armed service
requests system restart after process eviction, but Android can still stop it;
if it is absent, the tap-to-connect notification is the fallback. After force-stop
or reboot, open Session EQ again. No boot receiver is installed.

A normal pause that keeps the audio session open retains the connection so music
can resume without manual reconnection. There are no recurring Session EQ Java
timers during that pause. If a player never sends CLOSE, use OFF or Stop service
for full cleanup. Battery savings have **not** yet been measured on hardware;
system audio processing must be considered separately from this app's process.

## Build

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0. The Gradle
8.10.2 wrapper is included; the application has no runtime library dependencies.

Set `ANDROID_HOME` to your SDK, or create an untracked `local.properties` file:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

On Linux/macOS:

```sh
./gradlew verifyCore :app:assembleDebug
```

On Windows:

```powershell
.\gradlew.bat verifyCore :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
GitHub Actions runs the same checks and uploads the debug APK as a workflow
artifact. JVM checks cover EQ math, screenshot Rock values, and session registry
behavior; audio lifecycle and battery use still need device tests.

An alternative Linux SDK-tools build is available as `./build.sh`; it supports
`ANDROID_HOME`, `ANDROID_SDK_ROOT` or `SESSION_EQ_SDK`. Its APKs go into `dist/`.
Its development key is outside the repository, under
`~/.local/share/session-eq/debug.keystore`, unless `SESSION_EQ_SIGNING_KEY` is set.
This is a test key; choose separate private signing for public releases.

**An APK from another development key will not update an existing installation.**
Keep your signing key outside Git when producing updates. CI debug keys are
separate from the local key used for earlier personal builds.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).
Do not commit APKs, SDK paths, credentials or signing keys. The original AIMP
archive is intentionally excluded; only numeric preset data, provenance and a
reproducible extraction script are retained.

## License and attribution

Original Session EQ code: [Apache License 2.0](LICENSE).
See [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for icon
licenses and preset provenance. No AIMP source code or original application
binaries are included. Session EQ is not affiliated with or endorsed by AIMP.
