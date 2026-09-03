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
}
