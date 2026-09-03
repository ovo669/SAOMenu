package com.sao.saomenu.client;

import com.sao.saomenu.SAOMenuPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * SAO 风格模组设置界面:半透明白面板 + 橙色滑块/开关,
 * 修改即时生效并持久化到 {@code config/saomenu.json}。
 *
 * <p>行布局:6 个滑块(锚点 X/Y、菜单缩放、浮动幅度、碎裂密度、主题色)+ 13 个开关
 * (音效、隐藏原版快捷栏、SAO HUD、皮肤头像、SAO 通知、时钟、24 小时制、
 * 日期、入世欢迎动画、死亡碎裂、菜单跟随鼠标、目标血条、伤害数字)+ 主题预设行
 * (SAO 橙/ALO 蓝/GGO 红)+ 底行按钮(恢复默认 / 完成)。</p>
 */
public class SAOConfigScreen extends Screen {

    private static final ResourceLocation TEX_BTN = tex("btn_circle.png");

    private static final int PANEL_BG = 0xE6232729;
    private static final int ROW_HOVER = 0x2DF9F9F9;
    private static final int TEXT_WHITE = 0xFFF9F9F9;
    private static final int TEXT_GRAY = 0xFF9A9B9D;
    private static final int TEXT_ON_ACCENT = 0xFF232323;

    private static final int SLIDERS = 6;
    private static final int TOGGLES = 12;
    private static final int THEME_HUES = SLIDERS + TOGGLES; // 主题预设行号
    private static final int ROWS = SLIDERS + TOGGLES + 2;   // + 预设行 + 按钮行

    private final Screen lastScreen;
    private int dragRow = -1;

    public SAOConfigScreen(Screen lastScreen) {
        super(Component.translatable("saomenu.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("saomenu", "textures/gui/" + name);
    }

    // ------------------------------------------------------------ 布局

    private int panelW() {
        return Math.min(340, this.width - 20);
    }

    private int panelH() {
        return 34 + ROWS * rowH() + 14;
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return Math.max(10, (this.height - panelH()) / 2);
    }

    private int rowH() {
        return Math.max(10, Math.min(26, (this.height - 120) / ROWS));
    }

    private int rowY(int i) {
        return panelY() + 34 + i * rowH();
    }

    private boolean rowHovered(int i, int mouseX, int mouseY) {
        int y = rowY(i);
        return mouseX >= panelX() + 8 && mouseX <= panelX() + panelW() - 8
                && mouseY >= y && mouseY < y + rowH();
    }

    // ------------------------------------------------------------ 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x5A000000);
        g.fill(px, py, px + pw, py + ph, PANEL_BG);

        String title = tr("saomenu.config.title");
        g.drawString(this.font, title, px + pw / 2 - this.font.width(title) / 2,
                py + 12, TEXT_WHITE, false);

        for (int i = 0; i < ROWS; i++) {
            renderRow(g, i, mouseX, mouseY);
        }
    }

    private void renderRow(GuiGraphics g, int i, int mouseX, int mouseY) {
        int px = panelX();
        int pw = panelW();
        int y = rowY(i);
        if (rowHovered(i, mouseX, mouseY)) {
            g.fill(px + 8, y, px + pw - 8, y + rowH(), ROW_HOVER);
        }
        switch (i) {
            case 0 -> renderSlider(g, i, y, tr("saomenu.config.anchor_x"),
                    SAOConfig.anchorX(), SAOConfig.ANCHOR_MIN, SAOConfig.ANCHOR_MAX, pct(SAOConfig.anchorX()));
            case 1 -> renderSlider(g, i, y, tr("saomenu.config.anchor_y"),
                    SAOConfig.anchorY(), SAOConfig.ANCHOR_MIN, SAOConfig.ANCHOR_MAX, pct(SAOConfig.anchorY()));
            case 2 -> renderSlider(g, i, y, tr("saomenu.config.scale"),
                    SAOConfig.menuScale(), SAOConfig.SCALE_MIN, SAOConfig.SCALE_MAX,
                    String.format("%.2fx", SAOConfig.menuScale()));
            case 3 -> renderSlider(g, i, y, tr("saomenu.config.bob"),
                    SAOConfig.bobAmp(), SAOConfig.BOB_MIN, SAOConfig.BOB_MAX,
                    String.format("%.1fx", SAOConfig.bobAmp()));
            case 4 -> renderSlider(g, i, y, tr("saomenu.config.shatter_density"),
                    SAOConfig.deathShatterDensity(), SAOConfig.SHATTER_MIN, SAOConfig.SHATTER_MAX,
                    String.format("%.1fx", SAOConfig.deathShatterDensity()));
            case 5 -> renderSlider(g, i, y, tr("saomenu.config.accent"),
                    SAOConfig.accentHue(), 0f, 360f, Math.round(SAOConfig.accentHue()) + "°");
            case 6 -> renderToggle(g, y, tr("saomenu.config.sounds"), SAOConfig.sounds());
            case 7 -> renderToggle(g, y, tr("saomenu.config.hide_hotbar"), SAOConfig.hideHotbar());
            case 8 -> renderToggle(g, y, tr("saomenu.config.show_hud"), SAOConfig.showHud());
            case 9 -> renderToggle(g, y, tr("saomenu.config.sao_toasts"), SAOConfig.saoToasts());
            case 10 -> renderToggle(g, y, tr("saomenu.config.show_clock"), SAOConfig.showClock());
            case 11 -> renderToggle(g, y, tr("saomenu.config.clock_24h"), SAOConfig.clock24h());
            case 12 -> renderToggle(g, y, tr("saomenu.config.clock_date"), SAOConfig.clockDate());
            case 13 -> renderToggle(g, y, tr("saomenu.config.show_welcome"), SAOConfig.showWelcome());
            case 14 -> renderToggle(g, y, tr("saomenu.config.death_shatter"), SAOConfig.deathShatter());
            case 15 -> renderToggle(g, y, tr("saomenu.config.show_avatar"), SAOConfig.showAvatar());
            case 16 -> renderToggle(g, y, tr("saomenu.config.target_bar"), SAOConfig.showTargetBar());
            case 17 -> renderToggle(g, y, tr("saomenu.config.damage_numbers"), SAOConfig.showDamageNumbers());
            case 18 -> {
                // 主题预设:SAO 橙 / ALO 蓝 / GGO 红
                g.drawString(this.font, tr("saomenu.config.theme"), px + 12, y + (rowH() - 8) / 2, TEXT_WHITE, false);
                int bw = (pw - 150 - 24 - 2 * 6) / 3;
                String[] keys = {"saomenu.theme.sao", "saomenu.theme.alo", "saomenu.theme.ggo"};
                float[] hues = {41.44f, 202f, 355f};
                for (int t = 0; t < 3; t++) {
                    int bx = px + 150 + t * (bw + 6);
                    boolean sel = Math.round(SAOConfig.accentHue()) == Math.round(hues[t]);
                    renderButton(g, bx, y, bw, tr(keys[t]), sel || rowHovered(i, mouseX, mouseY));
                }
            }
            case 20 -> {
                int bw = (pw - 44) / 2;
                renderButton(g, px + 12, y, bw, tr("saomenu.config.reset"), rowHovered(i, mouseX, mouseY));
                renderButton(g, px + pw - 12 - bw, y, bw, tr("saomenu.config.done"), rowHovered(i, mouseX, mouseY));
            }
        }
    }

    private void renderSlider(GuiGraphics g, int i, int y, String label,
                              float value, float lo, float hi, String text) {
        int px = panelX();
        int pw = panelW();
        int rh = rowH();
        g.drawString(this.font, label, px + 12, y + (rh - 8) / 2, TEXT_WHITE, false);

        int x0 = px + 150;
        int x1 = px + pw - 80;
        int sh = Math.max(4, rh / 3);
        int trackY = y + (rh - sh) / 2;
        g.fill(x0, trackY, x1, trackY + sh, 0x40FFFFFF);
        float frac = Mth.clamp((value - lo) / (hi - lo), 0f, 1f);
        int fill = Math.round((x1 - x0) * frac);
        if (fill > 0) {
            g.fill(x0, trackY, x0 + fill, trackY + sh, SAOConfig.accent());
        }
        int hx = x0 + fill;
        int d = Math.max(10, rh - 2);
        // fill() 会关掉混合,按钮贴图边缘的半透明像素需要混合才不致发黑
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        if (dragRow == i) {
            setTint(SAOConfig.accent(), 1f);
            g.blit(TEX_BTN, hx - d / 2, y + rh / 2 - d / 2, 0, 0, d, d, d, d);
            shaderAlpha(1f);
        } else {
            g.blit(TEX_BTN, hx - d / 2, y + rh / 2 - d / 2, 0, 0, d, d, d, d);
        }
        g.drawString(this.font, text, x1 + 8, y + (rh - 8) / 2, TEXT_GRAY, false);
    }

    private void renderToggle(GuiGraphics g, int y, String label, boolean on) {
        int rh = rowH();
        g.drawString(this.font, label, panelX() + 12, y + (rh - 8) / 2, TEXT_WHITE, false);
        String s = tr(on ? "saomenu.config.on" : "saomenu.config.off");
        int tx = panelX() + panelW() - 12 - this.font.width(s);
        g.drawString(this.font, s, tx, y + (rh - 8) / 2, on ? SAOConfig.accent() : TEXT_GRAY, false);
    }

    private void renderButton(GuiGraphics g, int x, int y, int w, String label, boolean hover) {
        int h = Math.min(20, rowH() - 4);
        int by = y + (rowH() - h) / 2;
        g.fill(x, by, x + w, by + h, hover ? lighten(SAOConfig.accent()) : SAOConfig.accent());
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2,
                by + (h - 8) / 2, TEXT_ON_ACCENT, false);
    }

    // ------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        for (int i = 0; i < ROWS; i++) {
            if (!rowHovered(i, mx, my)) {
                continue;
            }
            if (i < SLIDERS) {
                dragRow = i;
                applySlider(i, mx);
            } else if (i < SLIDERS + TOGGLES) {
                toggle(i);
            } else if (i == THEME_HUES) {
                int pw = panelW();
                int bw = (pw - 150 - 24 - 2 * 6) / 3;
                float[] hues = {41.44f, 202f, 355f};
                for (int t = 0; t < 3; t++) {
                    int bx = panelX() + 150 + t * (bw + 6);
                    if (mx >= bx && mx < bx + bw) {
                        SAOConfig.setAccentHue(hues[t]);
                        saveNow();
                        return true;
                    }
                }
            } else {
                int pw = panelW();
                int bw = (pw - 44) / 2;
                if (mx < panelX() + 12 + bw) {
                    SAOConfig.reset();
                    playUi();
                } else {
                    onClose();
                    return true;
                }
            }
            playUi();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragRow >= 0 && dragRow < SLIDERS) {
            applySlider(dragRow, (int) mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragRow = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applySlider(int i, int mx) {
        int x0 = panelX() + 150;
        int x1 = panelX() + panelW() - 80;
        float frac = Mth.clamp((mx - x0) / (float) (x1 - x0), 0f, 1f);
        switch (i) {
            case 0 -> SAOConfig.setAnchorX(SAOConfig.ANCHOR_MIN + frac * (SAOConfig.ANCHOR_MAX - SAOConfig.ANCHOR_MIN));
            case 1 -> SAOConfig.setAnchorY(SAOConfig.ANCHOR_MIN + frac * (SAOConfig.ANCHOR_MAX - SAOConfig.ANCHOR_MIN));
            case 2 -> SAOConfig.setMenuScale(SAOConfig.SCALE_MIN + frac * (SAOConfig.SCALE_MAX - SAOConfig.SCALE_MIN));
            case 3 -> SAOConfig.setBobAmp(SAOConfig.BOB_MIN + frac * (SAOConfig.BOB_MAX - SAOConfig.BOB_MIN));
            case 4 -> SAOConfig.setDeathShatterDensity(
                    SAOConfig.SHATTER_MIN + frac * (SAOConfig.SHATTER_MAX - SAOConfig.SHATTER_MIN));
            case 5 -> SAOConfig.setAccentHue(frac * 360f);
        }
        saveNow();
    }

    private void toggle(int i) {
        switch (i) {
            case 6 -> SAOConfig.setSounds(!SAOConfig.sounds());
            case 7 -> SAOConfig.setHideHotbar(!SAOConfig.hideHotbar());
            case 8 -> SAOConfig.setShowHud(!SAOConfig.showHud());
            case 9 -> SAOConfig.setSaoToasts(!SAOConfig.saoToasts());
            case 10 -> SAOConfig.setShowClock(!SAOConfig.showClock());
            case 11 -> SAOConfig.setClock24h(!SAOConfig.clock24h());
            case 12 -> SAOConfig.setClockDate(!SAOConfig.clockDate());
            case 13 -> SAOConfig.setShowWelcome(!SAOConfig.showWelcome());
            case 14 -> SAOConfig.setDeathShatter(!SAOConfig.deathShatter());
            case 15 -> SAOConfig.setShowAvatar(!SAOConfig.showAvatar());
            case 16 -> SAOConfig.setShowTargetBar(!SAOConfig.showTargetBar());
            case 17 -> SAOConfig.setShowDamageNumbers(!SAOConfig.showDamageNumbers());
        }
        saveNow();
    }

    private void saveNow() {
        Path p = SAOConfig.path();
        if (p == null) {
            p = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("saomenu.json");
        }
        SAOConfig.save(p);
    }

    private static void setTint(int argb, float alpha) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                Mth.clamp(alpha, 0f, 1f));
    }

    private static void shaderAlpha(float a) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(a, 0f, 1f));
    }

    private static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 30);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 30);
        int b = Math.min(255, (argb & 0xFF) + 30);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void playUi() {
        if (SAOConfig.sounds()) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SAOMenuPlatform.clickSound(), 1.0F));
        }
    }

    @Override
    public void onClose() {
        saveNow();
        if (this.minecraft != null) {
            this.minecraft.setScreen(lastScreen);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // O 键层级返回(回到菜单界面)
        if (keyCode == GLFW.GLFW_KEY_O) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private String pct(float v) {
        return String.format("%.1f%%", v * 100f);
    }
}
