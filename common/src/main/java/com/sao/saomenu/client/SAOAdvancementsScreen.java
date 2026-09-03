package com.sao.saomenu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sao.saomenu.SAOMenuPlatform;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * SAO 成就图鉴:好友面板"成就图鉴"项打开。
 * 与子菜单同款视觉——白色卡片面板 + SAO 条目(图标+名称,悬停橙色高亮),
 * 行级联滑入,悬停显示成就描述,滚轮翻页,官方 ◎ 圆钮关闭。
 */
public class SAOAdvancementsScreen extends Screen {

    private static final ResourceLocation TEX_PANEL = tex("panel.png");
    private static final ResourceLocation TEX_LIST_NORMAL = tex("list_normal.png");
    private static final ResourceLocation TEX_LIST_HOVER = tex("list_hover.png");
    private static final ResourceLocation TEX_BTN_OK = tex("btn_ok.png");
    private static final ResourceLocation TEX_BTN_OK_HOVER = tex("btn_ok_hover.png");

    private static final int CARD_LINE = 0xFFA09FA0;
    private static final int TEXT_DARK = 0xFF3C3C3D;
    private static final int TEXT_GRAY = 0xFF9A9DA0;
    private static final int SHADOW = 0x51303030;

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("saomenu", "textures/gui/" + name);
    }

    private final Screen lastScreen;
    private final List<Advancement> unlocked = new ArrayList<>();
    private final long openedAt;
    private int scroll;
    private int hoverRow = -1;

    public SAOAdvancementsScreen(Screen lastScreen) {
        super(Component.translatable("saomenu.menu.advancements"));
        this.lastScreen = lastScreen;
        this.openedAt = Util.getMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        unlocked.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            var ca = mc.getConnection().getAdvancements();
            Map<Advancement, AdvancementProgress> progress =
                    SAOMenuPlatform.advancementProgress(ca);
            for (Advancement a : ca.getAdvancements().getAllAdvancements()) {
                AdvancementProgress p = progress.get(a);
                DisplayInfo d = a.getDisplay();
                if (d != null && p != null && p.isDone()) {
                    unlocked.add(a);
                }
            }
            unlocked.sort(Comparator.comparing(
                    a -> a.getDisplay().getTitle().getString(), String.CASE_INSENSITIVE_ORDER));
        }
        scroll = 0;
    }

    // ------------------------------------------------------------ 布局

    private int rowH() {
        return 26;
    }

    private int rowsVisible() {
        return Mth.clamp((this.height - 130) / rowH(), 3, 14);
    }

    private int panelW() {
        return Math.min(340, this.width - 24);
    }

    /**
     * panel.png 实心卡片体的宽度:贴图右侧 ~10% 是指向箭头尾巴和透明边距,
     * 行/标题/按钮都必须约束在这个范围内,否则会越过卡片边框"突出去"。
     */
    private int bodyW() {
        return Math.round(panelW() * 0.896f);
    }

    private int panelH() {
        return 34 + rowsVisible() * rowH() + 46;
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return Math.max(8, (this.height - panelH()) / 2);
    }

    private int rowX() {
        return panelX() + 14;
    }

    private int rowW() {
        return Math.round(bodyW()) - 28;
    }

    private int listTop() {
        return panelY() + 34;
    }

    private int maxScroll() {
        return Math.max(0, unlocked.size() - rowsVisible());
    }

    /** 第 v 可见行的行矩形(含滑入动画前的基准位置)。 */
    private MenuLayout.Rect rowRect(int v) {
        return new MenuLayout.Rect(rowX(), listTop() + v * rowH(), rowW(), rowH());
    }

    /** 官方 ◎ 关闭钮(卡片体底部中央)。 */
    private MenuLayout.Rect doneRect() {
        int d = 24;
        return new MenuLayout.Rect(panelX() + Math.round(bodyW()) / 2 - d / 2,
                panelY() + panelH() - d - 12, d, d);
    }

    // ------------------------------------------------------------ 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        // 白卡面板(与人物卡/列表卡同款贴图)
        g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, mulAlpha(SHADOW, 1f));
        RenderSystem.enableBlend();
        g.blit(TEX_PANEL, px, py, pw, ph, 0, 0, pw, ph, pw, ph);
        RenderSystem.disableBlend();

        // 标题 + 下划线(以卡片体为中心)
        Font f = this.font;
        int bodyCx = px + Math.round(bodyW()) / 2;
        String title = tr("saomenu.menu.advancements") + " (" + unlocked.size() + ")";
        g.drawString(f, title, bodyCx - f.width(title) / 2, py + 10, TEXT_DARK, false);
        g.fill(px + 12, py + 22, px + Math.round(bodyW()) - 12, py + 23, CARD_LINE);

        // 条目行:SAO 子菜单同款白条 + 3D 图标 + 名称,级联滑入,悬停橙色高亮
        scroll = Mth.clamp(scroll, 0, maxScroll());
        hoverRow = -1;
        long age = Util.getMillis() - openedAt;
        for (int v = 0; v < rowsVisible(); v++) {
            int idx = scroll + v;
            if (idx >= unlocked.size()) {
                break;
            }
            Advancement a = unlocked.get(idx);
            DisplayInfo d = a.getDisplay();
            float p = Mth.clamp((age - v * 45) / 180f, 0f, 1f);
            float eased = 1f - (1f - p) * (1f - p) * (1f - p);
            MenuLayout.Rect base = rowRect(v);
            int slide = Math.round((1f - eased) * base.w() * 0.45f);
            int x = base.x() + slide;
            int y = base.y();
            boolean hover = mouseX >= x && mouseX < x + base.w()
                    && mouseY >= y && mouseY < y + base.h();
            if (hover) {
                hoverRow = idx;
            }
            RenderSystem.enableBlend();
            if (hover) {
                RenderSystem.setShaderColor(
                        ((SAOConfig.accent() >> 16) & 0xFF) / 255f,
                        ((SAOConfig.accent() >> 8) & 0xFF) / 255f,
                        (SAOConfig.accent() & 0xFF) / 255f, 1f);
                g.blit(TEX_LIST_HOVER, x, y, 0, 0, base.w(), base.h(), base.w(), base.h());
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                g.blit(TEX_LIST_NORMAL, x, y, 0, 0, base.w(), base.h(), base.w(), base.h());
            }
            // 图标(3D 物品)
            int iconSize = Math.round(base.h() * 0.74f);
            g.pose().pushPose();
            g.pose().translate(x + 6 + iconSize / 2f, y + base.h() / 2f, 120f);
            g.pose().scale(iconSize / 16f, iconSize / 16f, 1f);
            g.renderItem(d.getIcon(), -8, -8);
            g.pose().popPose();
            // 名称
            String name = d.getTitle().getString();
            int textX = x + 8 + iconSize + 6;
            int nameW = base.x() + base.w() - 8 - textX;
            g.drawString(f, clip(name, nameW), textX, y + (base.h() - 8) / 2,
                    hover ? 0xFFF9F9F9 : TEXT_DARK, false);
        }

        if (unlocked.isEmpty()) {
            String none = tr("saomenu.panel.no_advancements");
            g.drawString(f, none, bodyCx - f.width(none) / 2,
                    listTop() + 8, TEXT_GRAY, false);
        }

        // 滚动条(内容超出一屏才显示)
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackX = rowX() + rowW() + 4;
            int trackTop = listTop();
            int trackH = rowsVisible() * rowH();
            g.fill(trackX, trackTop, trackX + 2, trackTop + trackH, 0x33232729);
            float frac = rowsVisible() / (float) unlocked.size();
            int thumbH = Math.max(10, Math.round(trackH * frac));
            int thumbY = trackTop + Math.round((trackH - thumbH) * (scroll / (float) maxScroll));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, SAOConfig.accent());
        }

        // 悬停行的描述 tooltip(延迟合批的图标会被它盖住?tooltip 在其后绘制,顺序正确)
        if (hoverRow >= 0 && hoverRow < unlocked.size()) {
            g.renderTooltip(f, unlocked.get(hoverRow).getDisplay().getDescription(), mouseX, mouseY);
        }

        // 官方 ◎ 关闭钮(底部中央);先 flush 行图标再画按钮,防止图标盖钮
        g.flush();
        RenderSystem.disableDepthTest();
        MenuLayout.Rect done = doneRect();
        boolean hoverDone = done.contains(mouseX, mouseY);
        RenderSystem.enableBlend();
        g.blit(hoverDone ? TEX_BTN_OK_HOVER : TEX_BTN_OK, done.x(), done.y(),
                0, 0, done.w(), done.h(), done.w(), done.h());
    }

    private String clip(String s, int maxW) {
        return this.font.width(s) <= maxW ? s
                : this.font.plainSubstrByWidth(s, maxW - this.font.width("…")) + "…";
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        return (Math.round(a * Mth.clamp(factor, 0f, 1f)) << 24) | rgb;
    }

    // ------------------------------------------------------------ 交互

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll + (delta > 0 ? -1 : 1), 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && doneRect().contains((int) mouseX, (int) mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_O) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(lastScreen);
        }
    }
}
