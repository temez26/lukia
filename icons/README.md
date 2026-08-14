# App icon

Lukia keeps books you already own in one place and gets out of the way while you
read them, so the mark is the book itself rather than a device or a shelf: four
page *leaves* (the bookbinding word for a page) fanning open from a shared
spine. The tile uses the same near-black material and continuous-corner
squircle as the app's Apple-derived palette in
[`Color.kt`](../shared/src/commonMain/kotlin/io/lukia/shared/presentation/theme/Color.kt),
so the icon reads as part of the system on every platform.

`Lukia-icon-3.png` and `Lukia-icon-4.png` are the earlier explorations the
current mark grew out of, kept for reference.

## Regenerating

Every platform asset is derived from the geometry in
[`generate_icons.py`](generate_icons.py) — there is no binary master to hand-edit.
Change the constants at the top of that file, then:

```bash
python -m pip install pillow
python icons/generate_icons.py
```

Shapes are drawn supersampled and reduced with Lanczos, and each size is
rendered from the vector geometry rather than downscaled from a large PNG, which
is what keeps the 16 px and 48 px variants crisp. Below 40 px the fan drops to
three sheets and the soft shadows are skipped, because four sheets turn to mush
at that scale.

## What gets written

| Target | Files |
|---|---|
| Source | `icons/lukia-icon.svg` (flat vector master), `icons/lukia-icon-1024.png` |
| Android | `mipmap-*/ic_launcher*.png` — legacy, round, and the adaptive background / foreground / monochrome layers |
| iOS | `AppIcon.appiconset/app-icon-1024{,-dark,-tinted}.png` |
| Desktop | `desktopApp/src/main/resources/icons/lukia.{icns,ico,png}` |

Notes on the platform rules the script follows:

- **iOS** ships an opaque full-bleed tile; the dark and tinted appearances ship
  the mark on transparency so the system supplies its own background. iOS
  applies the squircle mask itself, so the source must not be pre-rounded.
- **Android** adaptive layers are 108 dp with the mark spanning 48 dp, well
  inside the 66 dp keyline, so no launcher mask can clip it. The monochrome
  layer keeps gaps between sheets because themed icons flatten to a single
  colour and would otherwise merge into a blob.
- **macOS** icons sit on an 824/1024 tile with room for the system shadow, and
  the `.icns` is written with the same OSTypes `iconutil` emits.

The SVG is the flat artwork; the soft shadows and rim light are a raster pass
and only exist in the PNGs.
