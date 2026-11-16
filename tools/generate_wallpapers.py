from pathlib import Path
import random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = Path("app/src/main/assets/wallpapers")
W, H = 2560, 3840
PALETTES = [
    ((8,8,28),(86,17,82),(255,45,91)), ((5,14,35),(32,39,111),(165,54,255)),
    ((24,7,25),(125,31,67),(255,112,58)), ((4,18,28),(12,74,104),(58,215,228)),
    ((16,8,33),(67,25,109),(255,58,128)), ((8,15,27),(60,43,75),(255,72,73)),
    ((5,9,31),(44,21,89),(130,81,255)), ((14,10,27),(93,32,56),(255,148,67)),
    ((4,11,23),(26,61,94),(69,170,255)), ((13,7,26),(93,18,90),(255,55,113)),
    ((5,17,26),(29,75,81),(112,255,210)), ((18,8,31),(78,25,91),(236,71,255)),
]
TITLES = ["NIGHT DRIVE","VIOLET DISTRICT","SUNSET STRIP","COASTLINE","NEON HEIGHTS","AFTER HOURS","CITY LIGHTS","DESERT RUN","DOWNTOWN","REDLINE","HARBOR","VORTEX RISE"]

def get_font(size, bold=False):
    path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()

def make_wallpaper(i):
    rng = random.Random(8173 + i * 917)
    top, mid, accent = PALETTES[i % len(PALETTES)]
    img = Image.new("RGB", (W, H))
    pix = img.load()
    low = tuple(int(c * .36) for c in accent)
    for y in range(H):
        t = y / H
        if t < .66:
            f = t / .66
            color = tuple(int(top[k]*(1-f) + mid[k]*f) for k in range(3))
        else:
            f = (t-.66)/.34
            color = tuple(int(mid[k]*(1-f) + low[k]*f) for k in range(3))
        for x in range(W):
            pix[x,y] = color
    d = ImageDraw.Draw(img, "RGBA")
    cx = rng.randint(int(W*.25), int(W*.75)); cy = int(H*.48)
    for radius, alpha in [(760,12),(560,18),(390,24)]:
        d.ellipse((cx-radius,cy-radius,cx+radius,cy+radius), fill=(*accent,alpha))
    d.ellipse((cx-300,cy-300,cx+300,cy+300), outline=(*accent,110), width=18)
    d.ellipse((cx-255,cy-255,cx+255,cy+255), outline=(255,255,255,35), width=4)
    horizon = int(H*.61)
    x = -30
    while x < W:
        bw = rng.randint(115,360); bh = rng.randint(260,1120); by = horizon-bh
        shade = rng.randint(8,30)
        d.rectangle((x,by,x+bw,horizon+150), fill=(shade,shade//2+4,shade+22,255), outline=(*accent,55), width=3)
        if rng.random() < .45:
            d.rectangle((x+bw*.32,by-70,x+bw*.68,by), fill=(13,12,31,255))
        cols = max(2,int(bw//38)); rows = max(2,int(bh//72))
        for row in range(rows):
            for col in range(cols):
                if rng.random() < .66:
                    wx = int(x+16+col*(bw-28)/cols); wy = int(by+22+row*(bh-36)/rows)
                    wc = rng.choice([(255,70,105,180),(169,100,255,170),(72,205,255,165),(255,190,105,160)])
                    d.rounded_rectangle((wx,wy,wx+12,wy+25), radius=3, fill=wc)
        x += bw + rng.randint(12,38)
    road_top = int(H*.68)
    d.polygon([(int(W*.44),road_top),(int(W*.56),road_top),(W+300,H),(-300,H)], fill=(5,6,16,255))
    d.line([(int(W*.44),road_top),(-200,H)], fill=(*accent,180), width=12)
    d.line([(int(W*.56),road_top),(W+200,H)], fill=(175,90,255,150), width=12)
    for n in range(11):
        t = n/10; y = int(road_top+(H-road_top)*(t**1.2)); half = int(10+25*t)
        d.rounded_rectangle((W//2-half,y,W//2+half,y+int(18+55*t)), radius=7, fill=(255,255,255,180))
    for n in range(7):
        t = rng.uniform(.25,.95); y = int(road_top+(H-road_top)*t)
        scale = .25+t*.8; x = rng.randint(int(W*.2),int(W*.8)); cw=int(100*scale); ch=int(48*scale)
        d.rounded_rectangle((x-cw,y-ch,x+cw,y+ch), radius=max(2,int(15*scale)),
            fill=rng.choice([(22,22,42,255),(50,12,38,255),(18,35,54,255)]),
            outline=(*accent,180), width=max(2,int(5*scale)))
        d.ellipse((x-cw+8,y-7*scale,x-cw+24*scale,y+8*scale), fill=(255,240,191,230))
        d.ellipse((x+cw-24*scale,y-7*scale,x+cw-8,y+8*scale), fill=(255,65,83,230))
    for j in range(5):
        yy = int(H*(.17+j*.065)); left = rng.randint(50,500)
        d.arc((left,yy,left+W-rng.randint(100,500),yy+int(H*.20)),185,345,
              fill=(*accent,rng.randint(45,105)),width=rng.randint(3,10))
    title_font = get_font(150,True); small_font = get_font(52,True)
    title = "VORTEX"; box = d.textbbox((0,0),title,font=title_font,stroke_width=3)
    d.text(((W-(box[2]-box[0]))//2,int(H*.105)),title,font=title_font,
           fill=(255,255,255,245),stroke_width=5,stroke_fill=(*accent,220))
    subtitle = "ROLEPLAY  /  " + TITLES[i % len(TITLES)]
    sb = d.textbbox((0,0),subtitle,font=small_font)
    d.text(((W-(sb[2]-sb[0]))//2,int(H*.16)),subtitle,font=small_font,fill=(245,225,255,220))
    grain = Image.effect_noise((W,H),55).convert("RGB")
    img = Image.blend(img,grain,.075)
    vignette = Image.new("L",(W,H),0); vd = ImageDraw.Draw(vignette)
    vd.ellipse((-W*.25,-H*.1,W*1.25,H*1.13),fill=205)
    vignette = vignette.filter(ImageFilter.GaussianBlur(220))
    img = Image.composite(img,Image.new("RGB",(W,H),(0,0,0)),vignette)
    return img

OUT.mkdir(parents=True, exist_ok=True)
for old in OUT.glob("*.jpg"):
    old.unlink()
target = 36 * 1024 * 1024
total = 0
count = 0
while count < 16:
    image = make_wallpaper(count)
    path = OUT / f"vortex_{count+1:02d}.jpg"
    image.save(path,"JPEG",quality=96,optimize=True,progressive=True,subsampling=0)
    del image
    total += path.stat().st_size
    count += 1
    print(f"{path.name}: {path.stat().st_size:,} bytes; bundle: {total:,} bytes")
    if count >= 10 and total >= target:
        break
if total < target:
    raise SystemExit(f"Wallpaper pack is only {total} bytes; expected at least {target}")
print(f"Generated {count} offline wallpapers ({total:,} bytes total).")
