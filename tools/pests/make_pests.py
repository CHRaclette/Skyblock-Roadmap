"""Generates the pest head textures (64x32, Minecraft head box layout: 8x8x8 at u=0,v=0)."""
import random, sys
from PIL import Image

OUT = sys.argv[1]

def hexc(h): return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (255,)
def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

# name: (kind, body, accent, eye)
PESTS = {
    "beetle":  ("bug",    "2E5E4E", "8BD3C0", "E8463C"),
    "mite":    ("bug",    "8A2B22", "D9644E", "1A1A1A"),
    "cricket": ("bug",    "4E8A2A", "A6D65A", "1A1A1A"),
    "locust":  ("bug",    "8C7A2E", "D9C25A", "3A2A10"),
    "mantis":  ("bug",    "5FBF3A", "C6F27A", "FFE04A"),
    "fly":     ("fly",    "3C4048", "8A93A6", "D8262E"),
    "mosquito":("fly",    "5A4A3A", "A08A6A", "D8262E"),
    "moth":    ("fly",    "B8A98A", "E8DCC0", "2A2A2A"),
    "dragonfly":("fly",   "2A62C8", "7FD0FF", "46E07A"),
    "firefly": ("fly",    "3A3020", "FFE65A", "FFF6A8"),
    "rat":     ("rodent", "6E6460", "B5A9A2", "E0263A"),
    "mouse":   ("rodent", "9C7B5A", "D8BC98", "1A1A1A"),
    "worm":    ("worm",   "D9837E", "F2B3AA", "3A1A1A"),
    "slug":    ("worm",   "B9C24A", "E6EE8A", "2A2A10"),
}

# Head box faces in a 64x32 texture: (u, v) of each 8x8 face
FACES = {"top": (8, 0), "bottom": (16, 0), "right": (0, 8), "front": (8, 8), "left": (16, 8), "back": (24, 8)}

def make(name, kind, body, accent, eye):
    rnd = random.Random(name)
    body, accent, eye = hexc(body), hexc(accent), hexc(eye)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    def put(face, x, y, c):
        u, v = FACES[face]
        px[u + x, v + y] = c
    # body with noise on every face
    for f in FACES:
        for y in range(8):
            for x in range(8):
                put(f, x, y, shade(body, 0.85 + rnd.random() * 0.3))
    if kind == "bug":
        # shell: centre seam + shiny spots on top/back/sides
        for f in ("top", "back", "left", "right"):
            for y in range(8):
                put(f, 3 if f in ("top", "back") else 7, y, shade(body, 0.55))
            for _ in range(5):
                put(f, rnd.randrange(8), rnd.randrange(8), shade(accent, 0.9 + rnd.random() * 0.2))
        # face: big eyes, mandibles
        for (x, y) in ((1, 2), (2, 2), (1, 3), (2, 3), (5, 2), (6, 2), (5, 3), (6, 3)):
            put("front", x, y, eye)
        put("front", 1, 2, shade(eye, 1.5)); put("front", 5, 2, shade(eye, 1.5))
        for (x, y) in ((2, 6), (5, 6), (3, 7), (4, 7)):
            put("front", x, y, shade(body, 0.4))
        # antennae on top
        for i in range(4):
            put("top", 2 - i // 2, 7 - i, shade(accent, 0.7)); put("top", 5 + i // 2, 7 - i, shade(accent, 0.7))
    elif kind == "fly":
        # huge compound eyes wrapping round the sides
        for (x, y) in ((0, 1), (1, 1), (2, 1), (0, 2), (1, 2), (2, 2), (0, 3), (1, 3), (2, 3), (1, 4),
                       (5, 1), (6, 1), (7, 1), (5, 2), (6, 2), (7, 2), (5, 3), (6, 3), (7, 3), (6, 4)):
            put("front", x, y, shade(eye, 0.8 + rnd.random() * 0.4))
        for y in range(1, 5):
            for x in range(5, 8):
                put("right", x, y, shade(eye, 0.75 + rnd.random() * 0.3))
            for x in range(0, 3):
                put("left", x, y, shade(eye, 0.75 + rnd.random() * 0.3))
        # proboscis
        for y in range(5, 8):
            put("front", 3, y, shade(accent, 0.6)); put("front", 4, y, shade(accent, 0.5))
        # fuzzy striped top
        for y in range(8):
            for x in range(8):
                if y % 3 == 0:
                    put("top", x, y, shade(accent, 0.8 + rnd.random() * 0.3))
        if name == "firefly":
            for f in ("back", "bottom"):
                for y in range(8):
                    for x in range(8):
                        put(f, x, y, shade(accent, 0.85 + rnd.random() * 0.3))
    elif kind == "rodent":
        # snout, pink nose, whiskers, eyes, ears on top
        for (x, y) in ((2, 5), (3, 5), (4, 5), (5, 5), (2, 6), (3, 6), (4, 6), (5, 6)):
            put("front", x, y, shade(accent, 0.95))
        put("front", 3, 5, hexc("F28BA8")); put("front", 4, 5, hexc("F28BA8"))
        for (x, y) in ((1, 2), (6, 2)):
            put("front", x, y, eye); put("front", x, y + 1, shade(eye, 0.6))
        for (x, y) in ((0, 5), (0, 6), (7, 5), (7, 6)):
            put("front", x, y, hexc("EDEDED"))
        for (x, y) in ((0, 0), (1, 0), (0, 1), (6, 0), (7, 0), (7, 1)):
            put("top", x, y, hexc("F2A7B8"))
    elif kind == "worm":
        # ringed segments, tiny eyes, open mouth
        for f in FACES:
            for y in range(8):
                if y % 2 == 1:
                    for x in range(8):
                        put(f, x, y, shade(accent, 0.8 + rnd.random() * 0.2))
        put("front", 2, 2, eye); put("front", 5, 2, eye)
        for (x, y) in ((3, 5), (4, 5), (3, 6), (4, 6)):
            put("front", x, y, hexc("4A1A22"))
        if name == "slug":
            for (x, y) in ((2, 0), (2, 1), (5, 0), (5, 1)):
                put("top", x, y, shade(body, 0.6))
    img.save(f"{OUT}/{name}.png")

for n, (k, b, a, e) in PESTS.items():
    make(n, k, b, a, e)
print("made", len(PESTS))
