"""Recolours the owner's art from imperial purple and gold to black and vibrant gold (1.0.71).

Owner request, 2026-10-02: every icon and image keeps its drawing exactly; only the purple becomes black and the gold
becomes the app's own gold (`Brand.Gold` in ui/Theme.kt, #F2BE22). Each pixel is split by hue into a gold part and a
purple part: the gold part is moved onto the app's gold (same brightness pattern), the purple part becomes a neutral
near-black of the same luminance, and edge pixels between the two are blended by how gold they are.

The transform is per pixel, so it was applied in place to the sources and to every resized copy. Run it once only:
running it again on already recoloured files changes nothing visible but is not needed.
    python branding/recolour_black_gold.py <files...>
"""
import colorsys
import sys

from PIL import Image

TARGET = (0xF2, 0xBE, 0x22)  # Brand.Gold
T_H, T_S, T_V = colorsys.rgb_to_hsv(*(c / 255 for c in TARGET))
ART_H, ART_S, ART_V = 37 / 360, 0.60, 0.82  # the art's median gold


def gold_weight(h: float, s: float, v: float) -> float:
    """1 for gold hues, 0 for purple, ramping across the magenta and red edge hues between them."""
    deg = h * 360
    if s < 0.08:
        return 1.0 if v > 0.5 else 0.0
    if deg <= 75:
        return 1.0
    if 250 <= deg <= 300:
        return 0.0
    if deg > 300:
        return (deg - 300) / 60  # 300° (purple) → 360° (red-gold edge)
    return max(0.0, (250 - deg) / 175)  # greens and blues barely occur


def recolour(rgb):
    r, g, b = (c / 255 for c in rgb)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    w = gold_weight(h, s, v)
    # Gold part: hue moved onto the app's gold, saturation and brightness scaled so the art's median gold lands on it.
    # Edge hues (the red and magenta halo where gold meets purple) are clamped into the gold range first.
    deg = h * 360
    deg = min(60.0, max(20.0, deg - 360 if deg > 180 else deg))
    nh = (T_H * 360 + (deg - ART_H * 360) * 0.8) % 360 / 360
    ns = min(1.0, s + (T_S - ART_S))
    nv = min(1.0, v * T_V / ART_V)
    gr, gg, gb = colorsys.hsv_to_rgb(nh, ns, nv)
    # Purple part: a neutral black of the same luminance.
    lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
    k = min(1.0, lum * 1.15)
    out = (w * gr + (1 - w) * k, w * gg + (1 - w) * k, w * gb + (1 - w) * k)
    return tuple(max(0, min(255, round(c * 255))) for c in out)


def main(paths):
    for path in paths:
        im = Image.open(path)
        mode = im.mode
        rgb = im.convert("RGB")
        cache = {}
        data = [cache[p] if p in cache else cache.setdefault(p, recolour(p)) for p in rgb.getdata()]
        rgb.putdata(data)
        out = rgb if mode == "RGB" else rgb.convert(mode)
        if path.lower().endswith(".jpg"):
            out.save(path, quality=95)
        else:
            out.save(path, optimize=True)
        print("recoloured", path)


if __name__ == "__main__":
    main(sys.argv[1:])
