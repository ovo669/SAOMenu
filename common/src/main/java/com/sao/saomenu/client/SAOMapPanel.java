package com.sao.saomenu.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sao.saomenu.SAOMenu;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;

/**
 * SAO 风格悬浮地图面板(参照动画里亚丝娜手持的地图卡):
 * 半透明白框 + 蓝青水彩色调的地图 + 红色成员点位。
 *
 * <h2>交互模型</h2>
 * <ul>
 *   <li>菜单里点「地图」→ {@link #toggle()} 面板在菜单旁滑出</li>
 *   <li>按住面板任意处拖动,位置(屏幕比例)持久化到配置</li>
 *   <li>右上角图钉按钮 → {@link #togglePin()}:固定后关掉菜单,
 *       面板继续以 HUD 层渲染({@link #renderHud}),再点解除</li>
 * </ul>
 *
 * <h2>地图着色</h2>
 * <p>原版地图 colors[] 解码后按亮度重映射到蓝青色渐变(暗部深蓝 → 亮部浅青),
 * 复刻动画地图的水彩蓝观感;未探索区域为更深的底色。每 500ms 重传一次像素。</p>
 */
public final class SAOMapPanel {

    private static final ResourceLocation TEX_MAP =
            new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/dyn_map.png");
    private static final ResourceLocation TEX_BTN =
            new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/btn_circle.png");

    // 配色(参照动画地图卡;fill 单层混合,35% 白即得玻璃卡观感)
    private static final int CARD_BG = 0x59FCFCFC;        // 半透明白(35%)
    private static final int CARD_EDGE = 0x4DFFFFFF;      // 亮边
    private static final int CARD_SHADOW = 0x4B303030;
    private static final int HEADER_TEXT = 0xFF6E7173;
    private static final int CARD_LINE = 0xA8C9CACC;
    private static final int FOOTER_TEXT = 0xCC9A9DA0;
    private static final int MAP_BACKDROP = 0xFF10293C;   // 无地图底色(深蓝)
    private static final int DOT_CORE = 0xFFE03A50;       // SAO 红点

    private static final int MAP_PX = 128;
    private static final long UPLOAD_MS = 500;
    private static final long OPEN_MS = 220;

    // ------------------------------------------------------------ 状态

    /** 面板是否已滑出(菜单内可见)。 */
    private static boolean shown;
    private static long openAt;

    /** 拖动状态。 */
    private static boolean dragging;
    private static float dragGrabFx;
    private static float dragGrabFy;
    private static boolean draggedSinceDown;

    /** 地图纹理缓存。 */
    private static DynamicTexture mapTexture;
    private static boolean textureRegistered;
    private static long lastUploadAt;
    private static String lastMapSig = "";

    private SAOMapPanel() {
    }

    // ------------------------------------------------------------ 开关/图钉

    /** 菜单「地图」项调用:滑出/收回面板。 */
    public static void toggle() {
        shown = !shown;
        if (shown) {
            openAt = Util.getMillis();
        }
    }

    public static boolean isShown() {
        return shown;
    }

    public static boolean isPinned() {
        return SAOConfig.mapPinned();
    }

    /** 图钉切换:固定后关菜单仍显示。 */
    public static void togglePin() {
        SAOConfig.setMapPinned(!SAOConfig.mapPinned());
        savePos();
    }

    // ------------------------------------------------------------ 几何

    /** 面板高度 = 38% 屏高,宽高比 0.72(参照动画手持卡大小)。 */
    public static int panelH(int screenH) {
        return Math.round(screenH * 0.38f);
    }

    public static int panelW(int screenH) {
        return Math.round(panelH(screenH) * 0.72f);
    }

    /** 面板左上角像素坐标(按配置比例换算并钳回屏幕内;anim 为入场滑入偏移)。 */
    private static int[] panelOrigin(int screenW, int screenH, float animSlide) {
        int w = panelW(screenH);
        int h = panelH(screenH);
        float fx = Mth.clamp(SAOConfig.mapPanelX(), 0f, 1f);
        float fy = Mth.clamp(SAOConfig.mapPanelY(), 0f, 1f);
        int x = Math.round(fx * (screenW - w));
        int y = Math.round(fy * (screenH - h));
        return new int[]{Math.max(2, Math.min(x, screenW - w - 2)) - Math.round(animSlide),
                Math.max(2, Math.min(y, screenH - h - 2))};
    }

    private static MenuLayout.Rect cardRect(int screenW, int screenH) {
        long age = Util.getMillis() - openAt;
        float p = shown ? Mth.clamp(age / (float) OPEN_MS, 0f, 1f) : 1f;
        float slide = (1f - easeOutCubic(p)) * panelW(screenH) * 0.40f;
        int[] o = panelOrigin(screenW, screenH, slide);
        return new MenuLayout.Rect(o[0], o[1], panelW(screenH), panelH(screenH));
    }

    /** 卡内地图显示区。 */
    private static MenuLayout.Rect mapRect(MenuLayout.Rect card) {
        int mx = card.x() + Math.round(card.w() * 0.07f);
        int my = card.y() + Math.round(card.h() * 0.125f);
        int mw = Math.round(card.w() * 0.86f);
        int mh = Math.round(card.h() * 0.70f);
        return new MenuLayout.Rect(mx, my, mw, mh);
    }

    /** 右上角图钉按钮中心。 */
    private static int[] pinCenter(MenuLayout.Rect card) {
        int d = pinSize(card);
        return new int[]{card.x() + card.w() - d / 2 - Math.round(card.w() * 0.05f),
                card.y() + Math.round(card.h() * 0.058f) + d / 2};
    }

    private static int pinSize(MenuLayout.Rect card) {
        return Math.max(10, Math.round(card.h() * 0.045f));
    }

    // ------------------------------------------------------------ 命中

    public static boolean hitCard(int screenW, int screenH, int mx, int my) {
        if (!shown) {
            return false;
        }
        return cardRect(screenW, screenH).contains(mx, my);
    }

    public static boolean hitPin(int screenW, int screenH, int mx, int my) {
        MenuLayout.Rect card = cardRect(screenW, screenH);
        int[] pc = pinCenter(card);
        int r = pinSize(card) / 2 + 2;
        return MenuLayout.inCircle(pc[0], pc[1], r, mx, my);
    }

    /** 开始拖动(记录抓住点相对面板的比例)。 */
    public static void beginDrag(int screenW, int screenH, int mx, int my) {
        MenuLayout.Rect card = cardRect(screenW, screenH);
        dragGrabFx = (mx - card.x()) / (float) card.w();
        dragGrabFy = (my - card.y()) / (float) card.h();
        dragging = true;
        draggedSinceDown = false;
    }

    /** 拖动中:更新配置位置(屏幕比例,拖出屏幕自然钳制)。 */
    public static void dragTo(int screenW, int screenH, int mx, int my) {
        if (!dragging) {
            return;
        }
        int w = panelW(screenH);
        int h = panelH(screenH);
        float fx = (mx - dragGrabFx * w) / (float) Math.max(1, screenW - w);
        float fy = (my - dragGrabFy * h) / (float) Math.max(1, screenH - h);
        SAOConfig.setMapPanelX(fx);
        SAOConfig.setMapPanelY(fy);
        draggedSinceDown = true;
    }

    /** 松手:若真拖动过则落盘。 */
    public static void endDragAndSave() {
        if (dragging && draggedSinceDown) {
            savePos();
        }
        dragging = false;
    }

    private static void savePos() {
        java.nio.file.Path p = SAOConfig.path();
        if (p == null) {
            p = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("saomenu.json");
        }
        SAOConfig.save(p);
    }

    // ------------------------------------------------------------ 渲染

    /** 菜单内渲染(SAOMenuScreen 调用,alpha 跟随菜单开关动画)。 */
    public static void render(GuiGraphics g, Minecraft mc, int screenW, int screenH, float alpha) {
        if (!shown) {
            return;
        }
        draw(g, mc, screenW, screenH, alpha, Util.getMillis());
    }

    /** HUD 渲染(SAOHud 调用;图钉固定且菜单未开时显示)。 */
    public static void renderHud(GuiGraphics g, Minecraft mc, int screenW, int screenH) {
        if (!shown || !SAOConfig.mapPinned()) {
            return;
        }
        draw(g, mc, screenW, screenH, 0.92f, Util.getMillis());
    }

    private static void draw(GuiGraphics g, Minecraft mc, int screenW, int screenH, float alpha, long now) {
        MenuLayout.Rect card = cardRect(screenW, screenH);
        MenuLayout.Rect mapAt = mapRect(card);
        float ease = Mth.clamp((now - openAt) / (float) OPEN_MS, 0f, 1f);
        if (!shown) {
            ease = 1f;
        }
        float a = alpha * Mth.clamp(ease * 1.4f, 0f, 1f);

        // fill() 自身不管理混合:前面任何 fill 收尾都会把混合关掉,
        // 不显式开启时卡片 alpha 会被当作不透明写入,半透明底就变成实心白
        RenderSystem.enableBlend();

        // 阴影 + 半透明白卡(圆角:两横两竖交叉)
        fillRounded(g, card.x() + 2, card.y() + 3, card.w(), card.h(),
                Math.max(2, Math.round(card.h() * 0.015f)), mulAlpha(CARD_SHADOW, a));
        fillRounded(g, card.x(), card.y(), card.w(), card.h(),
                Math.max(2, Math.round(card.h() * 0.015f)), mulAlpha(CARD_BG, a));
        // 顶部亮边(半透明卡片的立体感)
        g.fill(card.x() + 3, card.y(), card.x() + card.w() - 3, card.y() + 1, mulAlpha(CARD_EDGE, a));

        Font f = mc.font;
        // 头部小字 + 细线(参照动画卡片顶部信息条)
        g.drawString(f, tr("saomenu.map.title"), card.x() + Math.round(card.w() * 0.07f),
                card.y() + Math.round(card.h() * 0.035f), mulAlpha(HEADER_TEXT, a), false);
        int lineY = card.y() + Math.round(card.h() * 0.105f);
        g.fill(card.x() + Math.round(card.w() * 0.07f), lineY,
                card.x() + card.w() - Math.round(card.w() * 0.07f), lineY + 1, mulAlpha(CARD_LINE, a));

        // 地图区
        MapItemSavedData data = refreshTexture(mc, now);
        g.fill(mapAt.x() - 1, mapAt.y() - 1, mapAt.x() + mapAt.w() + 1, mapAt.y() + mapAt.h() + 1,
                mulAlpha(MAP_BACKDROP, a));
        if (data != null && mapTexture != null) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, a);
            g.blit(TEX_MAP, mapAt.x(), mapAt.y(), mapAt.w(), mapAt.h(),
                    0f, 0f, MAP_PX, MAP_PX, MAP_PX, MAP_PX);
            drawDots(g, mc, mapAt, data);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            g.fill(mapAt.x(), mapAt.y(), mapAt.x() + mapAt.w(), mapAt.y() + mapAt.h(),
                    mulAlpha(0xB818334A, a));
        }

        // 底部信息条(参照动画:两行小字)
        int footY = mapAt.y() + mapAt.h() + Math.round(card.h() * 0.025f);
        Player p = mc.player;
        if (p != null) {
            String l1 = String.format("X %d / Z %d", (int) Math.floor(p.getX()), (int) Math.floor(p.getZ()));
            g.drawString(f, l1, card.x() + Math.round(card.w() * 0.07f), footY,
                    mulAlpha(FOOTER_TEXT, a), false);
            if (data != null) {
                int blocks = 1 << data.scale;
                String l2 = tr("saomenu.map.scale", blocks);
                g.drawString(f, l2, card.x() + Math.round(card.w() * 0.07f),
                        footY + Math.round(card.h() * 0.045f), mulAlpha(FOOTER_TEXT, a), false);
            }
        }

        drawPinButton(g, card, a, mc);
        // 卡片整组画完再关混合,避免影响后续 HUD 图元
        RenderSystem.disableBlend();
    }

    /** 图钉按钮:固定 = 主题色,未固定 = 灰;白色小图钉符号。 */
    private static void drawPinButton(GuiGraphics g, MenuLayout.Rect card, float alpha, Minecraft mc) {
        int d = pinSize(card);
        int[] pc = pinCenter(card);
        int bx = pc[0] - d / 2;
        int by = pc[1] - d / 2;
        boolean pinned = SAOConfig.mapPinned();
        if (pinned) {
            setTint(SAOConfig.accent(), alpha);
        } else {
            setTint(0xFF83868A, alpha);
        }
        RenderSystem.enableBlend();
        g.blit(TEX_BTN, bx, by, 0, 0, d, d, d, d);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // 图钉符号:竖针 + 针头点(白色;混合保持开启,由 draw() 收尾统一关闭)
        int gx = pc[0];
        int gy = pc[1];
        int ia = Math.round(255 * Mth.clamp(alpha, 0f, 1f)) << 24;
        g.fill(gx, gy - d / 4, gx + 1, gy + d / 4, 0xFFFFFFFF & ia | ia);
        g.fill(gx - 1, gy - d / 3, gx + 2, gy - d / 4 + 1, 0xFFFFFFFF & ia | ia);
    }

    /**
     * 红点:直接使用地图数据里<strong>服务器同步好的装饰坐标</strong>。
     *
     * <p>原版客户端同步包(ClientboundMapItemDataPacket)不含地图中心的世界坐标,
     * 客户端拿到的 {@code centerX/centerZ} 恒为 (0,0)——按世界坐标换算永远失败
     * (旧实现的 bug)。装饰坐标(x/y,byte ±128)是服务器算好的相对位置,
     * 像素 = x/2 + 64,与原版装饰渲染一致。
     * 类型:PLAYER 系 = 自己(大点);FRAME = 队伍成员(小点)。</p>
     */
    private static void drawDots(GuiGraphics g, Minecraft mc, MenuLayout.Rect mapAt,
                                 MapItemSavedData data) {
        if (mc.player == null) {
            return;
        }
        for (MapDecoration dec : data.getDecorations()) {
            MapDecoration.Type type = dec.getType();
            boolean self = type == MapDecoration.Type.PLAYER
                    || type == MapDecoration.Type.PLAYER_OFF_MAP
                    || type == MapDecoration.Type.PLAYER_OFF_LIMITS;
            boolean mate = type == MapDecoration.Type.FRAME;
            if (!self && !mate) {
                continue;
            }
            int px = (int) Math.round(dec.getX() / 2.0 + 64.0);
            int py = (int) Math.round(dec.getY() / 2.0 + 64.0);
            if (px < 0 || py < 0 || px > 127 || py > 127) {
                continue;
            }
            drawDot(g, mapAt, px, py, self);
        }
    }

    /** 红点:实心小方点近似圆,自身稍大、队友稍小。 */
    private static void drawDot(GuiGraphics g, MenuLayout.Rect mapAt, int px, int py, boolean big) {
        int cx = mapAt.x() + Math.round(px / 127f * (mapAt.w() - 1));
        int cy = mapAt.y() + Math.round(py / 127f * (mapAt.h() - 1));
        int r = big ? 2 : 1;
        g.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, DOT_CORE);
    }

    // ------------------------------------------------------------ 纹理

    /** 找玩家的已填充地图(主手 → 副手 → 背包第一张)。 */
    private static ItemStack findMap(Minecraft mc) {
        Player p = mc.player;
        if (p == null) {
            return ItemStack.EMPTY;
        }
        ItemStack held = !p.getMainHandItem().isEmpty() && MapItem.getMapId(p.getMainHandItem()) != null
                ? p.getMainHandItem() : p.getOffhandItem();
        if (!held.isEmpty() && MapItem.getMapId(held) != null) {
            return held;
        }
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof MapItem && MapItem.getMapId(s) != null) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 每 500ms 把地图颜色重映射成蓝青水彩调并上传;返回当前数据(无地图 null)。 */
    private static MapItemSavedData refreshTexture(Minecraft mc, long now) {
        ItemStack stack = findMap(mc);
        Integer mapId = stack.isEmpty() ? null : MapItem.getMapId(stack);
        MapItemSavedData data = mapId == null || mc.level == null
                ? null : MapItem.getSavedData(mapId, mc.level);
        if (data == null) {
            return null;
        }
        String sig = mapId + "@" + data.scale;
        if (mapTexture != null && sig.equals(lastMapSig) && now - lastUploadAt < UPLOAD_MS) {
            return data;
        }
        if (mapTexture == null) {
            mapTexture = new DynamicTexture(MAP_PX, MAP_PX, true);
            mc.getTextureManager().register(TEX_MAP, mapTexture);
            textureRegistered = true;
        }
        NativeImage img = mapTexture.getPixels();
        if (img == null) {
            return data;
        }
        byte[] colors = data.colors;
        for (int y = 0; y < MAP_PX; y++) {
            for (int x = 0; x < MAP_PX; x++) {
                int idx = x + y * MAP_PX;
                int packed = idx < colors.length ? colors[idx] & 0xFF : 0;
                int abgr;
                if ((packed >> 2) == 0) {
                    // 未探索:近黑深底棋盘微差(与已探索地形拉开最大对比)
                    abgr = ((x + y) & 1) == 0 ? packAbgr(9, 15, 26) : packAbgr(6, 11, 20);
                } else {
                    int v = MapColor.getColorFromPackedId(packed);
                    // NativeImage ABGR: r 低 8 位
                    int r = v & 0xFF;
                    int gg = (v >> 8) & 0xFF;
                    int b = (v >> 16) & 0xFF;
                    float l = (r * 0.30f + gg * 0.59f + b * 0.11f) / 255f;
                    abgr = tealRamp(l);
                }
                img.setPixelRGBA(x, y, abgr);
            }
        }
        mapTexture.upload();
        lastUploadAt = now;
        lastMapSig = sig;
        return data;
    }

    /**
     * 亮度 → 蓝青水彩渐变。地形整体提亮提饱和,与「未探索近黑底」拉开强对比,
     * 任何地形在面板上都能一眼读出(旧配色地形色与底色过于接近,整张图像空白)。
     * 输出 NativeImage ABGR。
     */
    static int tealRamp(float l) {
        l = Mth.clamp(l, 0f, 1f);
        int r;
        int g;
        int b;
        if (l < 0.5f) {
            float t = l / 0.5f;
            r = lerp(24, 46, t);
            g = lerp(66, 128, t);
            b = lerp(100, 170, t);
        } else {
            float t = (l - 0.5f) / 0.5f;
            r = lerp(62, 170, t);
            g = lerp(152, 234, t);
            b = lerp(192, 250, t);
        }
        return packAbgr(r, g, b);
    }

    private static int packAbgr(int r, int g, int b) {
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    // ------------------------------------------------------------ 生命周期

    /** 换世界/退服:释放纹理并收回面板(图钉偏好在配置里保留)。 */
    public static void reset() {
        shown = false;
        dragging = false;
        if (textureRegistered) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.getTextureManager().release(TEX_MAP);
            }
        }
        mapTexture = null;
        textureRegistered = false;
        lastMapSig = "";
    }

    // ---------------------------------------------------------------- 工具

    private static String tr(String key, Object... args) {
        String s = Component.translatable(key).getString();
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        return (Math.round(a * Mth.clamp(factor, 0f, 1f)) << 24) | rgb;
    }

    private static void setTint(int argb, float alpha) {
        RenderSystem.setShaderColor(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                Mth.clamp(alpha, 0f, 1f));
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        // 三段互不重叠:中段全宽,上下段各让出 r。
        // 不能用「横条+竖条」两段画法——重叠区在 alpha 混合下会叠两层,
        // 65% 的卡底会被叠成 88%,整张卡看起来接近实心
        g.fill(x, y + r, x + w, y + h - r, color);
        g.fill(x + r, y, x + w - r, y + r, color);
        g.fill(x + r, y + h - r, x + w - r, y + h, color);
    }
}
