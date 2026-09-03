package com.sao.saomenu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.sao.saomenu.SAOMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.time.LocalTime;

/**
 * SAO 时钟小组件(复刻动画帧):白色圆表盘(时针/分针/红秒针实时走动)
 * 悬在两块半透明玻璃板左端,大玻璃板上以 SAO 方块数字显示时间。
 *
 * <p>几何按参考帧逐像素测量后等比缩放(帧内:整块高 222 / 表盘直径 148 /
 * 数字高 160 / 笔画与玻璃板白度按帧实测),表盘圆心正好压在小玻璃板左缘、
 * 圆盘向左凸出板外;大板宽度随数字位数自适应。</p>
 *
 * <p>跟随 {@code showClock} 配置常显(菜单内与 HUD 层都渲染);
 * 按住可拖动,位置(屏幕比例)持久化到配置。</p>
 */
public final class SAOClockPanel {

    private static final ResourceLocation TEX_FACE =
            new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/clock_face.png");
    private static final ResourceLocation TEX_DIGITS =
            new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/clock_digits.png");

    /** 组件 GUI 高度;缩放系数 = H / 222(参考帧整块高度)。 */
    private static final int H = 36;

    private static float getScaleFactor() {
        return (H / 222f) * SAOConfig.clockScale();
    }

    // ---- 参考帧坐标系(高 222)内的几何,全部按参考帧实测 ----
    private static final int REF_FACE_D = 148;      // 表盘直径
    private static final int REF_FACE_Y = 37;       // (222-148)/2,盘面垂直居中
    private static final int REF_SMALL_X = 74;      // 小玻璃板左缘 = 表盘圆心横坐标
    private static final int REF_SMALL_W = 131;
    private static final int REF_GAP = 14;          // 两块玻璃板之间的缝
    private static final int REF_BIG_X = REF_SMALL_X + REF_SMALL_W + REF_GAP;
    private static final int REF_PAD = 60;          // 大板左右内边距
    private static final int REF_DIGIT_H = 160;     // 数字字高
    private static final int REF_DIGIT_Y = 31;      // (222-160)/2
    private static final int REF_CELL = 68;         // 贴图集中单个字形格宽
    private static final int REF_ADV = 84;          // 数字步进(字形 68 + 间隙 16)
    private static final int REF_ATLAS_W = 748;     // 贴图集总宽(11 格 * 68)
    /** "HH:MM" 形态下的参考帧总宽(用于 GUI 命中框)。 */
    private static final int REF_TOTAL_W = REF_BIG_X + 2 * REF_PAD + 4 * REF_ADV + REF_CELL;

    /** 组件总宽(GUI 像素,用于命中/拖动/锚点)。 */
    private static int getScaledW() {
        return Math.round(REF_TOTAL_W * getScaleFactor());
    }

    /** 组件总高(GUI 像素,用于命中/拖动/锚点)。 */
    private static int getScaledH() {
        return Math.round(H * SAOConfig.clockScale());
    }

    /** 玻璃板:纯白 alpha≈0.35(参考帧实测透色比例)。 */
    private static final int PANEL_BG = 0x59FFFFFF;
    private static final int HAND_DARK = 0xFF3A3E42;
    private static final int HAND_RED = 0xFFD8434F;

    private static boolean dragging;
    private static float grabFx;
    private static float grabFy;
    private static boolean draggedSinceDown;

    private SAOClockPanel() {
    }

    public static int panelW() {
        return getScaledW();
    }

    public static int panelH() {
        return getScaledH();
    }

    private static int originX(int screenW) {
        int w = getScaledW();
        float fx = Mth.clamp(SAOConfig.clockPanelX(), 0f, 1f);
        return Math.max(2, Math.min(Math.round(fx * (screenW - w)), screenW - w - 2));
    }

    private static int originY(int screenH) {
        int h = getScaledH();
        float fy = Mth.clamp(SAOConfig.clockPanelY(), 0f, 1f);
        return Math.max(2, Math.min(Math.round(fy * (screenH - h)), screenH - h - 2));
    }

    private static MenuLayout.Rect rect(int screenW, int screenH) {
        return new MenuLayout.Rect(originX(screenW), originY(screenH), getScaledW(), getScaledH());
    }

    // ------------------------------------------------------------ 渲染

    public static void render(GuiGraphics g, Minecraft mc, int screenW, int screenH, float alpha) {
        if (!SAOConfig.showClock()) {
            return;
        }
        int x = originX(screenW);
        int y = originY(screenH);
        RenderSystem.enableBlend();
        // 整体淡入淡出走 shader 颜色,几何与配色按参考帧原样绘制
        RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float k = getScaleFactor();
        pose.scale(k, k, 1f);

        String text = timeText();
        int bigW = 2 * REF_PAD + (text.length() - 1) * REF_ADV + REF_CELL;

        // 两块半透明玻璃板:小板(表盘背后,从圆心向右)+ 大板(数字)
        g.fill(REF_SMALL_X, 0, REF_SMALL_X + REF_SMALL_W, 222, PANEL_BG);
        g.fill(REF_BIG_X, 0, REF_BIG_X + bigW, 222, PANEL_BG);

        // SAO 方块数字(0-9 + 冒号,贴图集逐字绘制)
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int idx = (c == ':') ? 10 : (c - '0');
            if (idx < 0 || idx > 10) {
                continue;
            }
            g.blit(TEX_DIGITS, REF_BIG_X + REF_PAD + i * REF_ADV, REF_DIGIT_Y,
                    REF_CELL, REF_DIGIT_H, idx * REF_CELL, 0, REF_CELL, REF_DIGIT_H,
                    REF_ATLAS_W, REF_DIGIT_H);
        }

        // 表盘:不透明白盘盖在玻璃板上,圆心压小板左缘,向左凸出板外
        g.blit(TEX_FACE, 0, REF_FACE_Y, REF_FACE_D, REF_FACE_D, 0, 0, 256, 256, 256, 256);

        // 指针:时/分/红秒,实时走动(长度按参考帧盘面比例)
        int cx = REF_FACE_D / 2;
        int cy = REF_FACE_Y + REF_FACE_D / 2;
        LocalTime t = java.time.LocalTime.now();
        float sec = t.getSecond() + t.getNano() / 1_000_000_000f;
        float minute = t.getMinute() + sec / 60f;
        float hour = (t.getHour() % 12) + minute / 60f;
        drawHand(g, cx, cy, hour / 12f * Mth.TWO_PI, 31, 9, HAND_DARK);
        drawHand(g, cx, cy, minute / 60f * Mth.TWO_PI, 46, 7, HAND_DARK);
        drawHand(g, cx, cy, sec / 60f * Mth.TWO_PI, 41, 4, HAND_RED);
        g.fill(cx - 4, cy - 4, cx + 4, cy + 4, HAND_DARK);

        pose.popPose();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** 当前时间文本(12/24 小时制跟随配置,冒号取贴图集第 11 格)。 */
    private static String timeText() {
        LocalTime t = java.time.LocalTime.now();
        int hour = t.getHour();
        if (!SAOConfig.clock24h()) {
            hour = hour % 12;
            if (hour == 0) {
                hour = 12;
            }
        }
        return String.format("%02d:%02d", hour, t.getMinute());
    }

    /** 指针:从表盘中心向上延伸的细矩形,绕 Z 轴旋转(12 点方向 = 0 弧度,顺时针)。 */
    private static void drawHand(GuiGraphics g, int cx, int cy, float angle,
                                 float len, float thick, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotation(angle));
        g.fill(Math.round(-thick / 2f), -Math.round(len),
                Math.round(thick / 2f + 0.5f), Math.round(len * 0.22f), color);
        pose.popPose();
    }

    // ------------------------------------------------------------ 拖动

    public static boolean hitCard(int screenW, int screenH, int mx, int my) {
        return SAOConfig.showClock() && rect(screenW, screenH).contains(mx, my);
    }

    public static void beginDrag(int screenW, int screenH, int mx, int my) {
        MenuLayout.Rect r = rect(screenW, screenH);
        grabFx = (mx - r.x()) / (float) r.w();
        grabFy = (my - r.y()) / (float) r.h();
        dragging = true;
        draggedSinceDown = false;
    }

    public static void dragTo(int screenW, int screenH, int mx, int my) {
        if (!dragging) {
            return;
        }
        int w = getScaledW();
        int h = getScaledH();
        float fx = (mx - grabFx * w) / (float) Math.max(1, screenW - w);
        float fy = (my - grabFy * h) / (float) Math.max(1, screenH - h);
        SAOConfig.setClockPanelX(fx);
        SAOConfig.setClockPanelY(fy);
        draggedSinceDown = true;
    }

    public static void endDragAndSave() {
        if (dragging && draggedSinceDown) {
            java.nio.file.Path p = SAOConfig.path();
            if (p == null) {
                p = Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("config").resolve("saomenu.json");
            }
            SAOConfig.save(p);
        }
        dragging = false;
    }
}
