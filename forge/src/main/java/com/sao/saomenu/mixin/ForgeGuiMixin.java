package com.sao.saomenu.mixin;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.client.SAOConfig;
import com.sao.saomenu.client.SAOHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 的 {@link ForgeGui} 把原版 Gui.render 的 HUD 段拆成了独立方法
 * (renderHealth/renderArmor/renderFood/renderAir/renderHealthMount),
 * 主 render 只调用这些拆分方法,原版 {@code renderPlayerHealth} 不会再被
 * 调用——所以 GuiMixin 里对 renderPlayerHealth 的取消在 Forge 上是空操作,
 * 血条必须在这里按拆分方法逐个取消。
 *
 * <p>饥饿条取消后由 {@link SAOHud#renderVanillaFoodCentered} 居中代画
 * (含氧气泡);renderAir 一并取消避免氧气泡画在旧位置错行。
 * 这些方法都是 Forge 自己新增的,名字不参与混淆,注解无需重映射。</p>
 */
@Mixin(ForgeGui.class)
public class ForgeGuiMixin {

    private static boolean saomenu$loggedFoodHook;

    @Inject(method = "renderHealth(IILnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void saomenu$hideHealth(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (SAOConfig.hideVanillaHealth()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmor(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void saomenu$hideArmor(GuiGraphics guiGraphics, int width, int height, CallbackInfo ci) {
        if (SAOConfig.hideVanillaHealth()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHealthMount(IILnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void saomenu$hideMountHealth(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (SAOConfig.hideVanillaHealth()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFood(IILnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void saomenu$centerFood(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!SAOConfig.hideVanillaHealth()) {
            return;
        }
        ci.cancel();
        if (!saomenu$loggedFoodHook) {
            saomenu$loggedFoodHook = true;
            SAOMenu.LOGGER.info("[SAOMenu] ForgeGui.renderFood 已接管,饥饿值居中");
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            SAOHud.renderVanillaFoodCentered(guiGraphics, player);
        }
    }

    @Inject(method = "renderAir(IILnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void saomenu$relocateAir(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        // 氧气泡由 renderFood 的接管路径一起画(居中),这里只取消原位绘制
        if (SAOConfig.hideVanillaHealth()) {
            ci.cancel();
        }
    }
}
