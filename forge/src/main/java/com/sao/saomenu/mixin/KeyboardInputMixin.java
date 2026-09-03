package com.sao.saomenu.mixin;

import com.sao.saomenu.client.SAOMenuMovement;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SAO 菜单打开时接管移动输入:原版 tick 读完按键后,
 * 由 {@link SAOMenuMovement} 按 GLFW 原生按键状态改写 Input 字段。
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void saomenu$applyMovement(boolean sneaking, float slowFallingMultiplier, CallbackInfo ci) {
        SAOMenuMovement.apply((Input) (Object) this);
    }
}
