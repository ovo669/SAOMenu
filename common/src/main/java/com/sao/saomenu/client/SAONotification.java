package com.sao.saomenu.client;

import com.sao.saomenu.SAOMenuPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * SAO 风格通知系统:右上角白底半透明横幅(主题色镶边),滑入停留后淡出。
 *
 * <p>数据层({@link #push}/{@link #prune})不依赖渲染,可单元测试;
 * 由 {@link SAOHud} 每帧调用 {@link #render} 绘制。</p>
 */
public final class SAONotification {

    /** 一条通知。message 可为空字符串(只显示标题);icon 可为 null。 */
    public record Entry(String title, String message, net.minecraft.world.item.ItemStack icon, long at) {
    }

    private static final List<Entry> QUEUE = new ArrayList<>();

    private static final long SLIDE_MS = 150;
    private static final long STAY_MS = 2600;
    private static final long FADE_MS = 300;
    private static final int MAX_ENTRIES = 4;

    // 白色底、略透明(85%);文字改用深色保证可读性
    private static final int PANEL_BG = 0xD9FFFFFF;
    private static final int SHADOW = 0x3A000000;
    private static final int TITLE_DARK = 0xFF1B1D1F;
    private static final int MSG_DARK = 0xFF585C5E;

    private SAONotification() {
    }

    /** 入队一条通知并播放提示音(受 SAOConfig.sounds 控制)。 */
    public static void push(String title, String message) {
        push(title, message, null);
    }

    /** 带图标入队(成就通知传入成就展示物品)。 */
    public static void push(String title, String message, net.minecraft.world.item.ItemStack icon) {
        long now = now();
        QUEUE.add(new Entry(title, message, icon, now));
        while (QUEUE.size() > MAX_ENTRIES) {
            QUEUE.remove(0);
        }
        playSound();
    }

    /** 移除已完全淡出的通知。 */
    public static void prune(long now) {
        Iterator<Entry> it = QUEUE.iterator();
        while (it.hasNext()) {
            if (now - it.next().at() > STAY_MS + FADE_MS) {
                it.remove();
            }
        }
    }

    public static int size() {
        return QUEUE.size();
    }

    public static void clear() {
        QUEUE.clear();
    }

    /** 每帧绘制(右上角纵向堆叠,滑入 + 淡出)。 */
    public static void render(GuiGraphics g, int screenW, int screenH, long now) {
        prune(now);
        Font font = Minecraft.getInstance().font;
        int i = 0;
        for (Entry e : QUEUE) {
            long age = now - e.at();
            if (age < 0) {
                continue;
            }
            float slide = easeOutCubic(clamp01(age / (float) SLIDE_MS));
            float alpha = 1f - clamp01((age - STAY_MS) / (float) FADE_MS);
            if (alpha <= 0f) {
                continue;
            }
            int w = Math.max(140, Math.round(screenW * 0.30f));
            int h = e.message().isEmpty() ? 26 : 34;
            // 从屏幕右缘外滑入到最终位置(screenW-8-w)
            int x = Math.round(screenW + 8 - (w + 16) * slide);
            int y = 8 + i * 40;

            g.fill(x + 3, y + 3, x + w + 3, y + h + 3, mulAlpha(SHADOW, alpha));
            g.fill(x, y, x + w, y + h, mulAlpha(PANEL_BG, alpha));
            g.fill(x, y, x + 3, y + h, mulAlpha(SAOConfig.accent(), alpha));

            // 成就图标(若有)
            int textX = x + 10;
            if (e.icon() != null && !e.icon().isEmpty()) {
                int isz = Math.min(16, h - 8);
                g.pose().pushPose();
                g.pose().translate(x + 8 + isz / 2f, y + h / 2f, 120f);
                g.pose().scale(isz / 16f, isz / 16f, 1f);
                g.renderItem(e.icon(), -8, -8);
                g.pose().popPose();
                textX = x + 8 + isz + 6;
            }
            g.drawString(font, clip(font, e.title(), w - (textX - x) - 8), textX, y + 5,
                    mulAlpha(TITLE_DARK, alpha), false);
            if (!e.message().isEmpty()) {
                g.drawString(font, clip(font, e.message(), w - (textX - x) - 8), textX, y + 19,
                        mulAlpha(MSG_DARK, alpha), false);
            }
            i++;
        }
    }

    private static String clip(Font font, String s, int maxW) {
        return font.width(s) <= maxW ? s : font.plainSubstrByWidth(s, maxW - font.width("…")) + "…";
    }

    private static void playSound() {
        if (!SAOConfig.sounds()) {
            return;
        }
        try {
            // 单元测试等非客户端环境下 Minecraft.getInstance() 为 null,静默跳过
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SAOMenuPlatform.panelSound(), 0.6F));
        } catch (Throwable ignored) {
            // 非客户端环境:忽略
        }
    }

    private static long now() {
        return net.minecraft.Util.getMillis();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        return (Math.round(a * Mth.clamp(factor, 0f, 1f)) << 24) | rgb;
    }
}
