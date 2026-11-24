"""Offline preview of Atrium's generated artwork and home layout.

This mirrors Field.java and HomeLayout.java so composition can be judged with
the TV switched off. It is a preview, not the product: the real thing is the
Java. Numbers here are copied from the Java and must be kept in step.
"""
import colorsys, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

GEN_W, GEN_H = 512, 288
HORIZON, HORIZON_SOFT = 0.80, 0.06
LEAN = 0.34
AP_X, AP_Y, AP_RX, AP_RY = 0.78, 0.24, 0.40, 0.44
LEFT_FLOOR, LEFT_EDGE = 0.05, 0.56

FONTS = "atrium/app/src/main/assets/fonts/"
LIGHT = FONTS + "Jost-Light.ttf"
REG = FONTS + "Jost-Regular.ttf"
MED = FONTS + "Jost-Medium.ttf"

def i32(v):
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= (1 << 31) else v

def seed_of(key):
    h = 17
    for ch in key:
        h = i32(h * 31 + ord(ch))
    return h or 1

def hsv_to_rgb(h, s, v):
    h = h % 360.0
    c = v * s
    x = c * (1 - abs((h / 60.0) % 2 - 1))
    m = v - c
    r, g, b = [(c, x, 0), (x, c, 0), (0, c, x), (0, x, c), (x, 0, c), (c, 0, x)][int(h // 60) % 6]
    return (r + m, g + m, b + m)

def spec_for(hue, sat, intensity, key):
    glow = hsv_to_rgb(hue, min(sat, 0.44), 1.0)
    deep = hsv_to_rgb(hue, min(sat * 1.15, 0.76), 0.19)
    shadow = hsv_to_rgb(hue + 168.0, 0.55, 0.085)
    return dict(glow=glow, deep=deep, shadow=shadow, intensity=intensity, seed=seed_of(key))

def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)

def aperture(fx, fy):
    dx = (fx - AP_X) / AP_RX
    dy = (fy - AP_Y) / AP_RY
    d2 = dx * dx + dy * dy
    return np.where(d2 > 3.6, 0.0, np.exp(-2.15 * d2))

def reach(fx, fy):
    dx = (fx - 0.80) * 0.86
    dy = (fy - 0.16) * 0.70
    d = 1 - np.sqrt(dx * dx + dy * dy) * 1.30
    d = np.maximum(d, 0)
    return d * d

def build(spec):
    fy = (np.arange(GEN_H) / (GEN_H - 1))[:, None]
    fx = (np.arange(GEN_W) / (GEN_W - 1))[None, :]

    wall = 1.18 - 0.55 * fy
    floor_t = np.maximum(0.0, (fy - HORIZON) / (1 - HORIZON))
    floor = 0.34 + 0.30 * floor_t
    m = smooth((fy - (HORIZON - HORIZON_SOFT)) / (2 * HORIZON_SOFT))
    shell = wall + (floor - wall) * m

    mirror_y = np.where(fy > HORIZON, HORIZON - (fy - HORIZON) * 0.78, -1.0)
    mirror_fade = np.where(fy > HORIZON, (1 - floor_t) * 0.42, 0.0)

    lit = aperture(fx, fy) * 1.00
    lit = lit + np.where(mirror_y > 0, aperture(fx, np.maximum(mirror_y, 0)) * mirror_fade, 0.0)

    rnd = spec["seed"]
    beams = []
    for i in range(4):
        rnd = i32(rnd * 1103515245 + 12345); a = ((rnd & 0xFFFFFFFF) >> 8 & 0xFFFF) / 65535
        rnd = i32(rnd * 1103515245 + 12345); b = ((rnd & 0xFFFFFFFF) >> 8 & 0xFFFF) / 65535
        rnd = i32(rnd * 1103515245 + 12345); c = ((rnd & 0xFFFFFFFF) >> 8 & 0xFFFF) / 65535
        beams.append((0.36 + i * 0.20 + a * 0.11, 0.016 + b * 0.042, 0.42 + c * 0.58))

    u = fx - LEAN * fy
    beam = np.zeros((GEN_H, GEN_W))
    for bx, bw, bi in beams:
        away = np.abs(u - bx)
        d = away / bw
        core = np.where(d < 1, bi * smooth(1 - d), 0.0)
        hd = away / (bw * 3.4)
        halo = np.where(hd < 1, bi * 0.26 * smooth(1 - hd), 0.0)
        beam = beam + core + halo
    beam = np.minimum(beam, 1.4)
    lit = lit + beam * reach(fx, fy) * 0.66

    side = LEFT_FLOOR + (1 - LEFT_FLOOR) * smooth(fx / LEFT_EDGE)
    # The rows live in the bottom half, so the light is taken off it. The
    # artwork owns the top right, the content sits on near black underneath.
    damp = 1.0 - 0.78 * smooth((fy - 0.40) / 0.46)
    lit = np.minimum(lit * side * damp * spec["intensity"] * 1.45, 1.0)
    lit = lit * lit * (3 - 2 * lit)

    out = np.zeros((GEN_H, GEN_W, 3))
    for ch in range(3):
        base = (spec["shadow"][ch] + (spec["deep"][ch] - spec["shadow"][ch]) * side) * shell
        v = base + (spec["glow"][ch] - base) * lit
        bloom = np.where(lit > 0.82, (lit - 0.82) / 0.18 * 0.30, 0.0)
        v = v + (1 - v) * bloom
        out[:, :, ch] = v

    vx = (fx - 0.56) * 2
    vy = (fy - 0.48) * 2
    vd = np.sqrt(vx * vx * 0.74 + vy * vy)
    vig = np.clip((vd - 0.52) / 0.88, 0, 1)
    out *= (1 - vig * vig * 0.70)[:, :, None]

    return Image.fromarray(np.clip(out * 255, 0, 255).astype(np.uint8), "RGB")

def grain(img):
    rng = np.random.default_rng(7)
    a = np.abs(rng.normal(0, 1.4, img.size[::-1]))
    a = np.minimum(a, 4) * np.where(rng.random(img.size[::-1]) > 0.5, 1, -1)
    arr = np.asarray(img).astype(np.int16) + a[:, :, None].astype(np.int16)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")

# ---- layout, in dp against a 960x540 canvas, 2 physical pixels per dp ----
S = 2
def dp(v):
    return int(round(v * S))

class Light:
    """
    One of Palette.Light's presets: the colour and strength of the hour.

    An icon with no colour in it leaves the room this colour, which is why the
    hue and the saturation are carried here and not only the strength.
    """

    def __init__(self, hue, sat, intensity):
        self.hue, self.sat, self.intensity = hue, sat, intensity


# Dusk, key #FF9A6B. Night, key #6E96D4. Both from Palette.java.
DUSK = Light(19.0, 0.58, 0.48)
NIGHT = Light(216.5, 0.48, 0.30)

# styles.xml draws the window in this colour, under everything.
BASE_DEEP = (5, 6, 10)

PAPER = (245, 243, 239)
DIM = (245, 243, 239, 120)
MID = (245, 243, 239, 150)

ICONS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icons")

# AppCard.java
TILE_DP, WIDTH_DP, GAP_DP = 78, 104, 14
CORNER_RATIO, INSET_RATIO = 15 / 78, 14 / 78
FOCUSED_SCALE, RESTING_ALPHA = 1.09, 0.72


def f(path, sp):
    return ImageFont.truetype(path, int(round(sp * S)))


def track(d, xy, text, font, fill, spacing=0.0):
    """Letter spacing. Pillow has none, and the spaced small caps need it."""
    x, y = xy
    for ch in text:
        d.text((x, y), ch, font=font, fill=fill)
        x += d.textlength(ch, font=font) + spacing * font.size


def icon_for(pkg):
    """The app's real icon, pulled off the TV into design/icons."""
    path = os.path.join(ICONS, pkg + ".png")
    return Image.open(path).convert("RGBA") if os.path.exists(path) else None


def analyse(icon):
    """
    AccentExtractor.analyse, in Python.

    The room takes its colour from the icon's own pixels, so the study has to
    read the colour the same way the TV does or it is showing a colour the TV
    will never produce.
    """
    im = icon.resize((32, 32), Image.BILINEAR)
    w = h = 32
    buckets = 24
    weight = [0.0] * buckets
    hue_sum = [0.0] * buckets
    sat_sum = [0.0] * buckets
    edge_total = edge_clear = 0

    for y in range(h):
        for x in range(w):
            r, g, b, a = im.getpixel((x, y))
            if x < w // 8 or x >= w - w // 8 or y < h // 8 or y >= h - h // 8:
                edge_total += 1
                if a < 32:
                    edge_clear += 1
            if a < 128:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if ss < 0.22 or vv < 0.18:
                continue
            i = min(int(hh * buckets), buckets - 1)
            wt = ss * ss
            weight[i] += wt
            hue_sum[i] += hh * 360 * wt
            sat_sum[i] += ss * wt

    needs_plate = edge_total > 0 and edge_clear * 100 // edge_total > 55
    best = max(range(buckets), key=lambda i: weight[i])
    if weight[best] / (w * h) < 0.012:
        return None, None, needs_plate
    return hue_sum[best] / weight[best], min(1.0, sat_sum[best] / weight[best]), needs_plate


def compose_tile(icon, size, needs_plate):
    """
    AccentExtractor.compose, in Python.

    Every icon is clipped to the same rounded square, and a bare logo on a
    clear background gets a faint plate under it so it does not float.
    """
    corner = int(round(size * CORNER_RATIO))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], corner, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    if needs_plate:
        plate = Image.new("RGBA", (size, size), (255, 255, 255, 26))
        plate.putalpha(mask.point(lambda v: v * 26 // 255))
        out.alpha_composite(plate)
        inset = int(round(size * INSET_RATIO))
        out.alpha_composite(icon.resize((size - 2 * inset,) * 2, Image.LANCZOS), (inset, inset))
    else:
        clipped = icon.resize((size, size), Image.LANCZOS)
        a = clipped.getchannel("A").point(lambda v: v)
        clipped.putalpha(Image.eval(Image.merge("L", (a,)), lambda v: v).point(lambda v: v))
        clipped.putalpha(mask)
        out.alpha_composite(clipped)
    return out


def flat_tile(size, colour):
    """A stand-in for an app whose icon is a vector and cannot be read here."""
    corner = int(round(size * CORNER_RATIO))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(out).rounded_rectangle([0, 0, size - 1, size - 1], corner, fill=colour)
    return out


class App:
    """One app, with the colour its icon gives the room."""

    def __init__(self, pkg, label, fallback=(90, 90, 95)):
        self.pkg, self.label = pkg, label
        self.icon = icon_for(pkg)
        if self.icon is None:
            # No icon to read, so the room keeps the colour of the hour.
            self.hue, self.sat, self.plate, self.fallback = None, None, False, fallback
        else:
            self.hue, self.sat, self.plate = analyse(self.icon)
            self.fallback = None

    def art(self, size):
        if self.icon is None:
            return flat_tile(size, self.fallback)
        return compose_tile(self.icon, size, self.plate)


def paste_tile(img, app, left, foot, focused):
    """
    A tile sits on its foot. The focused one grows upwards out of the row, so
    the row does not shuffle sideways when focus moves along it.
    """
    size = dp(TILE_DP * (FOCUSED_SCALE if focused else 1))
    art = app.art(size)
    if not focused:
        art.putalpha(art.getchannel("A").point(lambda v: int(v * RESTING_ALPHA)))
    x = int(left + dp(WIDTH_DP) / 2 - size / 2)
    # Pasted with its own alpha as the mask. Drawing onto an RGB base is what
    # keeps the translucent panels and buttons translucent.
    img.paste(art, (x, int(foot - size)), art)
    return x, size


def rim(img, x, foot, size, colour):
    """The 2dp line in the app's own colour, the fourth cue that focus is here."""
    d = ImageDraw.Draw(img, "RGBA")
    pad = dp(3)
    d.rounded_rectangle([x - pad, foot - size - pad, x + size + pad, foot + pad],
                        int(round((size + 2 * pad) * CORNER_RATIO)), outline=colour, width=dp(2))


def scene(hue, sat, name, intensity):
    """The room, built and upscaled the way FieldView draws it."""
    spec = spec_for(hue, sat, intensity, name)
    return grain(build(spec).resize((960 * S, 540 * S), Image.BILINEAR))


def chrome(d, rail):
    """The clock and the rail. They are on every section, so they live here."""
    d.text((dp(30), dp(28)), "7:12", font=f(LIGHT, 34), fill=PAPER)
    w = d.textlength("7:12", font=f(LIGHT, 34))
    d.text((dp(30) + w + dp(6), dp(44)), "PM", font=f(LIGHT, 13), fill=DIM)
    track(d, (dp(30), dp(72)), "SUNDAY  ·  18 OCTOBER", f(MED, 11), DIM, 0.20)

    for i, (label, cur) in enumerate(rail):
        y = dp(116) + i * dp(56)
        if cur:
            d.rounded_rectangle([dp(28), y, dp(28 + 168), y + dp(50)], dp(13),
                                fill=(255, 255, 255, 20))
        d.text((dp(28 + 53), y + dp(14)), label, font=f(REG, 15),
               fill=PAPER if cur else MID)


def draw_home(apps, body, eyebrow, tools, out_path, light=DUSK):
    """The home screen, with the first app in the row focused."""
    focus = apps[0]
    hue = focus.hue if focus.hue is not None else light.hue
    sat = focus.sat if focus.sat is not None else light.sat
    accent = hsv_to_rgb_255(hue, min(sat, 0.62), 1.0)

    img = scene(hue, sat, focus.pkg, light.intensity)
    rail = [("Home", True), ("Apps", False), ("Settings", False)]
    d = ImageDraw.Draw(img, "RGBA")
    chrome(d, rail)

    x = dp(236)
    # The hero, measured the way Hero.java measures it. The actions go under
    # whatever it came to, so the name can change size without a collision.
    top = dp(92)
    eyebrow_font, title_font, body_font = f(MED, 12), f(LIGHT, 58), f(LIGHT, 14.5)
    eyebrow_base = eyebrow_font.getmetrics()[0]
    title_base = eyebrow_base + dp(16) + title_font.getmetrics()[0] * 0.78
    body_top = title_base + dp(20)
    hero_h = body_top + sum(body_font.getmetrics()) * 1.22

    rule_y = top + eyebrow_base - eyebrow_font.size * 0.32
    d.rounded_rectangle([x, rule_y - dp(1), x + dp(26), rule_y + dp(1)], 1, fill=accent)
    track(d, (x + dp(40), top), eyebrow.upper(), eyebrow_font, DIM, 0.28)
    d.text((x, top + title_base), focus.label, font=title_font, fill=PAPER, anchor="ls")
    d.text((x, top + body_top), body, font=body_font, fill=MID)

    py = int(top + hero_h + dp(16))
    pw = dp(118)
    d.rounded_rectangle([x, py, x + pw, py + dp(42)], dp(21),
                        fill=(255, 255, 255, 26), outline=(255, 255, 255, 92))
    d.text((x + dp(46), py + dp(11)), "Open", font=f(MED, 15), fill=PAPER)
    d.polygon([(x + dp(24), py + dp(14)), (x + dp(35), py + dp(21)),
               (x + dp(24), py + dp(28))], fill=PAPER)
    ox = x + pw + dp(14)
    d.rounded_rectangle([ox, py, ox + dp(132), py + dp(42)], dp(21),
                        fill=(255, 255, 255, 26), outline=(255, 255, 255, 92))
    d.text((ox + dp(30), py + dp(11)), "Options", font=f(MED, 15), fill=PAPER)

    track(d, (x, dp(300 - 14)), "YOUR APPS", f(MED, 12), DIM, 0.26)
    foot = dp(312 + TILE_DP)
    for i, app in enumerate(apps):
        left = x + i * dp(WIDTH_DP + GAP_DP)
        tx, size = paste_tile(img, app, left, foot, i == 0)
        if i == 0:
            rim(img, tx, foot, size, accent)
        d = ImageDraw.Draw(img, "RGBA")
        tw = d.textlength(app.label, font=f(REG, 12))
        d.text((left + dp(WIDTH_DP) / 2 - tw / 2, foot + dp(10)), app.label,
               font=f(REG, 12), fill=PAPER if i == 0 else MID)

    track(d, (x, dp(444 - 14)), "TOOLS", f(MED, 12), DIM, 0.26)
    n = len(tools)
    cw = (dp(664) - (n - 1) * dp(14)) / n
    for i, (title, sub, chue) in enumerate(tools):
        cx = x + i * (cw + dp(14))
        cy = dp(454)
        d.rounded_rectangle([cx, cy, cx + cw, cy + dp(56)], dp(12),
                            fill=hsv_to_rgb_255(chue, 0.45, 0.30))
        d.text((cx + dp(48), cy + dp(11)), title, font=f(REG, 14), fill=PAPER)
        d.text((cx + dp(48), cy + dp(31)), sub, font=f(LIGHT, 11), fill=DIM)

    img.save(out_path)
    print("wrote", out_path)


# The All apps section. The rail stays, the hero and the two rows are replaced
# by every app at once. AppsSection.java: 236dp in, 96dp down, 5 columns of
# 104dp with 14dp between them, rows 132dp apart.
APPS_COLS = 5


def draw_apps(apps, out_path, light=DUSK):
    focus = apps[0]
    hue = focus.hue if focus.hue is not None else light.hue
    sat = focus.sat if focus.sat is not None else light.sat
    accent = hsv_to_rgb_255(hue, min(sat, 0.62), 1.0)

    img = scene(hue, sat, focus.pkg, light.intensity)
    # The grid fills the screen, so the room is pushed back behind it. In the
    # app this is the field view's own alpha over the window colour.
    img = Image.blend(img, Image.new("RGB", img.size, BASE_DEEP), 0.5)
    d = ImageDraw.Draw(img, "RGBA")
    chrome(d, [("Home", False), ("Apps", True), ("Settings", False)])

    x = dp(236)
    track(d, (x, dp(96)), "ALL APPS", f(MED, 12), DIM, 0.26)
    top = dp(96 + 15 + 22)

    for i, app in enumerate(apps):
        col, row = i % APPS_COLS, i // APPS_COLS
        left = x + col * dp(WIDTH_DP + GAP_DP)
        foot = top + row * dp(132) + dp(TILE_DP)
        tx, size = paste_tile(img, app, left, foot, i == 0)
        if i == 0:
            rim(img, tx, foot, size, accent)
        d = ImageDraw.Draw(img, "RGBA")
        tw = d.textlength(app.label, font=f(REG, 12))
        d.text((left + dp(WIDTH_DP) / 2 - tw / 2, foot + dp(10)), app.label,
               font=f(REG, 12), fill=PAPER if i == 0 else MID)

    img.save(out_path)
    print("wrote", out_path)


def hsv_to_rgb_255(h, s, v):
    r, g, b = hsv_to_rgb(h, s, v)
    return (int(r * 255), int(g * 255), int(b * 255))


def library():
    """The apps on this TV, in the order the row shows them."""
    return [
        App("com.amazon.firetv.youtube", "YouTube"),
        App("io.github.jqssun.airplay", "AirPlay Server", (74, 96, 74)),
        App("com.peacock.peacockfiretv", "Peacock TV"),
        App("com.amazon.venezia", "Appstore"),
        App("com.esaba.downloader", "Downloader"),
        App("com.amazon.cloud9", "Silk Browser"),
        App("com.amazon.bueller.music", "Amazon Music"),
    ]


def front(apps, pkg):
    """Put one app first in the row, which is where focus starts."""
    chosen = [a for a in apps if a.pkg == pkg]
    return (chosen + [a for a in apps if a.pkg != pkg])[:6]


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "study.png"
    mode = sys.argv[2] if len(sys.argv) > 2 else "home"

    apps = library()
    tools = [("Network", "Wi-Fi and network", 215), ("System", "About this TV", 35),
             ("Fire TV", "Amazon home screen", 265), ("Atrium", "Light, apps, motion", 210)]

    if mode == "apps":
        draw_apps(apps, out)
    elif mode == "teal":
        # Silk Browser, whose icon is teal. The room is a different place.
        draw_home(front(apps, "com.amazon.cloud9"), "Opened yesterday",
                  "This evening", tools, out)
    elif mode == "plain":
        # Peacock's icon is black and white, so there is no colour to take.
        # The room keeps the light of the hour instead.
        draw_home(front(apps, "com.peacock.peacockfiretv"), "Opened 2 hours ago",
                  "This evening", tools, out)
    elif mode == "night":
        draw_home(apps[:6], "Opened yesterday", "Tonight", tools, out, light=NIGHT)
    else:
        draw_home(apps[:6], "You have not opened this from here yet",
                  "This evening", tools, out)
