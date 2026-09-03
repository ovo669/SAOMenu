package com.sao.saomenu.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Alt 自由观察:按住 Alt 临时切到第三人称,<strong>相机与玩家朝向解耦</strong>——
 * 移动鼠标只转自由相机角度,玩家实际朝向保持不变,松开 Alt 镜头弹回原视角。
 *
 * <h2>操作</h2>
 * <ul>
 *   <li>按住 Alt:进入自由观察(第三人称,鼠标自由转动相机)</li>
 *   <li>自由观察期间按鼠标中键:锁定/解锁当前视角(锁定时松开 Alt 也保持)</li>
 *   <li>自由观察期间滚轮:拉远/拉近相机距离(1.5 ~ 12 格)</li>
 * </ul>
 *
 * <h2>解耦原理</h2>
 * <p>原版相机方向恒等于玩家 yaw/pitch,直接转视角必然转动角色。
 * {@code MouseHandlerMixin} 在 turnPlayer 的头/尾各注入一次:头部记录玩家
 * 当前朝向,尾部把本帧产生的旋转增量转存进 {@code camYaw/camPitch} 并立刻
 * 把玩家朝向回滚——玩家的朝向从未改变,旋转全部进了自由相机;
 * {@code CameraMixin} 在 Camera.setup 末尾用自由相机角度重设相机旋转与位置。</p>
 */
public final class SAOFreeLook {

    /** 相机距离范围(格)。 */
    public static final float DIST_MIN = 1.5f;
    public static final float DIST_MAX = 12f;
    /** 默认距离(原版第三人称 4 格)。 */
    public static final float DIST_DEF = 4f;

    private static boolean active;
    /** 中键锁定:激活状态下松开 Alt 仍保持自由观察。 */
    private static boolean locked;
    /** 进入前的相机类型(松开时恢复)。 */
    private static CameraType savedCamera = CameraType.FIRST_PERSON;

    /** 自由相机角度(与玩家朝向解耦)。 */
    private static float camYaw;
    private static float camPitch;
    /** 当前相机距离(滚轮调节)。 */
    private static float dist = DIST_DEF;

    /** turnPlayer 头部捕获的玩家朝向(尾部转存增量后回滚)。 */
    private static boolean capturing;
    private static float capYaw;
    private static float capPitch;
    /** 中键边沿检测(tick 轮询)。 */
    private static boolean middleWasDown;

    private SAOFreeLook() {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isLocked() {
        return locked;
    }

    public static float camYaw() {
        return camYaw;
    }

    public static float camPitch() {
        return camPitch;
    }

    public static float distance() {
        return dist;
    }

    /** 每客户端 tick 轮询(客户端 tick 钩子调用)。 */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            if (active) {
                restore(mc);
            }
            return;
        }
        long handle = mc.getWindow().getWindow();
        boolean alt = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

        // 中键锁定:tick 轮询边沿(不依赖事件注入,任何环境都可靠)
        boolean middle = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                == GLFW.GLFW_PRESS;
        if (active && middle && !middleWasDown) {
            locked = !locked;
        }
        middleWasDown = middle;

        if (!active) {
            if (alt) {
                enter(mc);
            }
            return;
        }
        // 已激活:锁定状态下保持;未锁定且 Alt 松开 → 退出
        if (!locked && !alt) {
            restore(mc);
        }
    }

    private static void enter(Minecraft mc) {
        active = true;
        locked = false;
        savedCamera = mc.options.getCameraType();
        camYaw = mc.player.getYRot();
        camPitch = mc.player.getXRot();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void restore(Minecraft mc) {
        active = false;
        locked = false;
        mc.options.setCameraType(savedCamera);
        // 玩家朝向从未被修改,无需回滚;相机自动回到玩家视角(镜头弹回)
    }

    /** turnPlayer 头部:记录玩家进入本帧转向前的朝向。 */
    public static void beginTurnCapture(net.minecraft.world.entity.player.Player p) {
        if (!active || p == null) {
            capturing = false;
            return;
        }
        capturing = true;
        capYaw = p.getYRot();
        capPitch = p.getXRot();
    }

    /** turnPlayer 尾部:把本帧旋转增量转存进自由相机,玩家朝向回滚。 */
    public static void endTurnCapture(net.minecraft.world.entity.player.Player p) {
        if (!capturing || p == null) {
            capturing = false;
            return;
        }
        capturing = false;
        float dYaw = Mth.wrapDegrees(p.getYRot() - capYaw);
        float dPitch = p.getXRot() - capPitch;
        // 立刻回滚玩家朝向——角色从未转向
        p.setYRot(capYaw);
        p.setXRot(capPitch);
        p.yRotO = capYaw;
        p.xRotO = capPitch;
        // 增量进自由相机
        camYaw = Mth.wrapDegrees(camYaw + dYaw);
        camPitch = Mth.clamp(camPitch + dPitch, -90f, 90f);
    }

    /** 中键:自由观察激活时切换锁定(事件路径,tick 轮询为主)。 */
    public static boolean onMouseClick(Minecraft mc, int button) {
        return active && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
    }

    /** 滚轮:自由观察激活时缩放相机距离。 */
    public static boolean onMouseScroll(Minecraft mc, double delta) {
        if (!active) {
            return false;
        }
        dist = Mth.clamp(dist - (float) delta * 1.2f, DIST_MIN, DIST_MAX);
        return true;
    }

    // ------------------------------------------------------------ 预览自检(GLFW 按键无法合成,直调状态机)

    /** 无头自检:等价于按住 Alt 进入。 */
    public static void debugEnterForPreview(Minecraft mc) {
        if (mc.player == null || active) {
            return;
        }
        enter(mc);
    }

    /** 无头自检:等价于按下中键(切换锁定)。 */
    public static void debugMiddleClickForPreview() {
        if (active) {
            locked = !locked;
        }
    }

    /** 无头自检:等价于解锁后松开 Alt 退出。 */
    public static void debugExitForPreview(Minecraft mc) {
        if (active) {
            locked = false;
            restore(mc);
        }
    }
}
