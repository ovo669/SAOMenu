package com.sao.saomenu.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;

/**
 * SAO 菜单打开时的移动输入接管(由 {@code KeyboardInputMixin} 在原版
 * 读取按键之后调用):直接按 GLFW 原生按键状态改写 {@link Input} 字段,
 * 让玩家在非阻塞菜单开着时照常走/跳/潜行/疾跑。
 *
 * <p>键码解析:遍历 GLFW 键值空间用 {@code KeyMapping.matches} 匹配一次并缓存,
 * 不依赖 KeyMapping 的任何内部状态(因此与事件路径是否可靠无关)。</p>
 */
public final class SAOMenuMovement {

    private static final int[] CODES = new int[7];
    private static boolean resolved;

    private SAOMenuMovement() {
    }

    /** 由 Mixin 在 KeyboardInput.tick 末尾调用。 */
    public static void apply(Input input) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof SAOMenuScreen) || mc.player == null) {
            return;
        }
        SAOMenuScreen screen = (SAOMenuScreen) mc.screen;
        KeyMapping[] kms = screen.moveKeys();
        if (!resolved) {
            for (int code = 32; code <= 348; code++) {
                for (int i = 0; i < kms.length; i++) {
                    if (CODES[i] == 0 && kms[i].matches(code, 0)) {
                        CODES[i] = code;
                    }
                }
            }
            resolved = true;
        }
        if (screen.isMovementBlocked()) {
            // 确认弹窗/信息弹窗/关闭动画期间:移动输入清零
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
            input.forwardImpulse = 0f;
            input.leftImpulse = 0f;
            return;
        }
        long win = mc.getWindow().getWindow();
        // 方向与原版 KeyboardInput.tick 同号:前进 +1,左移 +1
        input.up = isDown(win, CODES[0]);
        input.down = isDown(win, CODES[1]);
        input.left = isDown(win, CODES[2]);
        input.right = isDown(win, CODES[3]);
        input.jumping = isDown(win, CODES[4]);
        input.shiftKeyDown = isDown(win, CODES[5]);
        input.forwardImpulse = (input.up ? 1f : 0f) - (input.down ? 1f : 0f);
        input.leftImpulse = (input.left ? 1f : 0f) - (input.right ? 1f : 0f);
        // 疾跑键:LocalPlayer.aiStep 直接读 keySprint.isDown(),
        // 由 SAOMenuScreen 的每帧 KeyMapping 轮询负责置位
    }

    private static boolean isDown(long window, int code) {
        return code != 0 && InputConstants.isKeyDown(window, code);
    }

    // ------------------------------------------------------------ 自动疾跑

    /** 自动疾跑正在程序性按住疾跑键(用于收手时只收回自己的强制)。 */
    private static boolean sprintForced;

    /**
     * 按住 W 自动疾跑:程序性按下疾跑键,让原版 aiStep 的全套疾跑条件
     * (饱食度 ≥ 6、撞墙停止、水面游泳疾跑、飞行疾跑)原样生效——
     * 本模组不直接改写玩家的 sprinting 标志。
     *
     * <p>只在「想跑但疾跑键没按下」与「不想跑但此前是本方法按下的」两个
     * 边沿触碰 KeyMapping,玩家物理按住疾跑键的状态不受影响;
     * 界面打开时 KeyMapping 已被 releaseAll,keyUp 读不到按下,
     * 因此菜单/背包打开时自动失效,关界面重新按住 W 即恢复。</p>
     *
     * <p>已知取舍:自动疾跑期间若玩家同时物理按着疾跑键并先松开 W,
     * 疾跑键会被收回一次,需要重新按一下疾跑键才能恢复手动疾跑。</p>
     */
    public static void autoSprint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        KeyMapping sprint = mc.options.keySprint;
        boolean want = SAOConfig.autoSprint() && mc.options.keyUp.isDown();
        if (want) {
            if (!sprint.isDown()) {
                sprint.setDown(true);
            }
            sprintForced = true;
        } else if (sprintForced) {
            if (sprint.isDown()) {
                sprint.setDown(false);
            }
            sprintForced = false;
        }
    }
}
