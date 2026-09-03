package com.sao.saomenu.mixin;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.client.SAOConfig;
import com.sao.saomenu.client.SAONotification;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 成就通知:把原版 AdvancementToast 替换为 SAO 风格通知横幅。
 * 开关由 SAOConfig.saoToasts 控制;其他 Toast(教程/系统/配方)不受影响。
 */
@Mixin(ToastComponent.class)
public class ToastMixin {

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void saomenu$replaceAdvancementToast(Toast toast, CallbackInfo ci) {
        if (!(toast instanceof AdvancementToast at) || !SAOConfig.saoToasts()) {
            return;
        }
        Advancement advancement = ((AdvancementToastAccessor) at).saomenu$advancement();
        if (advancement != null) {
            DisplayInfo display = advancement.getDisplay();
            if (display != null) {
                SAOMenu.LOGGER.info("[SAOMenu] advancement toast replaced: {}",
                        display.getTitle().getString());
                SAONotification.push(display.getTitle().getString(),
                        display.getDescription().getString(), display.getIcon());
            }
        }
        ci.cancel();
    }
}
