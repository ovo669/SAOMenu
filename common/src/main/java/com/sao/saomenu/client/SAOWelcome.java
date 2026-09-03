package com.sao.saomenu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.SAOMenuPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 进入世界时的 SAO 欢迎动画:顶部 "Welcome to Sword Art Online !" 横幅落下,
 * 下方 Message 面板弹出并显示启动提示,停留后整体淡出。
 *
 * <p>时间轴({@link #bannerAlpha} 等)是不依赖 Minecraft 的纯函数,可单元测试;
 * 由 {@link #clientTick} 检测「无世界 → 有世界」的跳变触发一次,
 * {@link SAOHud#render} 每帧调用 {@link #render} 绘制。</p>
 */
public final class SAOWelcome {

    /** 横幅贴图 625x97:上半为标题文字,下半为装饰横条。 */
    private static final ResourceLocation TEX_WELCOME =
            new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/welcome.png");
    /** Message 面板贴图 350x237(自带投影与圆角,body x8..343 / y8..227)。 */
    private static final ResourceLocation TEX_PANEL =
            new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/message_panel.png");

    private static final int TEX_W_W = 625;
    private static final int TEX_W_H = 97;
    private static final int TEX_P_W = 350;
    private static final int TEX_P_H = 237;

    /** 面板灰色文字带在贴图内的纵向中心(v 81..161)。 */
    private static final float PANEL_MSG_V = 121f / TEX_P_H;
    /** 面板 body 的横向中心(x 8..343)。 */
    private static final float PANEL_MSG_U = 175.5f / TEX_P_W;

    private static final int MSG_DARK = 0xFF3A3D3F;
    /** 面板贴图本身只有 80% 不透明,先垫一层白底,避免地形透上来压掉文字。 */
    private static final int PANEL_BASE = 0xFFF7F7F7;

    // ------------------------------------------------------------ 时间轴(毫秒)

    public static final long BANNER_IN_MS = 480;
    public static final long PANEL_DELAY_MS = 300;
    public static final long PANEL_IN_MS = 420;
    public static final long TEXT_DELAY_MS = 820;
    public static final long TEXT_IN_MS = 320;
    public static final long HOLD_MS = 2600;
    public static final long FADE_MS = 700;
    /** 动画总时长:文字出现完毕 + 停留 + 淡出。 */
    public static final long TOTAL_MS = TEXT_DELAY_MS + TEXT_IN_MS + HOLD_MS + FADE_MS;

    /** 淡出开始时刻。 */
    public static final long FADE_AT_MS = TOTAL_MS - FADE_MS;

    private static long startAt = Long.MIN_VALUE;
    private static boolean inWorld;

    private SAOWelcome() {
    }

    // ------------------------------------------------------------ 触发

    /**
     * 每客户端 tick 调用:检测「无世界 → 有世界」跳变并播放一次。
     *
     * <p>维度切换只会替换 level、不会让 player/level 变 null,因此不会重复触发。</p>
     */
    public static void clientTick(Minecraft mc) {
        boolean now = mc != null && mc.player != null && mc.level != null;
        if (now && !inWorld) {
            start();
        }
        if (!now && inWorld) {
            // 离开世界:清掉依赖实体 id 的缓存,避免换世界后 id 复用导致误显示
            SAOTargetBar3D.reset();
            SAODeathEffect.reset();
            SAOBossBanner.reset();
            // 地图面板收起并释放动态纹理
            SAOMapPanel.reset();
            // 组队状态随世界失效(换服/单人退出)
            com.sao.saomenu.party.SAOClientPartyState.reset();
        }
        inWorld = now;
    }

    /** 立即开始播放(重复调用会重新计时)。 */
    public static void start() {
        if (!SAOConfig.showWelcome()) {
            return;
        }
        startAt = net.minecraft.Util.getMillis();
        if (SAOConfig.sounds()) {
            try {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SAOMenuPlatform.launcherSound(), 0.8F));
            } catch (Throwable ignored) {
                // 非客户端环境(单测):忽略
            }
        }
    }

    /** 立即结束(预览自检在抓其他截图前调用,避免遮挡)。 */
    public static void dismiss() {
        startAt = Long.MIN_VALUE;
    }

    /** 当前是否正在播放。 */
    public static boolean active() {
        return startAt != Long.MIN_VALUE
                && !finished(net.minecraft.Util.getMillis() - startAt);
    }

    // ------------------------------------------------------------ 时间轴纯函数

    public static boolean finished(long elapsed) {
        return elapsed < 0 || elapsed >= TOTAL_MS;
    }

    /** 横幅:淡入后保持,最后随整体淡出。 */
    public static float bannerAlpha(long elapsed) {
        return easeOutCubic(clamp01(elapsed / (float) BANNER_IN_MS)) * globalFade(elapsed);
    }

    /** 面板:延迟后淡入,最后随整体淡出。 */
    public static float panelAlpha(long elapsed) {
        return easeOutCubic(clamp01((elapsed - PANEL_DELAY_MS) / (float) PANEL_IN_MS))
                * globalFade(elapsed);
    }

    /** 面板内提示文字:面板站稳后才出现。 */
    public static float textAlpha(long elapsed) {
        return clamp01((elapsed - TEXT_DELAY_MS) / (float) TEXT_IN_MS) * globalFade(elapsed);
    }

    /** 整体淡出系数:淡出开始前恒为 1。 */
    public static float globalFade(long elapsed) {
        if (elapsed <= FADE_AT_MS) {
            return 1f;
        }
        return 1f - clamp01((elapsed - FADE_AT_MS) / (float) FADE_MS);
    }

    /** 面板弹出缩放:0.88 → 1.0。 */
    public static float panelScale(long elapsed) {
        return 0.88f + 0.12f * easeOutCubic(clamp01((elapsed - PANEL_DELAY_MS) / (float) PANEL_IN_MS));
    }

    /** 横幅从中心向外展开的进度(0 → 1)。 */
    public static float bannerReveal(long elapsed) {
        return easeOutCubic(clamp01(elapsed / (float) BANNER_IN_MS));
    }

    // ------------------------------------------------------------ 渲染

    /** 每帧绘制(由 SAOHud.render 调用,不受 showHud 开关影响)。 */
    public static void render(GuiGraphics g, int screenW, int screenH) {
        if (startAt == Long.MIN_VALUE) {
            return;
        }
        long elapsed = net.minecraft.Util.getMillis() - startAt;
        if (finished(elapsed)) {
            startAt = Long.MIN_VALUE;
            return;
        }

        // 整体缩小到原设计的 2/3(横幅与面板都由横幅宽度派生)
        int bannerW = Math.min(Math.round(screenW * 0.70f * 2f / 3f), Math.max(80, screenW - 24));
        int bannerH = Math.max(8, Math.round(bannerW * TEX_W_H / (float) TEX_W_W));
        int bannerX = (screenW - bannerW) / 2;
        // 横幅横跨屏幕中上部,会压到左上角血条板与效果图标行,故下移到它们之下
        int top = Math.round(screenH * 0.10f);
        if (SAOConfig.showHud()) {
            top = Math.max(top, MenuLayout.plateBottom(screenW) + 22 + 6);
        }
        int bannerY = top;

        int panelW = Math.max(60, Math.round(bannerW * 0.42f));
        int panelH = Math.max(40, Math.round(panelW * TEX_P_H / (float) TEX_P_W));
        int panelCx = screenW / 2;
        int panelTop = Math.min(
                bannerY + bannerH + Math.round(screenH * 0.05f),
                Math.max(bannerY + bannerH, screenH - panelH - 8));
        int panelCy = panelTop + panelH / 2;

        float ba = bannerAlpha(elapsed);
        if (ba > 0.004f) {
            // 从中间向外展开:宽度从 0 拉伸到全宽,中心固定
            float reveal = bannerReveal(elapsed);
            int drawW = Math.max(1, Math.round(bannerW * reveal));
            int drawX = bannerX + (bannerW - drawW) / 2;
            RenderSystem.enableBlend();
            shaderAlpha(ba);
            g.blit(TEX_WELCOME, drawX, bannerY, drawW, bannerH,
                    0f, 0f, TEX_W_W, TEX_W_H, TEX_W_W, TEX_W_H);
            shaderAlpha(1f);
        }

        float pa = panelAlpha(elapsed);
        if (pa <= 0.004f) {
            return;
        }
        float s = panelScale(elapsed);
        g.pose().pushPose();
        g.pose().translate(panelCx, panelCy, 0f);
        g.pose().scale(s, s, 1f);
        g.pose().translate(-panelW / 2f, -panelH / 2f, 0f);

        // 贴图 body(x8..343 / y8..227)只有 80% 不透明,先垫白底再叠贴图,
        // 否则地形会从面板里透出来盖掉 Message 文字
        int bodyL = Math.round(panelW * 8f / TEX_P_W);
        int bodyR = Math.round(panelW * 343f / TEX_P_W);
        int bodyT = Math.round(panelH * 8f / TEX_P_H);
        int bodyB = Math.round(panelH * 227f / TEX_P_H);
        g.fill(bodyL, bodyT, bodyR, bodyB, mulAlpha(PANEL_BASE, pa));
        // fill() 收尾会关掉混合,必须在 blit 前重新开启,
        // 否则贴图四周的半透明投影会被画成实心黑框
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        shaderAlpha(pa);
        g.blit(TEX_PANEL, 0, 0, panelW, panelH, 0f, 0f, TEX_P_W, TEX_P_H, TEX_P_W, TEX_P_H);
        shaderAlpha(1f);

        // 灰色文字带内的主题色左镶边(与通知横幅统一的视觉语言)
        int bandTop = Math.round(panelH * 81f / TEX_P_H);
        int bandBot = Math.round(panelH * 161f / TEX_P_H);
        g.fill(bodyL, bandTop, bodyL + Math.max(1, Math.round(panelW * 0.011f)), bandBot,
                mulAlpha(SAOConfig.accent(), pa));

        float ta = textAlpha(elapsed);
        if (ta > 0.004f) {
            Font font = Minecraft.getInstance().font;
            String msg = Component.translatable("saomenu.welcome.msg").getString();
            // 参考图里提示文字约占面板 body 宽的 44%,8px 字体直接画偏小,放大后再绘制
            float ts = 1.5f;
            g.pose().pushPose();
            g.pose().translate(panelW * PANEL_MSG_U, panelH * PANEL_MSG_V, 0f);
            g.pose().scale(ts, ts, 1f);
            g.drawString(font, msg, -font.width(msg) / 2, -4, mulAlpha(MSG_DARK, ta), false);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------ 小工具

    private static void shaderAlpha(float a) {
        RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(a, 0f, 1f));
    }

    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        return (Math.round(a * clamp01(factor)) << 24) | (argb & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }
}
