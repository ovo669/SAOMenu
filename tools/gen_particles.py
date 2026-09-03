# -*- coding: utf-8 -*-
"""生成 SAO 死亡碎裂特效的粒子贴图。

Minecraft 粒子图集按「一个 PNG = 一个 sprite」收录,因此这里输出独立文件,
由 particles/sao_shard.json 与 particles/sao_glow.json 列出:

  sao_shard_0.png  细长三角碎片(动漫里数量最多的形态)
  sao_shard_1.png  宽三角碎片
  sao_shard_2.png  窄条状碎片(近似侧视薄片)
  sao_glow.png     柔和径向光晕(叠加混合的爆散闪光)

碎片配色参照动漫:半透明宝蓝内部 + 青白高光边缘 + 顶点白热点。
贴图不带方向,旋转由粒子的 roll 完成。
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main",
                   "resources", "assets", "saomenu", "textures", "particle")

CELL = 32
SS = 8  # supersample

CORE = (46, 111, 232)        # 内部宝蓝
EDGE = (198, 232, 255)       # 边缘青白高光
GLOW = (150, 205, 255)       # 光晕色


def shard(points, edge_width=1.0):
    """按归一化多边形顶点画一枚碎片:蓝色实心 + 细亮边 + 顶点微高光。

    边宽刻意做细:碎片在游戏里只有几厘米大,粗白边会让它看成冰块而非玻璃碴。
    """
    img = Image.new("RGBA", (CELL * SS, CELL * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pts = [(x * CELL * SS, y * CELL * SS) for x, y in points]

    d.polygon(pts, fill=CORE + (170,))
    d.line(pts + [pts[0]], fill=EDGE + (205,), width=int(edge_width * SS), joint="curve")
    for x, y in pts:
        r = 0.7 * SS
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, 165))

    img = img.resize((CELL, CELL), Image.LANCZOS)
    return Image.alpha_composite(img.filter(ImageFilter.GaussianBlur(0.7)), img)


def glow():
    """柔和径向光晕:中心白、外圈蓝。"""
    img = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    px = img.load()
    c = (CELL - 1) / 2.0
    for y in range(CELL):
        for x in range(CELL):
            dist = math.hypot(x - c, y - c) / c
            if dist >= 1.0:
                continue
            f = (1.0 - dist) ** 2.2
            px[x, y] = (
                int(GLOW[0] + (255 - GLOW[0]) * f),
                int(GLOW[1] + (255 - GLOW[1]) * f),
                int(GLOW[2] + (255 - GLOW[2]) * f),
                int(255 * f),
            )
    return img


SHARDS = [
    ("sao_shard_0.png", [(0.50, 0.06), (0.72, 0.92), (0.34, 0.70)], 1.0),
    ("sao_shard_1.png", [(0.12, 0.24), (0.90, 0.42), (0.44, 0.94)], 1.0),
    ("sao_shard_2.png", [(0.20, 0.10), (0.40, 0.14), (0.82, 0.88), (0.60, 0.92)], 0.8),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, pts, ew in SHARDS:
        path = os.path.join(OUT, name)
        shard(pts, ew).save(path)
        print("wrote", os.path.normpath(path))
    path = os.path.join(OUT, "sao_glow.png")
    glow().save(path)
    print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
