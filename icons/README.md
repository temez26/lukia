# App icon

Four page leaves fanning open from a shared spine, on a near-black squircle.

- `lukia-icon.svg`: master artwork (flat; shadows exist only in the PNGs)
- `lukia-icon-1024.png`: rendered tile

## Where the assets live

| Target | Files |
| --- | --- |
| Android | `androidApp/src/main/res/mipmap-*/`: `ic_launcher`, `ic_launcher_round`, and the adaptive background, foreground and monochrome layers |
| iOS | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/`: default, dark and tinted |
| Desktop | `desktopApp/src/main/resources/icons/lukia.{icns,ico,png}` |

## Rules for a replacement

- **iOS:** the default tile is opaque and full-bleed, not pre-rounded. The dark
  and tinted variants are the mark on transparency; tinted is greyscale.
- **Android:** adaptive layers are 108 dp with the mark inside 48 dp. The
  monochrome layer keeps gaps between the sheets.
- **macOS:** the tile is 824/1024 of the canvas. Windows and Linux use the full
  squircle.
- **Small sizes** (below about 40 px) drop the fourth sheet and the shadows, and
  are rendered at their own resolution, not downscaled.

## Artwork values

- Tile: linear gradient `#1F1F23` to `#08080A`, superellipse outline
  (exponent 5).
- Sheets, front to back: `#FFFEFB`→`#C9C5BE`, `#E4E1DB`→`#A6A39D`,
  `#C1BEB8`→`#7C7A77`, `#989691`→`#5C5B59`.
- The fan spans 64% of the tile, with sheets at 0°, 13°, 25° and 36° from
  vertical and 100%, 93%, 86% and 80% length.
