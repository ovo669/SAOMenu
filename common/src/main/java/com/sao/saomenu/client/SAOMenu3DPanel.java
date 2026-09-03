package com.sao.saomenu.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 世界空间 SAO 菜单板:第一人称打开菜单时,角色面前浮现一块与玩家所见
 * <strong>一模一样</strong>的 SAO 菜单画面(F5 第三人称可见,参照原作
 * 「菜单浮现在角色面前」的演出)。
 *
 * <h2>实现</h2>
 * <ul>
 *   <li>菜单屏被原版渲染到主帧缓冲后(Forge {@code ScreenEvent.Render.Post} /
 *       Fabric {@code ScreenEvents.AFTER_RENDER}),用 {@code glBlitFramebuffer}
 *       把主帧缓冲 GPU→GPU 拷贝到一张全尺寸备用纹理——不自己搭离屏渲染管线,
 *       菜单内容(字体/图标/动画/悬停高亮)与玩家所见天然一致</li>
 *   <li>世界渲染阶段:把备用纹理贴到角色面前的板上,
 *       UV 只取菜单内容包围盒(玩家卡+按钮列+菜单项),板后衬不透明深色背板</li>
 *   <li>板锚定玩家身体朝向,随菜单开合缩放弹出,待机时上下轻浮;
 *       第一人称不渲染(全屏 HUD 接管,且面板贴脸会被透视放大)</li>
 *   <li>纯本地渲染:同客户端第三人称可见;其他真实玩家可见需网络同步(暂未实现)</li>
 * </ul>
 */
public final class SAOMenu3DPanel {

    /** 板世界高度(格,缩放=1、动画完成时)。 */
    private static final float BOARD_H = 1.62f;
    /** 板离眼睛的水平距离(格)。 */
    private static final float BOARD_DIST = 1.35f;
    private static RenderTarget boardTarget;
    private static int boardW = -1;
    private static int boardH = -1;

    private SAOMenu3DPanel() {
    }

    /**
     * 菜单屏渲染完成后调用(Forge {@code ScreenEvent.Render.Post} /
     * Fabric {@code ScreenEvents.AFTER_RENDER}):把主帧缓冲 blit 到备用纹理。
     * 先 endBatch 冲出 GuiGraphics 的批,保证菜单像素已全部上屏。
     */
    public static void onMenuScreenRendered(Minecraft mc) {
        if (!SAOConfig.thirdPersonMenu() || mc.player == null) {
            return;
        }
        if (!(mc.screen instanceof SAOMenuScreen)) {
            return;
        }
        mc.renderBuffers().bufferSource().endBatch();
        RenderTarget main = mc.getMainRenderTarget();
        ensureBoard(main.width, main.height);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, boardTarget.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height,
                0, 0, boardTarget.width, boardTarget.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        // 恢复绑定为主帧缓冲
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
    }

    /** 备用纹理:尺寸随主帧缓冲变化重建。 */
    private static void ensureBoard(int w, int h) {
        if (boardTarget != null && boardW == w && boardH == h) {
            return;
        }
        if (boardTarget != null) {
            boardTarget.destroyBuffers();
        }
        boardTarget = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        boardTarget.setFilterMode(GL11.GL_LINEAR);
        boardW = w;
        boardH = h;
    }

    /** 世界渲染阶段调用(实体绘制后):把上一帧的菜单画面贴到角色面前的板。 */
    public static void renderBoard(Minecraft mc, PoseStack pose, float partialTick) {
        if (!SAOConfig.thirdPersonMenu() || mc.player == null || mc.level == null
                || boardTarget == null) {
            return;
        }
        // 第一人称由全屏 HUD 菜单接管;面板贴脸会被透视放大成一堵墙
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            return;
        }
        if (!(mc.screen instanceof SAOMenuScreen menu)) {
            return;
        }
        float a = menu.worldMenuAlpha();
        if (a <= 0.02f) {
            return;
        }
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();

        // 内容包围盒(UV):玩家卡 + 按钮列 + 菜单项列;底部圆点栏(0.972h)裁在板外。
        // 主帧缓冲 y=0 是 GL 底(屏幕底),GUI y=0(顶)在纹理 v=1
        float contentX1 = MenuLayout.childColumnX(guiW, guiH) + Math.round(guiH * 0.20f);
        float contentY1 = guiH * 0.93f;
        float u1 = Mth.clamp(contentX1 / guiW, 0.05f, 1f);
        float vTop = 1f;
        float vBottom = Mth.clamp(1f - contentY1 / guiH, 0f, 1f);

        LocalPlayer p = mc.player;
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        float bodyRot = Mth.rotLerp(partialTick, p.yBodyRotO, p.yBodyRot);
        double rad = Math.toRadians(bodyRot);
        double fwdX = -Mth.sin((float) rad);
        double fwdZ = Mth.cos((float) rad);
        Vec3 eye = p.getPosition(partialTick).add(0, p.getEyeHeight() * 0.92, 0);
        Vec3 anchor = eye.add(fwdX * BOARD_DIST, -0.36, fwdZ * BOARD_DIST);

        long now = Util.getMillis();
        float bob = Mth.sin(now / 900f * Mth.TWO_PI) * 0.02f;
        float scale = 0.35f + 0.65f * easeOutBack(a);
        float hW = BOARD_H * scale * 0.5f;
        float wW = hW * (u1 / Math.max(0.05f, vTop - vBottom));
        // 纹理面沿朝向前移一点点,避免与背板 z-fighting
        float texZ = 0.006f;

        pose.pushPose();
        pose.translate(anchor.x - camPos.x, anchor.y - camPos.y, anchor.z - camPos.z);
        // 板面垂直于身体朝向:局部 +z 旋到玩家面前方向,板随角色转身而转
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-bodyRot));
        Matrix4f m = pose.last().pose();

        // 显式双面渲染:不关剔除时,观察者在板背面一侧会因背面剔除只看到
        // 深色背板(表现为「透明黑色板块」);渲染器残留的 cull 状态在不同
        // 环境(OptiFine 等)下不同,不能依赖默认值。显式开深度测试同理。
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        // 菜单画面纹理:不透明绘制,无任何背板。
        // blit 拷贝的主帧缓冲 alpha 在部分环境(OptiFine 等)为 0——开混合时
        // 菜单 RGB 会被 0 alpha 混掉,只剩深色背板(「透明黑色板块」)。
        // 纹理 RGB 本身就是玩家所见的完整合成画面(世界+菜单),直接不透明贴出,
        // 对 alpha 与剔除状态都免疫;开合淡入用 RGB 缩放近似(150ms,不可感知)
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, boardTarget.getColorTextureId());
        float fade = Mth.clamp(a, 0f, 1f);
        RenderSystem.setShaderColor(fade, fade, fade, 1f);
        RenderSystem.disableBlend();
        com.mojang.blaze3d.vertex.BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        texVert(bb, m, -wW, hW, texZ, 0f, vTop);
        texVert(bb, m, -wW, -hW, texZ, 0f, vBottom);
        texVert(bb, m, wW, -hW, texZ, u1, vBottom);
        texVert(bb, m, wW, hW, texZ, u1, vTop);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.enableCull();
        pose.popPose();
    }

    private static void texVert(com.mojang.blaze3d.vertex.BufferBuilder bb, Matrix4f m,
                                float x, float y, float z, float u, float v) {
        bb.vertex(m, x, y, z).uv(u, v).color(1f, 1f, 1f, 1f).endVertex();
    }

    private static float easeOutBack(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float c1 = 1.70158f * 1.25f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
