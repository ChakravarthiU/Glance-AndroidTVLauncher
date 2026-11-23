# Banners (Lottie)

The four banner designs from Requirements.md section 6 (Diwali, Simple, Birthday, Christmas), with the scripts that
made them. The app ships copies in `app/src/main/assets/banners/<design>.json`; copy them again after rebuilding.
`all-banners-frame.png` shows one frame of each. Run from this folder.

```sh
# 1. Render the still art (SVG in diwali-static.html) to a transparent PNG, then WebP
npm install playwright lottie-web@5.12.2
node art.js                                   # writes diwali-art.png
python3 -c "from PIL import Image; Image.open('diwali-art.png').save('diwali-art.webp', 'WEBP', quality=85, method=6)"

# The other designs' still art is one page, banner-art.html?design=simple|birthday|christmas:
node art-designs.js                           # writes simple-art.png, birthday-art.png, christmas-art.png
python3 -c "from PIL import Image; [Image.open(f'{d}-art.png').save(f'{d}-art.webp','WEBP',quality=85,method=6) for d in ('simple','birthday','christmas')]"

# 2. Build the Lottie file: design, art, output (optional custom text as the last two arguments)
python3 make_banner.py diwali diwali-art.webp diwali-banner.json "Happy Diwali" "WISHING YOU LIGHT AND JOY"
python3 make_banner.py birthday birthday-art.webp birthday-banner.json
cp diwali-banner.json ../../app/src/main/assets/banners/diwali.json   # and the same for each design

# 3. Preview: render the 90 frames with lottie-web, then encode an MP4
mkdir -p frames
node frames.js diwali-banner.json frames/ 90 ["Line 1"] ["Line 2"]
ffmpeg -framerate 30 -i frames/%03d.png -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p preview.mp4
```

`make_banner.py` uses only the Python standard library; the art step needs Pillow and Playwright (Chromium).
