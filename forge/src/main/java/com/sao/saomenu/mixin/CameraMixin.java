package com.sao.saomenu.mixin;

import com.sao.saomenu.client.SAOFreeLook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Alt 自由观察的相机摆放。
 *
 * <p>不手动摆相机(Camera.move 收的是相机局部空间坐标,手算世界向量会
 * 把相机甩到偏离角色的位置)——改为重定向原版 setup 内部的两个调用:</p>
 * <ul>
 *   <li>setRotation(普通第三人称的初始旋转)→ 换成自由相机角度,
 *       后续原版 move(-zoom,0,0) 沿该方向后退,相机恒以玩家眼部为圆心环绕</li>
 *   <li>getMaxZoom(4.0)(原版硬编码的第三人称距离)→ 换成滚轮调节值,
 *       并保留原版穿墙射线收紧</li>
 * </ul>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract double getMaxZoom(double desiredCameraDistance);

    /** 自由观察:初始相机旋转替换为自由相机角度(与玩家朝向解耦)。 */
    @Redirect(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
    private void saomenu$freeLookRotation(Camera camera, float yaw, float pitch) {
        // handler 的 receiver 就是本 mixin 实例(mixin 与目标类合并),直接走 shadow
        setRotation(SAOFreeLook.isActive() ? SAOFreeLook.camYaw() : yaw,
                SAOFreeLook.isActive() ? SAOFreeLook.camPitch() : pitch);
    }

    /** 自由观察:第三人称后退距离替换为滚轮调节值(穿墙收紧保留)。 */
    @Redirect(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"))
    private double saomenu$freeLookZoom(Camera camera, double desiredCameraDistance) {
        return getMaxZoom(SAOFreeLook.isActive() ? SAOFreeLook.distance() : desiredCameraDistance);
    }
}
