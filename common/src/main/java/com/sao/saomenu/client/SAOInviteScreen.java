package com.sao.saomenu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sao.saomenu.party.InviteResponseC2S;
import com.sao.saomenu.party.SAOClientPartyState;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * SAO 风格组队邀请窗(参照动画 Invite 卡):
 * alert.png 面板 + 标题 Invite + 「XX 邀请你组队」+ 官方蓝◎(接受)/粉✕(拒绝)圆钮。
 *
 * <p>翻转入场(easeOutBack 压 Y 轴);超过邀请有效期自动关闭;
 * ESC 等价拒绝;不暂停游戏。任何界面打开时收到邀请都会置顶弹出。</p>
 */
public final class SAOInviteScreen extends Screen {

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(com.sao.saomenu.SAOMenu.MOD_ID, "textures/gui/" + name);
    }

    private static final ResourceLocation TEX_ALERT = tex("alert.png");
    private static final ResourceLocation TEX_OK = tex("btn_ok.png");
    private static final ResourceLocation TEX_OK_HOVER = tex("btn_ok_hover.png");
    private static final ResourceLocation TEX_CANCEL = tex("btn_cancel.png");
    private static final ResourceLocation TEX_CANCEL_HOVER = tex("btn_cancel_hover.png");

    private static final int TEXT_DARK = 0xFF3C3C3D;

    /** 邀请人名字。 */
    private final String inviterName;
    private final long openedAt;

    public SAOInviteScreen(String inviterName) {
        super(Component.translatable("saomenu.invite.title"));
        this.inviterName = inviterName;
        this.openedAt = Util.getMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 弹窗矩形(alert.png 350x253 比例,居中)。 */
    private MenuLayout.Rect dialogRect() {
        int w = Math.min(280, this.width - 20);
        int h = Math.round(w * 253f / 350f);
        return new MenuLayout.Rect((this.width - w) / 2, (this.height - h) / 2 - 10, w, h);
    }

    /** 圆钮直径与圆心(alert 面板 1/4 与 3/4 宽、80% 高处)。 */
    private static final int BTN_D = 30;

    private int btn1x(MenuLayout.Rect at) {
        return at.x() + at.w() / 4 - BTN_D / 2;
    }

    private int btn2x(MenuLayout.Rect at) {
        return at.x() + at.w() * 3 / 4 - BTN_D / 2;
    }

    private int btnY(MenuLayout.Rect at) {
        return at.y() + Math.round(at.h() * 0.78f) - BTN_D / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 超时自动关闭(视为忽略,不发应答)
        if (Util.getMillis() - openedAt > SAOTeamManagerTimeout()) {
            SAOClientPartyState.clearInvite();
            this.onClose();
            return;
        }
        renderBackground(g);

        MenuLayout.Rect at = dialogRect();
        float p = Mth.clamp((Util.getMillis() - openedAt) / 160f, 0f, 1f);
        float s = 0.1f + 0.9f * easeOutBack(p);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(this.width / 2f, this.height / 2f, 0);
        pose.scale(1f, s, 1f);
        pose.translate(-this.width / 2f, -this.height / 2f, 0);

        // 阴影 + 面板
        g.fill(at.x() + 3, at.y() + 3, at.x() + at.w() + 3, at.y() + at.h() + 3, 0x6E303030);
        RenderSystem.enableBlend();
        g.blit(TEX_ALERT, at.x(), at.y(), 0, 0, at.w(), at.h(), at.w(), at.h());
        RenderSystem.disableBlend();

        Font f = this.font;
        // 标题:Invite(SAO 原窗标题)
        String title = tr("saomenu.invite.title");
        g.drawString(f, title, at.centerX() - f.width(title) / 2, at.y() + 12,
                mulAlpha(TEXT_DARK, 1f), false);
        // 正文:邀请人 + 询问
        String msg = tr("saomenu.invite.msg", inviterName);
        g.drawString(f, msg, at.centerX() - f.width(msg) / 2,
                at.y() + Math.round(at.h() * 0.42f), mulAlpha(TEXT_DARK, 1f), false);

        // 官方圆钮:蓝◎接受 / 粉✕拒绝(悬停换亮版贴图)
        boolean h1 = inBtn(btn1x(at), btnY(at), mouseX, mouseY);
        boolean h2 = inBtn(btn2x(at), btnY(at), mouseX, mouseY);
        int d = BTN_D;
        RenderSystem.enableBlend();
        g.blit(h1 ? TEX_OK_HOVER : TEX_OK, btn1x(at), btnY(at), 0, 0, d, d, d, d);
        g.blit(h2 ? TEX_CANCEL_HOVER : TEX_CANCEL, btn2x(at), btnY(at), 0, 0, d, d, d, d);
        RenderSystem.disableBlend();

        pose.popPose();
    }

    private static long SAOTeamManagerTimeout() {
        return com.sao.saomenu.party.SAOTeamManager.INVITE_TIMEOUT_MS;
    }

    private boolean inBtn(int bx, int by, int mx, int my) {
        return mx >= bx - 2 && mx < bx + BTN_D + 2 && my >= by - 2 && my < by + BTN_D + 2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        MenuLayout.Rect at = dialogRect();
        if (inBtn(btn1x(at), btnY(at), mx, my)) {
            respond(true);
            return true;
        }
        if (inBtn(btn2x(at), btnY(at), mx, my)) {
            respond(false);
            return true;
        }
        // 点弹窗外 = 忽略(不发应答,保留服务端会话到超时)
        if (mx < at.x() || mx >= at.x() + at.w() || my < at.y() || my >= at.y() + at.h()) {
            playClick();
            SAOClientPartyState.clearInvite();
            this.onClose();
            return true;
        }
        return true;
    }

    private void respond(boolean accept) {
        playClick();
        new InviteResponseC2S(accept).sendToServer();
        SAOClientPartyState.clearInvite();
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC = 拒绝(SAO 原作手势:拒绝也要明确表态)
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            respond(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    // ---------------------------------------------------------------- 工具(与 SAOMenuScreen 同款)

    private String tr(String key, Object... args) {
        String s = Component.translatable(key).getString();
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    private static float easeOutBack(float t) {
        float u = t - 1f;
        return 1f + 2.70158f * u * u * u + 1.70158f * u * u;
    }

    private void playClick() {
        if (SAOConfig.sounds()) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    com.sao.saomenu.SAOMenuPlatform.clickSound(), 1.0F));
        }
    }

    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        return (Math.round(a * Mth.clamp(factor, 0f, 1f)) << 24) | rgb;
    }
}
