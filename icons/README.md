# App icon

Lukia keeps books you already own in one place and gets out of the way while you
read them, so the mark is the book itself rather than a device or a shelf: four
page *leaves* (the bookbinding word for a page) fanning open from a shared
spine. The tile uses the same near-black material and continuous-corner
squircle as the app's Apple-derived palette in
[`Color.kt`](../shared/src/commonMain/kotlin/io/lukia/shared/presentation/theme/Color.kt),
so the icon reads as part of the system on every platform.

`lukia-icon.svg` is the master artwork and `lukia-icon-1024.png` is the rendered
tile. `Lukia-icon-3.png` and `Lukia-icon-4.png` are the earlier explorations the
current mark grew out of, kept for reference.

## Where the assets live

| Target | Files |
|---|---|
| Android | `androidApp/src/main/res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png`, and the adaptive `ic_launcher_background/foreground/monochrome.png` layers, at all five densities |
| iOS | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024{,-dark,-tinted}.png` |
| Desktop | `desktopApp/src/main/resources/icons/lukia.{icns,ico,png}` |

Legacy Android launcher icons are 48 dp (48–192 px), adaptive layers are 108 dp
(108–432 px), the `.ico` carries 16/24/32/48/64/128/256, and the `.icns` carries
16 through 1024 under the OSTypes `iconutil` emits (`icp4`, `ic11`, `icp5`,
`ic12`, `ic07`, `ic13`, `ic08`, `ic14`, `ic09`, `ic10`).

## Rules any replacement has to keep

- **iOS** ships an opaque full-bleed tile — iOS applies the squircle mask
  itself, so the source must not be pre-rounded or carry transparency. The dark
  and tinted appearances ship the mark on transparency instead, because the
  system supplies its own background for those; the tinted asset is greyscale.
- **Android** adaptive layers are 108 dp with the mark spanning 48 dp, well
  inside the 66 dp keyline, so no launcher mask can clip it. The monochrome
  layer keeps visible gaps between the sheets: themed icons flatten to a single
  colour, and without the gaps the fan merges into a blob.
- **macOS** icons sit on an 824/1024 tile, leaving room for the system shadow.
  Windows and Linux use the full-bleed squircle.
- **Small sizes** drop the fourth sheet and the soft shadows below roughly
  40 px, and each size is rendered at its own resolution rather than downscaled
  from the 1024 master — that is what keeps the 16 px and 48 px icons crisp.

## The artwork

- Tile: linear gradient `#1F1F23` to `#08080A`, running down and slightly right.
- Sheets, front to back: `#FFFEFB`→`#C9C5BE`, `#E4E1DB`→`#A6A39D`,
  `#C1BEB8`→`#7C7A77`, `#989691`→`#5C5B59`, each lit from the top left.
- The fan spans 64% of the tile, centred, and its sheets sit at 0°, 13°, 25° and
  36° from vertical at 100%, 93%, 86% and 80% length.
- The tile outline is a superellipse (exponent 5), which approximates Apple's
  continuous-corner rounding closely enough to sit beside system icons.

The SVG is the flat artwork; the soft drop shadows between sheets and the rim
light on the tile edge only exist in the rendered PNGs.
