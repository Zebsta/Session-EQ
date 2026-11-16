# Third-party components and acknowledgements

## Original application code

Session EQ's original application code is licensed under Apache License 2.0.
See [LICENSE](LICENSE) and [NOTICE](NOTICE). Third-party material retains its
own provenance and applicable terms; no ownership of AIMP is claimed.

## Google Material Icons

- Project: https://github.com/google/material-design-icons
- License: Apache License 2.0, included in [design/LICENSE-Material-Icons.txt](design/LICENSE-Material-Icons.txt)
- Tune: https://github.com/google/material-design-icons/blob/master/src/image/tune/materialicons/24px.svg
- Settings: https://github.com/google/material-design-icons/blob/master/src/action/settings/materialicons/24px.svg

The SVG paths are adapted into Android vector drawables. The tune glyph is also
placed inside a circular badge for launcher, splash and monochrome icons, and
inside an outlined ring for notifications.
The full upstream license is packaged in the APK as a raw resource.

## AIMP inspiration and numeric presets

- AIMP author: Artem Izmaylov.
- Website: https://aimp.ru/
- Official Windows download: https://aimp.ru/?do=download&os=windows
- Preset provenance: [reference/aimp-windows/README.md](reference/aimp-windows/README.md)

The UI is independently implemented from a user-provided reference screenshot.
The launcher disk uses the original AIMP asset's numeric gradient colors;
see [design/NOTICE.md](design/NOTICE.md) for the exact palette and source resource.
The numeric 20-band values shown in that screenshot form the preset named Rock.
Twenty-four further numeric presets are extracted from the official AIMP Windows
distribution and are identified in the UI with the `AIMP Windows · ` prefix.
Those values are credited to their source, not claimed as an original dataset.

No AIMP application binaries, decompiled source, logos or DSP implementation
are distributed here. Session EQ uses Android's DynamicsProcessing API instead.
The original AIMP distribution is not part of this repository. The extraction
script requires a separately downloaded official archive.

Matching frequency values and gains does not establish identical filter
response, limiter behavior or sound across the two applications.

## Gradle Wrapper

The wrapper files come from the Gradle v8.10.2 source distribution:
https://github.com/gradle/gradle/tree/v8.10.2/gradle/wrapper

Gradle is licensed under Apache License 2.0. The wrapper JAR and distribution
checksums are verified/pinned. Android build dependencies are downloaded during
the build and are not vendored into this source repository.
