# Icon sources

The logo combines an original circular badge arrangement with the Material tune icon,
following the user's selected design. The former E variant has been removed
from the current application resources. No AIMP logo file is included.

The regular launcher/splash icon has a dark background (`#1A1A1A`), a filled
orange disk, and black sliders (`#000000`). The orange radial gradient is taken
from the installed AIMP Android v4.31.1750 (04.11.2026) vector resource:

- center, offset 0: `#F7CA00`;
- offset 0.8: `#F7C000`;
- edge, offset 1: `#F7A200`.

These values come from `res/drawable-nodpi-v4/$ic_launcher_foreground__0.xml`;
the background is `color/ic_launcher_background`. Only palette/gradient
parameters are reused; no AIMP triangle or logo image is included.
The disk keeps the previous outer badge diameter and slider geometry.
Notification and Android-themed monochrome variants are separate alpha masks.

The tune and settings icons are from Google's Material Icons project:

- https://github.com/google/material-design-icons/blob/master/src/image/tune/materialicons/24px.svg
- https://github.com/google/material-design-icons/blob/master/src/action/settings/materialicons/24px.svg

Licensed under Apache License 2.0. The full license is in
`LICENSE-Material-Icons.txt` and is packaged in the APK as a raw resource.

`icons-preview.svg` and `icons-preview.png` preview the vector assets under
launcher masks. They are design previews, not screenshots from a phone.
