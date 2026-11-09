"""Optional asset regeneration (Python + Pillow); the seed uses the bundled PNGs."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

DEST = Path(__file__).parent / "evidence"
DEST.mkdir(exist_ok=True)
FONT = Path("C:/Windows/Fonts/segoeui.ttf")


def font(size):
    return ImageFont.truetype(str(FONT), size) if FONT.exists() else ImageFont.load_default(size=size)


for kind, title, note in [
    ("hvac", "Air conditioner inspection", "Check filter, drain hose and cooling performance"),
    ("projector", "Projector signal inspection", "Check HDMI cable, input source and cooling fan"),
    ("water", "Water leak inspection", "Check pipe joint, valve seal and floor drain"),
    ("safety", "Campus safety inspection", "Isolate the area and check the affected equipment"),
    ("result", "Maintenance completion record", "Repair completed - verify normal operation"),
]:
    im = Image.new("RGB", (1000, 600), "#f1f5f9")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 1000, 112), fill="#102e40")
    d.text((38, 21), "NEXORA / CAMPUS MAINTENANCE", font=font(29), fill="white")
    d.text((40, 68), "DEMO - SIMULATED DIAGRAM - NOT AN INCIDENT PHOTO", font=font(18), fill="#99ded3")
    d.rounded_rectangle((35, 140, 965, 480), radius=20, fill="white", outline="#cbd5e1", width=2)
    d.text((62, 162), title, font=font(30), fill="#102e40")
    if kind == "projector":
        d.rounded_rectangle((105, 260, 400, 395), radius=18, fill="#cbd5e1", outline="#334155", width=4)
        d.ellipse((295, 280, 380, 365), fill="#38bdf8", outline="#334155", width=4)
        d.line((400, 324, 540, 324, 540, 390, 740, 390), fill="#e68435", width=9)
        d.rectangle((690, 250, 870, 370), fill="#e2e8f0", outline="#334155", width=4)
        d.text((707, 292), "HDMI ?", font=font(27), fill="#b45309")
    elif kind == "water":
        d.line((150, 250, 150, 315, 535, 315, 535, 385, 820, 385), fill="#64748b", width=35)
        d.rectangle((420, 280, 465, 348), fill="#cbd5e1", outline="#334155", width=4)
        for x, y in [(440, 370), (470, 402), (420, 420)]:
            d.ellipse((x, y, x + 16, y + 24), fill="#0ea5e9")
        d.ellipse((382, 448, 540, 463), fill="#bae6fd")
    elif kind == "hvac":
        d.rounded_rectangle((175, 250, 825, 380), radius=20, fill="#e2e8f0", outline="#334155", width=4)
        for y in [320, 335, 350]:
            d.line((220, y, 780, y), fill="#64748b", width=4)
        for x in [340, 500, 660]:
            d.line((x, 392, x, 445), fill="#38bdf8", width=5)
            d.polygon([(x - 10, 430), (x + 10, 430), (x, 450)], fill="#38bdf8")
    elif kind == "safety":
        d.polygon([(300, 435), (500, 230), (700, 435)], fill="#fbbf24", outline="#92400e", width=5)
        d.text((484, 296), "!", font=font(84), fill="#102e40")
    else:
        d.ellipse((155, 245, 335, 425), fill="#d1fae5", outline="#059669", width=4)
        d.line((200, 330, 235, 365, 295, 295), fill="#059669", width=12)
        for n, label in enumerate(["Repair recorded", "Operation checked", "Ready for requester review"]):
            d.text((400, 260 + n * 53), label, font=font(25), fill="#14532d")
    d.text((40, 506), note, font=font(23), fill="#334155")
    d.text((40, 551), "Illustration for the local demo dataset. All people and incidents are fictional.", font=font(18), fill="#64748b")
    im.save(DEST / f"{kind}.png")
