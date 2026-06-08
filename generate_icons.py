from PIL import Image, ImageDraw
import os

img_path = r'c:\Users\matya\.gemini\antigravity\scratch\PurkynovaCanteen\logo.png'
base_res = r'c:\Users\matya\.gemini\antigravity\scratch\PurkynovaCanteen\app\src\main\res'

sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

try:
    img = Image.open(img_path).convert('RGBA')
    
    # Process legacy & round
    for density, size in sizes.items():
        folder = os.path.join(base_res, f'mipmap-{density}')
        os.makedirs(folder, exist_ok=True)
        
        # ic_launcher.png
        launcher = img.resize((size, size), Image.Resampling.LANCZOS)
        launcher.save(os.path.join(folder, 'ic_launcher.png'))
        
        # ic_launcher_round.png
        mask = Image.new('L', (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        round_img = launcher.copy()
        round_img.putalpha(mask)
        round_img.save(os.path.join(folder, 'ic_launcher_round.png'))
        
    print("Icons generated successfully.")
except Exception as e:
    print(f"Error: {e}")
