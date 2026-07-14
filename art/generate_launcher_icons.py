"""Generate Android adaptive-icon layers from the RIDEMAN neon master.

The master is emissive art composited on a flat #0A0A0A field, so we can recover a
true alpha channel by un-compositing: given out = fg*a + bg*(1-a), solve for fg and a.
Alpha is driven by brightness above the background; the glow falls off into a soft ramp.
"""
import sys, os
from PIL import Image
import numpy as np

MASTER = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "rideman-master-1024.png")
OUTDIR = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
BG = np.array([10, 10, 10], dtype=float)

# Adaptive icon: 108dp canvas, only the centre 72dp is guaranteed visible.
CANVAS_DP = 108
BIKE_W_DP = 66          # fits inside the 72dp safe circle at the bike's 2.63 aspect
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
SUPER = 4               # render at xxxhdpi, downsample to the rest

src = np.array(Image.open(MASTER).convert("RGB")).astype(float)
bike = src[352:581, 211:813]                      # bike only; wordmark band dropped

lum = bike.max(axis=2)
alpha = np.clip((lum - BG.max()) / (255.0 - BG.max()), 0, 1)

# Un-composite: fg = (out - bg*(1-a)) / a
with np.errstate(invalid="ignore", divide="ignore"):
    fg = (bike - BG * (1 - alpha)[..., None]) / np.where(alpha > 0, alpha, 1)[..., None]
fg = np.clip(np.nan_to_num(fg), 0, 255)

rgba = np.dstack([fg, alpha * 255]).astype(np.uint8)
bike_img = Image.fromarray(rgba, "RGBA")

# Monochrome layer: same shape, flat white, alpha boosted so strokes read solid
# once the system tints them (a raw glow ramp washes out on a themed home screen).
mono_a = (np.clip(alpha ** 0.55, 0, 1) * 255).astype(np.uint8)
mono = Image.fromarray(
    np.dstack([np.full(alpha.shape, 255, np.uint8)] * 3 + [mono_a]), "RGBA"
)

def layer(art, name):
    canvas_px = CANVAS_DP * SUPER
    bike_px = int(round(BIKE_W_DP * SUPER))
    scaled = art.resize((bike_px, int(round(bike_px * art.height / art.width))), Image.LANCZOS)
    base = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    base.paste(scaled, ((canvas_px - scaled.width) // 2, (canvas_px - scaled.height) // 2), scaled)
    for dpi, mult in DENSITIES.items():
        d = os.path.join(OUTDIR, f"drawable-{dpi}")
        os.makedirs(d, exist_ok=True)
        px = int(CANVAS_DP * mult)
        base.resize((px, px), Image.LANCZOS).save(os.path.join(d, f"{name}.png"), optimize=True)
        print(f"  drawable-{dpi}/{name}.png  {px}x{px}")

layer(bike_img, "ic_launcher_foreground")
layer(mono, "ic_launcher_monochrome")

# Play Store / Strava flat square: full master, no masking involved.
print("\nforeground bike: %.0fdp wide, %.1fdp tall on a %ddp canvas"
      % (BIKE_W_DP, BIKE_W_DP * bike_img.height / bike_img.width, CANVAS_DP))
