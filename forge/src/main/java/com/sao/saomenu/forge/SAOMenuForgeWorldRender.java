package com.sao.saomenu.forge;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.client.SAOMenu3DPanel;
import com.sao.saomenu.client.SAOTargetBar3D;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 世界渲染钩子:在实体绘制完成后叠加 SAO 3D 环绕目标血条。
 *
 * <p>选 AFTER_ENTITIES 而非 AFTER_LEVEL,是为了让血条早于天气/粒子叠加,
 * 同时此时 PoseStack 仍是纯相机旋转矩阵(未叠加额外变换)。</p>
 */
@Mod.EventBusSubscriber(modid = SAOMenu.MOD_ID, value = Dist.CLIENT)
public final class SAOMenuForgeWorldRender {

    private SAOMenuForgeWorldRender() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        // AFTER_ENTITIES 触发时实体几何只是写进了缓冲、尚未上屏:
        // 必须先把实体批全部刷出,深度缓冲里才有实体。
        // 否则环带先画、实体后画,整只生物会盖在环带和血量带上(穿透/看不见)
        mc.renderBuffers().bufferSource().endBatch();
        SAOTargetBar3D.render(mc, event.getPoseStack(),
                mc.renderBuffers().bufferSource(), event.getPartialTick());
        SAOMenu3DPanel.renderBoard(mc, event.getPoseStack(), event.getPartialTick());
        mc.renderBuffers().bufferSource().endBatch();
    }
}
