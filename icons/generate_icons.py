"""Renders every platform app-icon asset for Lukia from a single vector source.

The mark is a fan of four page "leaves" opening from a shared spine: leaf is the
bookbinding word for a page, and the fan is what a book does when you open it.
The tile borrows Apple's near-black material and continuous-corner squircle so
the icon sits naturally next to system apps on every platform.

Run with Pillow installed:

    python -m pip install pillow
    python icons/generate_icons.py
"""

from __future__ import annotations

import math
import struct
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]

# --------------------------------------------------------------------------
# Brand
# --------------------------------------------------------------------------

BACKGROUND = ((0.0, (31, 31, 35)), (1.0, (8, 8, 10)))
BACKGROUND_DIRECTION = (0.35, 1.0)

# Front sheet first. Warm greys read as paper against the neutral black tile.
SHEETS = (
    ((0.0, (255, 254, 251)), (0.55, (242, 239, 233)), (1.0, (201, 197, 190))),
    ((0.0, (228, 225, 219)), (1.0, (166, 163, 157))),
    ((0.0, (193, 190, 184)), (1.0, (124, 122, 119))),
    ((0.0, (152, 150, 145)), (1.0, (92, 91, 89))),
)
LIGHT_DIRECTION = (0.62, 1.0)

# Angle from vertical and length, front sheet first.
FAN = ((0.0, 1.0), (13.0, 0.93), (25.0, 0.86), (36.0, 0.80))

# One leaf, spine at the origin, pointing up, unit length.
LEAF_TIP = (0.10, 1.0)
LEAF_LEADING = ((-0.30, 0.28), (-0.20, 0.86))
LEAF_TRAILING = ((0.17, 0.72), (0.10, 0.20))

SQUIRCLE_EXPONENT = 5.0
MARK_BOX = 0.64  # fraction of the tile the mark spans
MARK_CENTER = (0.5, 0.495)

# --------------------------------------------------------------------------
# Geometry
# --------------------------------------------------------------------------


def cubic(p0, c1, c2, p3, steps=96):
    points = []
    for step in range(steps + 1):
        t = step / steps
        u = 1.0 - t
        points.append(
            (
                u * u * u * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * p3[0],
                u * u * u * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * p3[1],
            )
        )
    return points


def leaf_controls(angle_deg, length):
    """Leaf control points in tile space (y down), spine at the origin."""
    radians = math.radians(angle_deg)
    cos, sin = math.cos(radians), math.sin(radians)

    def place(point):
        x, y = point[0] * length, point[1] * length
        # Rotate clockwise about the spine, then flip to a y-down tile space.
        return (x * cos + y * sin, -(-x * sin + y * cos))

    tip = place(LEAF_TIP)
    return (
        (0.0, 0.0),
        place(LEAF_LEADING[0]),
        place(LEAF_LEADING[1]),
        tip,
        place(LEAF_TRAILING[0]),
        place(LEAF_TRAILING[1]),
    )


def leaf_polygon(angle_deg, length):
    spine, lead1, lead2, tip, trail1, trail2 = leaf_controls(angle_deg, length)
    return cubic(spine, lead1, lead2, tip) + cubic(tip, trail1, trail2, spine)[1:]


def fan_fit(sheet_count):
    """Transform that centres the fan in MARK_BOX, as a `point -> point` callable."""
    flat = [point for angle, length in FAN[:sheet_count] for point in leaf_polygon(angle, length)]
    min_x = min(x for x, _ in flat)
    max_x = max(x for x, _ in flat)
    min_y = min(y for _, y in flat)
    max_y = max(y for _, y in flat)
    scale = MARK_BOX / max(max_x - min_x, max_y - min_y)
    offset_x = MARK_CENTER[0] - (min_x + max_x) / 2 * scale
    offset_y = MARK_CENTER[1] - (min_y + max_y) / 2 * scale
    return lambda point: (point[0] * scale + offset_x, point[1] * scale + offset_y)


def fan_polygons(sheet_count):
    """Leaf outlines fitted into MARK_BOX, back sheet first so they draw in order."""
    fit = fan_fit(sheet_count)
    polygons = [[fit(point) for point in leaf_polygon(angle, length)] for angle, length in FAN[:sheet_count]]
    return list(reversed(polygons))


def squircle_polygon(inset=0.0, steps=720):
    """Superellipse approximation of Apple's continuous-corner rounded square."""
    radius = 0.5 - inset
    power = 2.0 / SQUIRCLE_EXPONENT
    points = []
    for step in range(steps):
        angle = 2 * math.pi * step / steps
        cos, sin = math.cos(angle), math.sin(angle)
        points.append(
            (
                0.5 + radius * math.copysign(abs(cos) ** power, cos),
                0.5 + radius * math.copysign(abs(sin) ** power, sin),
            )
        )
    return points


# --------------------------------------------------------------------------
# Raster helpers. Everything is drawn supersampled and reduced at the end,
# which is what keeps the edges clean at launcher sizes.
# --------------------------------------------------------------------------


def supersample(size):
    return 4 if size <= 256 else 3


def to_pixels(polygon, resolution):
    return [(x * resolution, y * resolution) for x, y in polygon]


def mask_of(polygon, resolution):
    mask = Image.new("L", (resolution, resolution), 0)
    ImageDraw.Draw(mask).polygon(to_pixels(polygon, resolution), fill=255)
    return mask


# Gradients run across the tile diagonal, so a 45 degree ramp reaches corner to
# corner and straighter ones stay soft. Shared with the SVG export.
GRADIENT_LENGTH = math.sqrt(2)


def sample_stops(stops, t):
    if t <= stops[0][0]:
        return stops[0][1]
    for (start, low), (end, high) in zip(stops, stops[1:]):
        if t <= end:
            local = (t - start) / (end - start)
            return tuple(round(low[channel] + (high[channel] - low[channel]) * local) for channel in range(3))
    return stops[-1][1]


def gradient(resolution, stops, direction):
    """Linear gradient across the tile, `direction` given in tile space (y down)."""
    strip = Image.new("RGB", (1024, 1))
    strip.putdata([sample_stops(stops, index / 1023) for index in range(1024)])

    # Rotating a horizontal ramp is cheaper than evaluating the gradient per
    # pixel, and a low-resolution ramp upscales without visible banding.
    working = 768
    ramp = strip.resize((working, working), Image.Resampling.BILINEAR)
    angle = math.degrees(math.atan2(-direction[1], direction[0]))
    rotated = ramp.rotate(angle, resample=Image.Resampling.BICUBIC, expand=False)
    span = int(working / GRADIENT_LENGTH) - 2
    origin = (working - span) // 2
    cropped = rotated.crop((origin, origin, origin + span, origin + span))
    return cropped.resize((resolution, resolution), Image.Resampling.BICUBIC)


def paint(layer, polygon, stops, direction=LIGHT_DIRECTION):
    resolution = layer.size[0]
    layer.paste(gradient(resolution, stops, direction), (0, 0), mask_of(polygon, resolution))


def cast_shadow(layer, polygon, blur, offset, opacity):
    resolution = layer.size[0]
    shifted = [(x + offset[0], y + offset[1]) for x, y in polygon]
    mask = mask_of(shifted, resolution).filter(ImageFilter.GaussianBlur(blur * resolution))
    mask = mask.point(lambda value: round(value * opacity))
    layer.paste(Image.new("RGB", (resolution, resolution), (0, 0, 0)), (0, 0), mask)


def dilate(mask, amount):
    blurred = mask.filter(ImageFilter.GaussianBlur(amount))
    return blurred.point(lambda value: 255 if value > 24 else 0)


def desaturate(layer):
    luminance = layer.convert("L")
    return Image.merge("RGBA", (luminance, luminance, luminance, layer.getchannel("A")))


# --------------------------------------------------------------------------
# Layers
# --------------------------------------------------------------------------

# Below this size the fourth sheet and the soft shadows only muddy the mark.
DETAIL_THRESHOLD = 40


def is_detailed(size):
    return size >= DETAIL_THRESHOLD


def sheet_count(size):
    return len(FAN) if is_detailed(size) else 3


def blank(resolution):
    return Image.new("RGBA", (resolution, resolution), (0, 0, 0, 0))


def draw_fan(layer, size):
    sheets = sheet_count(size)
    for index, polygon in enumerate(fan_polygons(sheets)):
        sheet = sheets - 1 - index
        if is_detailed(size) and sheet < sheets - 1:
            cast_shadow(layer, polygon, blur=0.012, offset=(0.006, 0.008), opacity=0.5)
        paint(layer, polygon, SHEETS[sheet])


def mark_layer(resolution, size):
    layer = blank(resolution)

    if is_detailed(size):
        shadow = blank(resolution)
        for polygon in fan_polygons(sheet_count(size)):
            cast_shadow(shadow, polygon, blur=0.026, offset=(0.012, 0.018), opacity=0.42)
        layer.alpha_composite(shadow)

    fan = blank(resolution)
    draw_fan(fan, size)
    layer.alpha_composite(fan)
    return layer


def background_layer(resolution, mask=None):
    layer = blank(resolution)
    tile = gradient(resolution, BACKGROUND, BACKGROUND_DIRECTION).convert("RGBA")
    if mask is None:
        layer.paste(tile, (0, 0))
    else:
        layer.paste(tile, (0, 0), mask)
    return layer


def rim_light(layer, polygon, width, opacity):
    resolution = layer.size[0]
    outline = Image.new("L", (resolution, resolution), 0)
    ImageDraw.Draw(outline).line(
        to_pixels(polygon + polygon[:1], resolution), fill=round(255 * opacity), width=max(1, round(width * resolution))
    )
    layer.paste(Image.new("RGB", (resolution, resolution), (255, 255, 255)), (0, 0), outline)


# --------------------------------------------------------------------------
# Variants
# --------------------------------------------------------------------------


MACOS_TILE = 824 / 1024  # macOS icons leave room around the tile for the system shadow


def render(variant, size):
    resolution = size * supersample(size)
    detailed = is_detailed(size)

    if variant == "square":
        layer = background_layer(resolution)
        layer.alpha_composite(mark_layer(resolution, size))
    elif variant == "rounded":
        outline = squircle_polygon()
        layer = background_layer(resolution, mask_of(outline, resolution))
        layer.alpha_composite(mark_layer(resolution, size))
        if detailed:
            rim_light(layer, outline, width=0.0025, opacity=0.16)
    elif variant == "circle":
        circle = Image.new("L", (resolution, resolution), 0)
        ImageDraw.Draw(circle).ellipse((0, 0, resolution - 1, resolution - 1), fill=255)
        layer = background_layer(resolution, circle)
        layer.alpha_composite(mark_layer(resolution, size))
    elif variant == "macos":
        outline = squircle_polygon(inset=(1 - MACOS_TILE) / 2)
        layer = blank(resolution)
        if detailed:
            cast_shadow(layer, outline, blur=0.018, offset=(0.0, 0.014), opacity=0.35)
        layer.alpha_composite(background_layer(resolution, mask_of(outline, resolution)))
        layer.alpha_composite(scaled_into(mark_layer(resolution, size), MACOS_TILE))
        if detailed:
            rim_light(layer, outline, width=0.0025, opacity=0.16)
    elif variant == "mark":
        layer = mark_layer(resolution, size)
    elif variant == "mark-tinted":
        layer = desaturate(mark_layer(resolution, size))
    elif variant == "adaptive-background":
        layer = background_layer(resolution)
    elif variant == "adaptive-foreground":
        layer = scaled_into(mark_layer(resolution, size), ADAPTIVE_MARK_SCALE)
    elif variant == "adaptive-monochrome":
        layer = scaled_into(monochrome_layer(resolution, size), ADAPTIVE_MARK_SCALE)
    else:
        raise ValueError(f"unknown variant: {variant}")

    return layer.resize((size, size), Image.Resampling.LANCZOS)


# On the 108dp adaptive layer the mark spans 48dp: the same proportion of the
# 72dp visible area as on the other platforms, and well inside the 66dp keyline.
ADAPTIVE_MARK_SCALE = (48 / 108) / MARK_BOX


def scaled_into(layer, factor):
    """Shrinks a full-tile layer around the tile centre."""
    resolution = layer.size[0]
    inner = max(1, round(resolution * factor))
    offset = (resolution - inner) // 2
    result = blank(resolution)
    result.alpha_composite(layer.resize((inner, inner), Image.Resampling.LANCZOS), (offset, offset))
    return result


def monochrome_layer(resolution, size):
    """Flat mark for Android themed icons.

    Themed icons collapse to a single colour, so the sheets are cut apart with a
    gap instead of relying on the tonal steps that separate them elsewhere.
    """
    gap = max(1.0, 0.004 * resolution)
    alpha = Image.new("L", (resolution, resolution), 0)
    for polygon in fan_polygons(sheet_count(size)):
        mask = mask_of(polygon, resolution)
        alpha = ImageChops.subtract(alpha, dilate(mask, gap))
        alpha = ImageChops.lighter(alpha, mask)
    white = Image.new("RGBA", (resolution, resolution), (255, 255, 255, 0))
    white.putalpha(alpha)
    return white


# --------------------------------------------------------------------------
# Packaging
# --------------------------------------------------------------------------

ANDROID_RES = ROOT / "androidApp/src/main/res"
ANDROID_DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
IOS_APPICON = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
DESKTOP_ICONS = ROOT / "desktopApp/src/main/resources/icons"

ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)
# iconutil's iconset mapping: OSType -> pixel size.
ICNS_ENTRIES = (
    ("icp4", 16),
    ("ic11", 32),
    ("icp5", 32),
    ("ic12", 64),
    ("ic07", 128),
    ("ic13", 256),
    ("ic08", 256),
    ("ic14", 512),
    ("ic09", 512),
    ("ic10", 1024),
)


def save(image, path, opaque=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    if opaque:
        flattened = Image.new("RGB", image.size, (0, 0, 0))
        flattened.paste(image, (0, 0), image)
        image = flattened
    image.save(path, format="PNG", optimize=True)
    print(f"  {path.relative_to(ROOT)}")


def write_ico(path, sizes):
    images = [render("rounded", size) for size in sizes]
    path.parent.mkdir(parents=True, exist_ok=True)
    images[-1].save(path, format="ICO", sizes=[(size, size) for size in sizes], append_images=images[:-1])
    print(f"  {path.relative_to(ROOT)}")


def write_icns(path):
    import io

    chunks = []
    cache = {}
    for ostype, size in ICNS_ENTRIES:
        if size not in cache:
            buffer = io.BytesIO()
            render("macos", size).save(buffer, format="PNG", optimize=True)
            cache[size] = buffer.getvalue()
        payload = cache[size]
        chunks.append(ostype.encode("ascii") + struct.pack(">I", len(payload) + 8) + payload)

    body = b"".join(chunks)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)
    print(f"  {path.relative_to(ROOT)}")


def write_svg(path):
    def point(value):
        return round(value * 1024, 2)

    outline = " ".join(
        f"{'M' if index == 0 else 'L'}{point(x)},{point(y)}"
        for index, (x, y) in enumerate(squircle_polygon(steps=360))
    )

    def stop_markup(stops):
        return "".join(
            f'<stop offset="{offset}" stop-color="#{red:02X}{green:02X}{blue:02X}"/>'
            for offset, (red, green, blue) in stops
        )

    def gradient_markup(name, stops, direction):
        length = math.hypot(*direction)
        reach = 512 * GRADIENT_LENGTH
        arm = (direction[0] / length * reach, direction[1] / length * reach)
        return (
            f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" '
            f'x1="{512 - arm[0]:.1f}" y1="{512 - arm[1]:.1f}" x2="{512 + arm[0]:.1f}" y2="{512 + arm[1]:.1f}">'
            f"{stop_markup(stops)}</linearGradient>"
        )

    defs = [gradient_markup("tile", BACKGROUND, BACKGROUND_DIRECTION)]
    defs += [gradient_markup(f"sheet{index}", stops, LIGHT_DIRECTION) for index, stops in enumerate(SHEETS)]

    fit = fan_fit(len(FAN))
    paths = []
    for sheet in reversed(range(len(FAN))):
        spine, lead1, lead2, tip, trail1, trail2 = (fit(control) for control in leaf_controls(*FAN[sheet]))
        data = (
            f"M{point(spine[0])},{point(spine[1])}"
            f" C{point(lead1[0])},{point(lead1[1])} {point(lead2[0])},{point(lead2[1])} {point(tip[0])},{point(tip[1])}"
            f" C{point(trail1[0])},{point(trail1[1])} {point(trail2[0])},{point(trail2[1])}"
            f" {point(spine[0])},{point(spine[1])} Z"
        )
        paths.append(f'<path d="{data}" fill="url(#sheet{sheet})"/>')

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">'
        f"<defs>{''.join(defs)}</defs>"
        f'<path d="{outline} Z" fill="url(#tile)"/>'
        f"{''.join(paths)}"
        "</svg>\n",
        encoding="utf-8",
    )
    print(f"  {path.relative_to(ROOT)}")


def build_android():
    print("Android")
    for density, factor in ANDROID_DENSITIES.items():
        launcher = round(48 * factor)
        layer = round(108 * factor)
        folder = ANDROID_RES / f"mipmap-{density}"
        save(render("rounded", launcher), folder / "ic_launcher.png")
        save(render("circle", launcher), folder / "ic_launcher_round.png")
        save(render("adaptive-background", layer), folder / "ic_launcher_background.png", opaque=True)
        save(render("adaptive-foreground", layer), folder / "ic_launcher_foreground.png")
        save(render("adaptive-monochrome", layer), folder / "ic_launcher_monochrome.png")


def build_ios():
    print("iOS")
    save(render("square", 1024), IOS_APPICON / "app-icon-1024.png", opaque=True)
    save(render("mark", 1024), IOS_APPICON / "app-icon-1024-dark.png")
    save(render("mark-tinted", 1024), IOS_APPICON / "app-icon-1024-tinted.png")


def build_desktop():
    print("Desktop")
    save(render("rounded", 512), DESKTOP_ICONS / "lukia.png")
    write_ico(DESKTOP_ICONS / "lukia.ico", ICO_SIZES)
    write_icns(DESKTOP_ICONS / "lukia.icns")


def build_source():
    print("Source")
    write_svg(ROOT / "icons/lukia-icon.svg")
    save(render("rounded", 1024), ROOT / "icons/lukia-icon-1024.png")


if __name__ == "__main__":
    build_source()
    build_android()
    build_ios()
    build_desktop()
