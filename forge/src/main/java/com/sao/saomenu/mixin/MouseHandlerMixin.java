package com.sao.saomenu.mixin;

import com.sao.saomenu.client.SAOFreeLook;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alt 自由观察的鼠标输入。
 *
 * <ul>
 *   <li>滚轮:自由观察激活时缩放相机距离,吞掉事件(不切快捷栏)</li>
 *   <li>turnPlayer 头/尾:把鼠标转向增量从玩家朝向转存到自由相机——
 *       玩家朝向保持不变,实现「相机与角色解耦」的自由观察</li>
 * </ul>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    /** 滚轮:自由观察激活时缩放相机距离,并吞掉事件(不切快捷栏)。 */
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void saomenu$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (SAOFreeLook.onMouseScroll(net.minecraft.client.Minecraft.getInstance(), vertical)) {
            ci.cancel();
        }
    }

    /** turnPlayer 头部:记录玩家本帧转向前的朝向。 */
    @Inject(method = "turnPlayer()V", at = @At("HEAD"))
    private void saomenu$beforeTurn(CallbackInfo ci) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        SAOFreeLook.beginTurnCapture(mc.player);
    }

    /** turnPlayer 尾部:旋转增量转存进自由相机,玩家朝向回滚。 */
    @Inject(method = "turnPlayer()V", at = @At("TAIL"))
    private void saomenu$afterTurn(CallbackInfo ci) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        SAOFreeLook.endTurnCapture(mc.player);
    }
}
