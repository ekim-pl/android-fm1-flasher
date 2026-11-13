"""Regenerate store graphics and frame actual emulator screenshots (requires Pillow)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
META = ROOT / "fastlane/metadata/android"
SCALE = 2
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
CJK = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"


def font(size, bold=False, chinese=False):
    return ImageFont.truetype(CJK if chinese else BOLD if bold else FONT,
                             size * SCALE, index=2 if chinese else 0)


def graphic(locale):
    im = Image.new("RGB", (1024 * SCALE, 500 * SCALE))
    draw = ImageDraw.Draw(im)
    for y in range(500 * SCALE):
        t = y / (500 * SCALE)
        draw.line((0, y, 1024 * SCALE, y), fill=(int(21 + 7*t), int(51 + 13*t), int(37 + 10*t)))
    def line(points, fill, width=1):
        draw.line([(int(x*SCALE), int(y*SCALE)) for x,y in points], fill=fill, width=width*SCALE, joint="curve")
    def box(bounds, fill, radius=0, outline=None, width=1):
        draw.rounded_rectangle(tuple(int(v*SCALE) for v in bounds), radius*SCALE, fill=fill, outline=outline, width=width*SCALE)
    for x in range(0, 1024, 40): line([(x,0),(x,500)], (35,69,50))
    for y in range(0, 500, 40): line([(0,y),(1024,y)], (35,69,50))
    box((658,99,949,401),(15,32,23),22,(88,118,75),2)
    box((681,125,926,286),(6,21,13),12)
    line([(700,247),(700,181),(747,181),(747,247),(794,247),(794,181),(841,181),(841,247),(888,247),(888,181),(908,181)],(202,245,159),7)
    for x in (708,761,814,867,920):
        draw.ellipse(((x-13)*SCALE,322*SCALE,(x+13)*SCALE,348*SCALE),fill=(48,75,56),outline=(130,162,110),width=2*SCALE)
        line([(x,335),(x+7,328)],(202,245,159),2)
    line([(659,360),(620,360),(620,415),(556,415)],(185,239,128),4)
    box((530,404,556,426),(185,239,128),3)
    draw.text((72*SCALE,88*SCALE),"USB MIDI  /  OFFLINE",font=font(16,True),fill=(202,245,159))
    draw.text((68*SCALE,145*SCALE),"FM-1 Flasher",font=font(49,True),fill=(245,250,239))
    headlines={
        "en-US":("Your firmware library.","USB MIDI updates."),
        "pl-PL":("Biblioteka firmware.","Aktualizacje USB MIDI."),
        "zh-CN":("发现固件。","通过 USB MIDI 更新。"),
    }
    for i,text in enumerate(headlines[locale]):
        draw.text((72*SCALE,(235+i*53)*SCALE),text,font=font(33,chinese=locale=="zh-CN"),fill=(214,230,207))
    return im.resize((1024,500),Image.Resampling.LANCZOS)


def icon():
    im=Image.new("RGBA",(512*SCALE,512*SCALE),(16,20,18,255))
    draw=ImageDraw.Draw(im)
    points=[(7,30),(7,18),(14,18),(14,30),(21,30),(21,18),(28,18),(28,30),(35,30),(35,18),(41,18)]
    draw.line([(round(x*512*SCALE/48),round(y*512*SCALE/48)) for x,y in points],fill=(185,239,128,255),width=round(3*512*SCALE/48),joint="curve")
    return im.resize((512,512),Image.Resampling.LANCZOS)


for locale in ("pl-PL","en-US","zh-CN"):
    folder=META/locale/"images"
    folder.mkdir(parents=True,exist_ok=True)
    graphic(locale).save(folder/"featureGraphic.png",optimize=True)
    icon().save(folder/"icon.png",optimize=True)
    for raw in sorted((ROOT/"publication/screenshots/raw"/locale).glob("*.png")):
        shot=Image.open(raw).convert("RGB")
        # Remove only Android status/navigation bars, retain the entire app viewport.
        landscape = shot.width > shot.height
        shot=shot.crop((0,96,shot.width,shot.height-60))
        background=shot.getpixel((0,0))
        size = (2560,1440) if landscape else (1440,2560)
        shot.thumbnail(size,Image.Resampling.LANCZOS)
        canvas=Image.new("RGB",size,background)
        canvas.paste(shot,((size[0]-shot.width)//2,(size[1]-shot.height)//2))
        target=folder/"phoneScreenshots"/raw.name
        target.parent.mkdir(exist_ok=True)
        canvas.save(target,optimize=True)
print("Store graphics generated; screenshots framed at 1440x2560 or 2560x1440.")
