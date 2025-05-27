#!/usr/bin/env python3
"""Pack Heroicons into a single SVG sprite file."""

import os
import re
import subprocess
import tempfile
import shutil
from pathlib import Path

# Configuration
REPO_URL = "https://github.com/tailwindlabs/heroicons.git"
OUTPUT_DIR = "../public/"
SPRITE_NAME = "heroicons-sprite.svg"


def extract_svg_content(svg_file):
    """Extract inner content from SVG file."""
    with open(svg_file, 'r') as f:
        content = f.read()

    # Extract only the inner content, removing outer svg tags and declarations
    content = re.sub(r'<\?xml.*?\?>', '', content)
    content = re.sub(r'<svg[^>]*>', '', content)
    content = re.sub(r'</svg>', '', content)

    # Replace any fill attribute with fill="currentColor"
    content = re.sub(r'fill="[^"]+"', 'fill="currentColor"', content)

    return content.strip()


def main():
    """Clone the Heroicons repository and create the SVG sprite file."""
    temp_dir = tempfile.mkdtemp()
    try:
        subprocess.run(
            ['git', 'clone', '--depth', '1', REPO_URL, temp_dir],
            check=True, stdout=subprocess.PIPE
        )

        os.makedirs(OUTPUT_DIR, exist_ok=True)

        solid_dir = os.path.join(temp_dir, 'src', '24', 'solid')
        outline_dir = os.path.join(temp_dir, 'src', '24', 'outline')
        output_file = os.path.join(OUTPUT_DIR, SPRITE_NAME)

        with open(output_file, 'w') as f:
            f.write('<?xml version="1.0" encoding="UTF-8"?>\n')
            f.write('<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="0" height="0" style="display:none;">\n') # noqa

        print("Processing solid icons...")
        for svg_file in Path(solid_dir).glob('*.svg'):
            icon_name = svg_file.stem
            print(f"  Adding: solid-{icon_name}")

            content = extract_svg_content(svg_file)

            with open(output_file, 'a') as f:
                f.write(f'  <symbol id="solid-{icon_name}" viewBox="0 0 24 24" fill="currentColor">\n')
                f.write(f'    {content}\n')
                f.write('  </symbol>\n')

        print("Processing outline icons...")
        for svg_file in Path(outline_dir).glob('*.svg'):
            icon_name = svg_file.stem
            print(f"  Adding: outline-{icon_name}")

            content = extract_svg_content(svg_file)

            with open(output_file, 'a') as f:
                f.write(f'  <symbol id="outline-{icon_name}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">\n') # noqa
                f.write(f'    {content}\n')
                f.write('  </symbol>\n')

        with open(output_file, 'a') as f:
            f.write('</svg>')

        print(f"Sprite generated at {output_file}")
    finally:
        shutil.rmtree(temp_dir)


if __name__ == "__main__":
    main()
