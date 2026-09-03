package com.sao.saomenu.mixin;

import com.sao.saomenu.client.SAOConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 开启死亡碎裂时隐藏原版死亡动画:实体在死亡当帧起不再绘制,
 * 视觉上就是「身体直接碎成蓝片」而不是先倒地变红再消失。
 *
 * <p>玩家自己不受影响(第一人称看不到自己尸体,第三人称仍需要正常表现);
 * 末影龙走 EnderDragonRenderer(不继承 LivingEntityRenderer),不在此列。</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingDeathRenderMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void saomenu$hideDyingBody(LivingEntity entity, float entityYaw, float partialTick,
                                       PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, CallbackInfo ci) {
        if (SAOConfig.deathShatter() && entity.deathTime > 0 && !(entity instanceof Player)) {
            ci.cancel();
        }
    }
}
