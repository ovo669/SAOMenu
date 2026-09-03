# -*- coding: utf-8 -*-
"""生成 SAO Menu 模组的 GUI 贴图。

样式取自 SAO_Utils_2.2 博客主题的 SAO_Menu.styl:
  - 圆形按钮: 半透明白 rgba(255,255,255,0.5) + 3px 白边, 悬停变橙 #eda60c
  - 菜单项小图标: 深色圆 rgb(77,72,73) + 白色符号
  - 面板箭头: #f9f9f9 实心三角
所有贴图 4x 超采样后缩小, 保证圆边平滑。
"""
import math
import os

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main",
                   "resources", "assets", "saomenu", "textures", "gui")
SS = 4  # supersample

WHITE_T = (255, 255, 255, 128)   # rgba(255,255,255,0.5)
ORANGE = (237, 166, 12, 255)     # #eda60c
DARK = (60, 60, 61, 230)         # rgba(60,60,61,0.9) 图标色
PANEL = (249, 249, 249, 235)     # #f9f9f9 面板
ITEM_DARK = (77, 72, 73, 255)    # 菜单项小图标底色


def canvas(size):
    return Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))


def save(img, size, name):
    img = img.resize((size, size), Image.LANCZOS)
    path = os.path.join(OUT, name)
    img.save(path)
    print("wrote", name)


def circle_button(size, color, ring=True):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    c = size * SS // 2
    r = size * SS // 2 - 1
    d.ellipse([c - r, c - r, c + r, c + r], fill=color)
    if ring:
        rw = int(2 * SS)
        d.ellipse([c - r, c - r, c + r, c + r], outline=color, width=max(2, rw))
    return img


def person(d, cx, cy, h, color):
    """画一个人形: 头 + 圆肩身体。h 为总高。"""
    head_r = h * 0.30
    d.ellipse([cx - head_r, cy - h * 0.5, cx + head_r, cy - h * 0.5 + head_r * 2], fill=color)
    body_w = h * 0.92
    body_h = h * 0.62
    d.ellipse([cx - body_w / 2, cy + h * 0.02, cx + body_w / 2, cy + h * 0.02 + body_h], fill=color)


def gear(d, cx, cy, r_out, r_in, teeth, color):
    pts = []
    step = 360.0 / teeth
    tooth_half = step * 0.32
    root_half = step * 0.5 - tooth_half
    for i in range(teeth):
        base = i * step
        for ang, rr in ((base - tooth_half, r_in), (base - tooth_half, r_out),
                        (base + tooth_half, r_out), (base + tooth_half, r_in),
                        (base + tooth_half + root_half, r_in)):
            a = math.radians(ang)
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=color)


def icon_person(size, color, companion=False, small=False):
    """主按钮图标: profile=单人, party=双人(一前一后), friends=并肩两人。"""
    img = canvas(size)
    d = ImageDraw.Draw(img)
    if companion:
        if small:  # friends: 并肩
            person(d, size * SS * 0.36, size * SS * 0.5, size * SS * 0.5, color)
            person(d, size * SS * 0.66, size * SS * 0.55, size * SS * 0.42, color)
        else:  # party: 后方的人先画
            person(d, size * SS * 0.68, size * SS * 0.55, size * SS * 0.42, color)
            person(d, size * SS * 0.42, size * SS * 0.5, size * SS * 0.52, color)
    else:
        person(d, size * SS * 0.5, size * SS * 0.48, size * SS * 0.58, color)
    return img


def icon_gear(size, color):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    c = size * SS / 2
    gear(d, c, c, c * 0.72, c * 0.52, 8, color)
    # 中心孔: 用透明擦除
    hole = c * 0.24
    mask = Image.new("L", img.size, 0)
    dm = ImageDraw.Draw(mask)
    dm.ellipse([c - hole, c - hole, c + hole, c + hole], fill=255)
    img.paste((0, 0, 0, 0), (0, 0), mask)
    return img


def item_icon(size, glyph):
    """菜单项小图标: 深色圆底 + 白色符号。glyph: sword/armor/ring/bag/person"""
    img = canvas(size)
    d = ImageDraw.Draw(img)
    c = size * SS / 2
    r = size * SS * 0.46
    d.ellipse([c - r, c - r, c + r, c + r], fill=ITEM_DARK)
    w = (255, 255, 255, 255)
    lw = int(1.6 * SS)
    if glyph == "person":
        person(d, c, c * 1.06, size * SS * 0.42, w)
    elif glyph == "sword":
        d.line([c - r * 0.45, c + r * 0.45, c + r * 0.5, c - r * 0.5], fill=w, width=lw * 2)
        d.line([c - r * 0.62, c + r * 0.15, c - r * 0.15, c + r * 0.62], fill=w, width=lw)
        d.line([c - r * 0.75, c + r * 0.75, c - r * 0.55, c + r * 0.55], fill=w, width=lw * 2)
    elif glyph == "armor":
        # 胸甲: 带领口的盾形
        top = c - r * 0.55
        bot = c + r * 0.6
        half = r * 0.55
        d.polygon([(c - half, top), (c + half, top), (c + half, c + r * 0.1),
                   (c, bot), (c - half, c + r * 0.1)], fill=w)
        notch = Image.new("L", img.size, 0)
        dn = ImageDraw.Draw(notch)
        dn.ellipse([c - half * 0.55, top - half * 0.2, c + half * 0.55, top + half * 0.8], fill=255)
        img.paste((0, 0, 0, 0), (0, 0), notch)
    elif glyph == "ring":
        d.ellipse([c - r * 0.42, c - r * 0.42 + r * 0.1, c + r * 0.42, c + r * 0.42 + r * 0.1],
                  outline=w, width=lw * 2)
        d.polygon([(c - r * 0.2, c - r * 0.32), (c + r * 0.2, c - r * 0.32),
                   (c + r * 0.12, c - r * 0.62), (c - r * 0.12, c - r * 0.62)], fill=w)
    elif glyph == "gear":
        gear(d, c, c, r * 0.75, r * 0.55, 8, w)
    elif glyph == "bag":
        top = c - r * 0.3
        d.rounded_rectangle([c - r * 0.55, top, c + r * 0.55, c + r * 0.6],
                            radius=3 * SS, fill=w)
        d.arc([c - r * 0.3, top - r * 0.45, c + r * 0.3, top + r * 0.25],
              180, 360, fill=w, width=lw)
    return img


def arrow_right(size):
    """面板右侧指向按钮的三角 (#f9f9f9)。宽:高 = 25:20 (styl)。"""
    w, h = size * SS, int(size * SS * 0.8)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(0, 0), (w, h // 2), (0, h)], fill=PANEL)
    return img, w // SS, h // SS


def silhouette(size_w, size_h):
    """玩家卡全身剪影(深灰), 参照 SAO Utils 卡片人形。"""
    img = Image.new("RGBA", (size_w * SS, size_h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = (DARK[0], DARK[1], DARK[2], 255)
    w, h = size_w * SS, size_h * SS
    head_r = h * 0.16
    hx, hy = w / 2, h * 0.16
    d.ellipse([hx - head_r, hy - head_r, hx + head_r, hy + head_r], fill=c)
    # 躯干
    sh = hy + head_r * 1.25
    tw = w * 0.34
    bw = w * 0.42
    d.polygon([(hx - tw, sh), (hx + tw, sh), (hx + bw, h * 0.62), (hx - bw, h * 0.62)], fill=c)
    # 手臂(微外张)
    d.polygon([(hx - tw, sh + 2), (hx - tw * 0.3, sh + 2), (hx - bw * 1.35, h * 0.60), (hx - bw * 0.95, h * 0.63)], fill=c)
    d.polygon([(hx + tw, sh + 2), (hx + tw * 0.3, sh + 2), (hx + bw * 1.35, h * 0.60), (hx + bw * 0.95, h * 0.63)], fill=c)
    # 双腿
    d.polygon([(hx - bw * 0.9, h * 0.60), (hx - bw * 0.1, h * 0.60), (hx - bw * 0.55, h * 0.97), (hx - bw * 1.15, h * 0.97)], fill=c)
    d.polygon([(hx + bw * 0.1, h * 0.60), (hx + bw * 0.9, h * 0.60), (hx + bw * 1.15, h * 0.97), (hx + bw * 0.55, h * 0.97)], fill=c)
    return img


def arrow_left(size):
    """菜单项左侧指向按钮的三角 ◀。"""
    w = int(size * SS * 0.8)
    h = size * SS
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(w, 0), (0, h // 2), (w, h)], fill=PANEL)
    return img, w // SS, h // SS


def connector_ring(size):
    """菜单项与按钮之间的小深色圆环。"""
    img = canvas(size)
    d = ImageDraw.Draw(img)
    c = size * SS / 2
    r = size * SS * 0.42
    d.ellipse([c - r, c - r, c + r, c + r], outline=(51, 51, 51, 255), width=max(2, SS))
    return img


def hud_plate():
    """SAO 血条板底图:深灰切角板 + 左侧"+"徽标 + 右下数字子板。"""
    w, h = 320, 80
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    body = (44, 45, 47, 238)
    cut = int(h * 0.24)
    d.polygon([(cut, 0), (w, 0), (w, h - cut), (w - cut, h), (0, h), (0, cut)], fill=body)
    d.line([(cut + 1, 1), (w - 1, 1)], fill=(96, 97, 99, 210))
    d.line([(1, cut + 1), (cut + 1, 1)], fill=(96, 97, 99, 150))
    # "+"徽标
    bx0, by0 = int(w * 0.025), int(h * 0.16)
    bx1, by1 = int(w * 0.125), int(h * 0.54)
    d.rectangle([bx0, by0, bx1, by1], fill=(62, 63, 65, 255))
    cx, cy = (bx0 + bx1) // 2, (by0 + by1) // 2
    r = int(h * 0.085)
    d.line([(cx - r, cy), (cx + r, cy)], fill=(240, 240, 240, 255), width=4)
    d.line([(cx, cy - r), (cx, cy + r)], fill=(240, 240, 240, 255), width=4)
    # 右下数字子板(带小切角)
    tx0, ty0 = int(w * 0.42), int(h * 0.56)
    tx1, ty1 = int(w * 0.985), int(h * 0.93)
    d.polygon([(tx0, ty0), (tx1, ty0), (tx1, ty1), (tx0 + cut // 3, ty1), (tx0, ty1 - cut // 3)],
              fill=(30, 31, 33, 228))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)

    # 主按钮圆底: 半透明白 / 橙色
    save(circle_button(64, WHITE_T), 64, "btn_circle.png")
    save(circle_button(64, ORANGE, ring=False), 64, "btn_circle_hover.png")

    # 底部圆环小点
    save(circle_button(32, WHITE_T, ring=False), 32, "dot.png")
    save(circle_button(32, ORANGE, ring=False), 32, "dot_hover.png")

    # 主按钮图标: 深色(常态) + 白色(悬停)
    for name, companion, small in (("profile", False, False), ("party", True, False),
                                   ("friends", True, True)):
        save(icon_person(32, DARK, companion, small), 32, f"icon_{name}_dark.png")
        save(icon_person(32, (255, 255, 255, 255), companion, small), 32, f"icon_{name}_white.png")
    save(icon_gear(32, DARK), 32, "icon_settings_dark.png")
    save(icon_gear(32, (255, 255, 255, 255)), 32, "icon_settings_white.png")

    # 菜单项小图标
    for name, glyph in (("status", "person"), ("weapon", "sword"), ("armor", "armor"),
                        ("ring", "ring"), ("bag", "bag"), ("config", "gear")):
        save(item_icon(24, glyph), 24, f"item_{name}.png")

    # SAO 血条板
    hud_plate().save(os.path.join(OUT, "hud_plate.png"))
    print("wrote hud_plate.png")

    # 玩家卡全身剪影(非正方形,保留 2:3 比例)
    sil = silhouette(64, 96).resize((64, 96), Image.LANCZOS)
    sil.save(os.path.join(OUT, "card_silhouette.png"))
    print("wrote card_silhouette.png 64x96")

    # 菜单项左箭头 ◀ 与连接环
    img2, w2, h2 = arrow_left(16)
    img2.resize((w2, h2), Image.LANCZOS).save(os.path.join(OUT, "arrow_left.png"))
    print("wrote arrow_left.png")
    save(connector_ring(12), 12, "ring.png")

    # 面板三角箭头
    img, w, h = arrow_right(24)
    img = img.resize((w, h), Image.LANCZOS)
    img.save(os.path.join(OUT, "arrow_right.png"))
    print("wrote arrow_right.png")


if __name__ == "__main__":
    main()
