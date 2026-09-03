package com.sao.saomenu.forge;

import com.sao.saomenu.SAOMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 侧音效注册,素材取自 SAO_Utils_2.2 的 Click.wav / Panel.wav。
 */
public final class SAOMenuForgeSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SAOMenu.MOD_ID);

    public static final RegistryObject<SoundEvent> LAUNCHER =
            SOUND_EVENTS.register("launcher", () -> SoundEvent.createVariableRangeEvent(id("launcher")));
    public static final RegistryObject<SoundEvent> CLICK =
            SOUND_EVENTS.register("click", () -> SoundEvent.createVariableRangeEvent(id("click")));
    public static final RegistryObject<SoundEvent> PANEL =
            SOUND_EVENTS.register("panel", () -> SoundEvent.createVariableRangeEvent(id("panel")));
    public static final RegistryObject<SoundEvent> ALERT =
            SOUND_EVENTS.register("alert", () -> SoundEvent.createVariableRangeEvent(id("alert")));

    private SAOMenuForgeSounds() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SAOMenu.MOD_ID, path);
    }
}
