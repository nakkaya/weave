"""Post deploy hook."""
import shutil
from pathlib import Path


def on_post_build(config, **kwargs):
    """Post deploy hook."""
    site_dir = Path(config['site_dir'])
    docs_dir = Path(config['docs_dir']).parent

    src = docs_dir / 'googlefd0478405f199051.html'
    if src.exists():
        shutil.copy(src, site_dir / 'googlefd0478405f199051.html')
