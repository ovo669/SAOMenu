#!/usr/bin/env python3
"""把设置背景视频(设置背景.mp4)转成 Minecraft GUI 帧动画贴图集。

流程:ffmpeg 抽帧(15fps, 480x270) → 拼成网格 atlas → 自适应调色板量化 →
输出单张 PNG 到 common 资源目录,并写 meta JSON 供 Java 侧读取帧数/网格。
用法:
    python tools/gen_settings_bg.py <视频路径>
(需先 ffmpeg -vf "fps=15,scale=480:270:flags=lanczos" 抽帧到临时目录,脚本内自动调用)
"""
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

FRAME_W = 480
FRAME_H = 270
FPS = 15
COLS = 16
COLORS = 96

REPO = Path(__file__).resolve().parent.parent
OUT_ATLAS = REPO / "common/src/main/resources/assets/saomenu/textures/gui/settings_bg.png"
OUT_META = REPO / "tools/settings_bg_meta.json"


def main() -> None:
    if len(sys.argv) < 2:
        print("usage: gen_settings_bg.py <video.mp4>")
        sys.exit(1)
    video = Path(sys.argv[1])
    from PIL import Image

    tmp = Path(tempfile.mkdtemp(prefix="saobg_"))
    try:
        subprocess.run(
            ["ffmpeg", "-v", "error", "-i", str(video),
             "-vf", f"fps={FPS},scale={FRAME_W}:{FRAME_H}:flags=lanczos",
             "-start_number", "0", str(tmp / "f%04d.png")],
            check=True)
        frames = sorted(tmp.glob("f*.png"))
        if not frames:
            raise SystemExit("no frames extracted")
        rows = (len(frames) + COLS - 1) // COLS
        atlas = Image.new("RGB", (COLS * FRAME_W, rows * FRAME_H), (0, 0, 0))
        for i, p in enumerate(frames):
            img = Image.open(p).convert("RGB")
            atlas.paste(img, ((i % COLS) * FRAME_W, (i // COLS) * FRAME_H))
        # 全 atlas 共享一套自适应调色板 + 抖动,暗色动画画面压缩率最高
        q = atlas.quantize(colors=COLORS, dither=Image.Dither.FLOYDSTEINBERG)
        OUT_ATLAS.parent.mkdir(parents=True, exist_ok=True)
        q.save(OUT_ATLAS, optimize=True)
        meta = {
            "frame_w": FRAME_W, "frame_h": FRAME_H, "fps": FPS,
            "cols": COLS, "rows": rows, "frames": len(frames),
            "atlas_w": atlas.width, "atlas_h": atlas.height,
        }
        OUT_META.write_text(json.dumps(meta, indent=2), encoding="utf-8")
        print(json.dumps(meta))
        print(f"atlas png size: {OUT_ATLAS.stat().st_size / 1024:.0f} KB")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
