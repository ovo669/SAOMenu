package com.sao.saomenu.mixin;

import com.sao.saomenu.client.SAOConfig;
import com.sao.saomenu.client.SAOHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 隐藏原版快捷栏与经验条:底部圆点即 SAO 物品栏,等级显示在血条板上;
 * 隐藏原版血条行(renderPlayerHealth 含血条/护甲),饥饿值居中代画
 * ({@link SAOHud#renderVanillaFoodCentered}),氧气泡保留。
 * 坐骑血条(renderVehicleHealth)是独立方法,不受影响。
 */
@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderHotbar(FLnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void saomenu$hideHotbar(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (SAOConfig.hideHotbar()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("HEAD"), cancellable = true)
    private void saomenu$hideExperience(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void saomenu$hideVanillaHealth(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!SAOConfig.hideVanillaHealth()) {
            return;
        }
        ci.cancel();
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            SAOHud.renderVanillaFoodCentered(guiGraphics,
                    net.minecraft.client.Minecraft.getInstance().player);
        }
    }
}
