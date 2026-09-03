package com.sao.saomenu.forge;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.client.SAOHud;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge HUD 钩子:每帧在原版 HUD 之后绘制 SAO 血条板与圆点物品栏。
 */
@Mod.EventBusSubscriber(modid = SAOMenu.MOD_ID, value = Dist.CLIENT)
public final class SAOMenuForgeHud {

    private SAOMenuForgeHud() {
    }

    @SubscribeEvent
    public static void onHudRender(RenderGuiEvent.Post event) {
        SAOHud.render(event.getGuiGraphics(), Minecraft.getInstance());
    }
}
