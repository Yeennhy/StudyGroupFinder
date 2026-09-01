"""Generate The Code Cup launcher icons from CodeCupColors.

Art is drawn in a 1000x1000 logical space at 4x supersample, then downsampled
with LANCZOS so every edge is antialiased.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

# --- brand palette (ui/theme/Color.kt) ---
PRIMARY = (0xF2, 0xB8, 0xC8)
ACCENT = (0xE9, 0x86, 0xA2)
HEADING = (0x54, 0x2D, 0x39)
WHITE = (0xFF, 0xFF, 0xFF)
CREAM = (0xFF, 0xF0, 0xF4)

SS = 4  # supersample factor
ART = 1000  # logical art space


def _d(img):
    return ImageDraw.Draw(img)


def gradient(size, top, bottom):
    """Vertical Primary -> Accent gradient, matching CodeCupColors.PrimaryGradient."""
    g = Image.new("RGB", (1, size))
    px = g.load()
    for y in range(size):
        t = y / max(1, size - 1)
        px[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return g.resize((size, size), Image.BICUBIC)


def draw_cup(img, ox, oy, scale, shadow=True):
    """Draw the cup group into img. Logical art space is 1000x1000 before scale."""
    d = _d(img)

    # The handle juts out to the right, so the cup's optical centre sits right of
    # the art centre. Nudge the whole group left to balance it in the safe zone.
    SHIFT_X = -20

    def P(x, y):
        return (ox + (x + SHIFT_X) * scale, oy + y * scale)

    def box(x1, y1, x2, y2):
        return [P(x1, y1), P(x2, y2)]

    # ---- steam: three wisps rising from the cup ----
    for sx, amp, top in ((390, 30, 96), (500, 34, 58), (610, 30, 96)):
        pts = []
        steps = 48
        for s in range(steps + 1):
            t = s / steps
            y = 268 - (268 - top) * t
            x = sx + math.sin(t * math.pi * 1.6) * amp
            pts.append(P(x, y))
        w = max(1, int(32 * scale))
        d.line(pts, fill=WHITE + (170,), width=w, joint="curve")
        for cap in (pts[0], pts[-1]):
            d.ellipse(
                [cap[0] - w / 2, cap[1] - w / 2, cap[0] + w / 2, cap[1] + w / 2],
                fill=WHITE + (170,),
            )

    # ---- saucer: sits flush under the cup, no floating gap ----
    d.rounded_rectangle(box(208, 782, 792, 846), radius=32 * scale, fill=WHITE)

    # ---- cup body: tapered, rounded at the bottom ----
    top_y, bot_y = 348, 790
    tl, tr = 288, 712  # top edge
    bl, br = 344, 656  # bottom edge
    r = 54.0

    # trapezoid stopping short of the bottom so corner circles can round it
    d.polygon(
        [P(tl, top_y), P(tr, top_y), P(br, bot_y - r), P(bl, bot_y - r)],
        fill=WHITE,
    )
    # rounded bottom corners + the strip between them
    d.ellipse(box(bl, bot_y - 2 * r, bl + 2 * r, bot_y), fill=WHITE)
    d.ellipse(box(br - 2 * r, bot_y - 2 * r, br, bot_y), fill=WHITE)
    d.rectangle(box(bl + r, bot_y - 2 * r, br - r, bot_y), fill=WHITE)

    # ---- handle: open ring clipped to the right of the cup ----
    # Keep the ring's top below the rim (top_y + coffee ellipse) or it pokes out
    # past the cup's top edge and leaves a notch beside the coffee surface.
    hcx, hcy, ro, ri = 700, 548, 118, 64
    d.ellipse(box(hcx - ro, hcy - ro, hcx + ro, hcy + ro), fill=WHITE)
    d.ellipse(box(hcx - ri, hcy - ri, hcx + ri, hcy + ri), fill=(0, 0, 0, 0))
    # re-fill the cup edge the ring's inner hole punched through
    d.polygon(
        [P(tl, top_y), P(tr, top_y), P(br, bot_y - r), P(bl, bot_y - r)],
        fill=WHITE,
    )

    # ---- coffee surface ----
    d.ellipse(box(tl + 18, top_y - 46, tr - 18, top_y + 46), fill=HEADING)
    d.ellipse(box(tl + 74, top_y - 22, tr - 74, top_y + 26), fill=(0x6E, 0x3F, 0x4D, 255))


def render_art(size, with_bg, bg_shape, content):
    """Render one icon layer at `size` px.

    bg_shape: None | 'square' | 'rounded' | 'circle'
    content:  fraction of `size` the cup group occupies
    """
    S = size * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    if with_bg:
        grad = gradient(S, PRIMARY, ACCENT).convert("RGBA")
        if bg_shape == "square":
            img.alpha_composite(grad)
        else:
            mask = Image.new("L", (S, S), 0)
            md = ImageDraw.Draw(mask)
            if bg_shape == "circle":
                md.ellipse([0, 0, S - 1, S - 1], fill=255)
            else:
                md.rounded_rectangle([0, 0, S - 1, S - 1], radius=S * 0.22, fill=255)
            img.paste(grad, (0, 0), mask)

    art = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    span = S * content
    scale = span / ART
    off = (S - span) / 2
    draw_cup(art, off, off + span * 0.02, scale)
    img.alpha_composite(art)

    return img.resize((size, size), Image.LANCZOS)


# density bucket -> (legacy icon px, adaptive layer px @108dp)
DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}

# Adaptive foreground must stay inside the 66dp-of-108dp safe zone, since the
# launcher masks and can parallax the layer. Legacy icons own the whole canvas.
FG_CONTENT = 0.75
LEGACY_CONTENT = 0.80


def write_resources(res_dir):
    written = []
    for bucket, (legacy_px, adaptive_px) in DENSITIES.items():
        mip = os.path.join(res_dir, f"mipmap-{bucket}")
        drw = os.path.join(res_dir, f"drawable-{bucket}")
        os.makedirs(mip, exist_ok=True)
        os.makedirs(drw, exist_ok=True)

        jobs = [
            (os.path.join(mip, "ic_launcher.webp"),
             render_art(legacy_px, True, "rounded", LEGACY_CONTENT)),
            (os.path.join(mip, "ic_launcher_round.webp"),
             render_art(legacy_px, True, "circle", LEGACY_CONTENT)),
            (os.path.join(drw, "ic_launcher_foreground.webp"),
             render_art(adaptive_px, False, None, FG_CONTENT)),
            (os.path.join(drw, "ic_launcher_background.webp"),
             gradient(adaptive_px, PRIMARY, ACCENT).convert("RGBA")),
        ]
        for path, im in jobs:
            im.save(path, "WEBP", quality=95, method=6)
            written.append((path, os.path.getsize(path)))
    return written


if __name__ == "__main__":
    mode = sys.argv[1]
    target = sys.argv[2]
    if mode == "preview":
        os.makedirs(target, exist_ok=True)
        render_art(512, True, "circle", 0.60).save(os.path.join(target, "preview_circle.png"))
        render_art(512, True, "rounded", 0.66).save(os.path.join(target, "preview_rounded.png"))
        render_art(512, True, "square", 0.60).save(os.path.join(target, "preview_square.png"))
        print("wrote previews to", target)
    else:
        rows = write_resources(target)
        total = sum(s for _, s in rows)
        for p, s in rows:
            print(f"{s/1024:8.1f} KB  {os.path.relpath(p, target)}")
        print(f"{total/1024:8.1f} KB  TOTAL ({len(rows)} files)")
