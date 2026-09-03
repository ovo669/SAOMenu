package com.sao.saomenu.mixin;

import com.sao.saomenu.client.SAOFreeLook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Alt 自由观察的相机摆放。
 *
 * <p>不手动摆相机(Camera.move 收的是相机局部空间坐标,手算世界向量会
 * 把相机甩到偏离角色的位置)——改为在原版 setup 内部就地改三个调用的入参:</p>
 * <ul>
 *   <li>setRotation(普通第三人称的初始旋转)的 yaw/pitch 两个入参
 *       → 换成自由相机角度,后续原版 move(-zoom,0,0) 沿该方向后退,
 *       相机恒以玩家眼部为圆心环绕</li>
 *   <li>getMaxZoom(4.0)(原版硬编码的第三人称距离)→ 换成滚轮调节值,
 *       并保留原版穿墙射线收紧</li>
 * </ul>
 *
 * <p><b>注入方式的两次教训:</b></p>
 * <ul>
 *   <li>不能用 @Redirect——它会把整条 INVOKE 指令换掉,处理方法签名必须带
 *       接收者,调用点参数表从 {@code (D)} 变成
 *       {@code (Lnet/minecraft/client/Camera;D)},别的模组对同一调用用
 *       @ModifyArg(index=0) 就会拿到 Camera 而不是 double,抛
 *       InvalidInjectionException(机械动力 6.0.8 的 client.CameraMixin
 *       正是这样被搞崩的)</li>
 *   <li>不能用 @ModifyArgs(复数)——它依赖 Mixin 在运行期动态生成的
 *       {@code org.spongepowered.asm.synthetic.args.Args} 类族,
 *       Forge 47.4.23 / ModLauncher 的模块类加载器加载不到内部类
 *       {@code Args$1},直接 NoClassDefFoundError</li>
 *   <li>只有 @ModifyArg(单数)既可叠加又零生成类:多个模组各改一次入参
 *       依次串联,互不破坏签名。改同一调用的多个参数就写多个 handler,
 *       用 index 区分</li>
 * </ul>
 */
@Mixin(Camera.class)
public class CameraMixin {

    /** 自由观察:初始相机旋转的 yaw 替换为自由相机角度(与玩家朝向解耦)。 */
    @ModifyArg(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0),
            index = 0)
    private float saomenu$freeLookYaw(float yaw) {
        return SAOFreeLook.isActive() ? SAOFreeLook.camYaw() : yaw;
    }

    /** 自由观察:初始相机旋转的 pitch 替换为自由相机角度。 */
    @ModifyArg(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0),
            index = 1)
    private float saomenu$freeLookPitch(float pitch) {
        return SAOFreeLook.isActive() ? SAOFreeLook.camPitch() : pitch;
    }

    /** 自由观察:第三人称后退距离替换为滚轮调节值(穿墙收紧保留)。 */
    @ModifyArg(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"),
            index = 0)
    private double saomenu$freeLookZoom(double desiredCameraDistance) {
        return SAOFreeLook.isActive() ? SAOFreeLook.distance() : desiredCameraDistance;
    }
}
