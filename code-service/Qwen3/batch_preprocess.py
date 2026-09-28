import os
import time
from pathlib import Path
from PIL import Image

from config import Config
from preprocessor import SmartPreprocessor


def is_image_file(path: Path) -> bool:
    return path.suffix.lower() in Config.ALLOWED_EXTENSIONS


def main():
    root = Path(__file__).resolve().parent
    source_dir = root / "测试图片"
    output_dir = root / "测试图片_processed"
    output_dir.mkdir(exist_ok=True)

    if not source_dir.exists() or not source_dir.is_dir():
        raise FileNotFoundError(f"Test image directory not found: {source_dir}")

    image_paths = sorted([p for p in source_dir.iterdir() if p.is_file() and is_image_file(p)])
    if not image_paths:
        print(f"No images found in {source_dir}. Supported extensions: {sorted(Config.ALLOWED_EXTENSIONS)}")
        return

    print(f"Found {len(image_paths)} images in {source_dir}")

    total_time = 0.0
    summary = []

    for img_path in image_paths:
        start = time.time()
        try:
            img = Image.open(img_path).convert("RGB")
            processed = SmartPreprocessor.process_image(img)
            elapsed = time.time() - start
            total_time += elapsed
            out_path = output_dir / img_path.name
            processed.save(out_path)
            summary.append((img_path.name, img.size, processed.size, elapsed))
            print(f"Processed {img_path.name}: {img.size} -> {processed.size} in {elapsed:.3f}s")
        except Exception as err:
            print(f"Failed {img_path.name}: {err}")

    if summary:
        avg_time = total_time / len(summary)
        print("\nSummary:")
        print(f"  images processed: {len(summary)}")
        print(f"  total time: {total_time:.3f}s")
        print(f"  avg time/image: {avg_time:.3f}s")
        print(f"  output directory: {output_dir}")


if __name__ == "__main__":
    main()
