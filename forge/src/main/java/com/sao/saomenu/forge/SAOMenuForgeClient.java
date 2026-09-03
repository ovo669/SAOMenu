package com.sao.saomenu.forge;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.client.SAOKeybinds;
import com.sao.saomenu.client.SAOMenu3DPanel;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge 客户端初始化:按键必须在 RegisterKeyMappingsEvent 注册。
 */
@Mod.EventBusSubscriber(modid = SAOMenu.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SAOMenuForgeClient {

    private SAOMenuForgeClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SAOKeybinds.OPEN_MENU);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SAOKeybinds.registerTickHandler();
    }

    /** 菜单屏渲染完成后:把主帧缓冲 blit 到世界菜单板的备用纹理。 */
    @Mod.EventBusSubscriber(modid = SAOMenu.MOD_ID, value = Dist.CLIENT)
    public static final class ScreenHooks {
        private ScreenHooks() {
        }

        @SubscribeEvent
        public static void onScreenPostRender(ScreenEvent.Render.Post event) {
            if (event.getScreen() instanceof com.sao.saomenu.client.SAOMenuScreen) {
                SAOMenu3DPanel.onMenuScreenRendered(Minecraft.getInstance());
            }
        }
    }
}
