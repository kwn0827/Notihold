#!/usr/bin/env python3
import os
import subprocess
from PIL import Image

# アイコンサイズのリスト（Androidのアイコンサイズ）
icon_sizes = {
    'mipmap-hdpi': 72,
    'mipmap-mdpi': 48, 
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

# プロジェクトパス
project_path = "C:/Users/Keigo/AndroidStudioProjects/MyApplication"
icon_path = os.path.join(project_path, "app/icon.png")

# 各サイズでアイコンを生成
for folder, size in icon_sizes.items():
    output_dir = os.path.join(project_path, f"app/src/main/res/{folder}")
    
    # 既存のアイコンを削除
    for filename in ['ic_launcher.webp', 'ic_launcher_round.webp']:
        file_path = os.path.join(output_dir, filename)
        if os.path.exists(file_path):
            os.remove(file_path)
    
    # 新しいアイコンを生成
    try:
        with Image.open(icon_path) as img:
            # 正方形にトリミング
            width, height = img.size
            min_size = min(width, height)
            left = (width - min_size) // 2
            top = (height - min_size) // 2
            right = (width + min_size) // 2
            bottom = (height + min_size) // 2
            img = img.crop((left, top, right, bottom))
            
            # リサイズ
            img_resized = img.resize((size, size), Image.Resampling.LANCZOS)
            
            # 通常アイコンを保存
            normal_path = os.path.join(output_dir, 'ic_launcher.webp')
            img_resized.save(normal_path, 'WEBP', quality=90)
            
            # 丸いアイコンを保存（円形マスクを適用）
            from PIL import ImageDraw
            mask = Image.new('L', (size, size), 0)
            draw = ImageDraw.Draw(mask)
            draw.ellipse((0, 0, size, size), fill=255)
            
            # アルファチャンネルを持つ画像を作成
            round_img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
            round_img.paste(img_resized, (0, 0))
            round_img.putalpha(mask)
            
            round_path = os.path.join(output_dir, 'ic_launcher_round.webp')
            round_img.save(round_path, 'WEBP', quality=90)
            
            print(f"Generated {folder}: {size}x{size}")
            
    except Exception as e:
        print(f"Error generating {folder}: {e}")

print("Icon generation completed!")
