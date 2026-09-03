package com.sao.saomenu.forge;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.client.SAOFreeLook;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 鼠标输入钩子:Alt 自由观察的中键锁定与滚轮缩放。
 */
@Mod.EventBusSubscriber(modid = SAOMenu.MOD_ID, value = Dist.CLIENT)
public final class SAOMenuForgeMouse {

    private SAOMenuForgeMouse() {
    }

    @SubscribeEvent
    public static void onClick(InputEvent.MouseButton e) {
        if (SAOFreeLook.onMouseClick(Minecraft.getInstance(), e.getButton())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        if (SAOFreeLook.onMouseScroll(Minecraft.getInstance(), e.getScrollDelta())) {
            e.setCanceled(true);
        }
    }
}
