package com.sao.saomenu.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sao.saomenu.SAOMenu;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 打开菜单的按键(默认 O)与逐帧检测。
 *
 * <p>Forge 侧按键注册必须走原生的 {@code RegisterKeyMappingsEvent}(见
 * SAOMenuForgeClient);Architectury 的 {@link KeyMappingRegistry} 在
 * FMLClientSetupEvent 阶段调用会抛 "registered after event"。</p>
 */
public final class SAOKeybinds {

    public static final String CATEGORY = "key.categories." + SAOMenu.MOD_ID;

    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key." + SAOMenu.MOD_ID + ".open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private static boolean tickHooked = false;

    private SAOKeybinds() {
    }

    /** Fabric:初始化时注册按键 + 逐帧检测(Architectury 在 init 阶段可安全注册)。 */
    public static void register() {
        KeyMappingRegistry.register(OPEN_MENU);
        registerTickHandler();
    }

    /** Forge:按键由 RegisterKeyMappingsEvent 注册,这里只挂逐帧检测与预览钩子。 */
    public static void registerTickHandler() {
        if (tickHooked) {
            return;
        }
        tickHooked = true;
        // 加载客户端配置(锚点/缩放/浮动/音效/HUD),供布局与渲染读取
        SAOConfig.load(Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("saomenu.json"));
        ClientTickEvent.CLIENT_POST.register(client -> {
            // 进入世界检测:无世界→有世界时播放 SAO 欢迎动画
            SAOWelcome.clientTick(client);
            // Alt 自由观察逐 tick 轮询(进入/退出/锁定状态机)
            SAOFreeLook.tick(client);
            // 生物死亡检测:死亡当帧爆散蓝色碎片
            SAODeathEffect.clientTick(client);
            // 二刀流:装备包发出后延后几 tick 再切史诗战斗的战斗模式
            SAODualWield.tick();
            while (OPEN_MENU.consumeClick()) {
                if (client.player == null) {
                    continue;
                }
                if (client.screen == null) {
                    client.setScreen(new SAOMenuScreen());
                } else if (client.screen instanceof SAOMenuScreen) {
                    // 再按一次 O 关闭菜单(走关闭动画)
                    client.screen.onClose();
                } else if (client.screen instanceof SAOInventoryScreen
                        || client.screen instanceof SAOConfigScreen
                        || client.screen instanceof SAOSettingsScreen) {
                    // 模组界面内按 O:层级返回(配置→菜单,物品栏→游戏)
                    client.screen.onClose();
                }
            }
        });
        SAOMenuPreview.registerIfRequested();
    }
}
