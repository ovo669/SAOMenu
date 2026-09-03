package com.sao.saomenu.forge;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.party.SAONetwork;
import com.sao.saomenu.party.SAOTeamManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge 入口。在模组构造期注册音效、粒子类型与组队网络消息;
 * 客户端按键由 {@link SAOMenuForgeClient} 在 FMLClientSetupEvent 里注册。
 */
@Mod(SAOMenu.MOD_ID)
public class SAOMenuForge {

    public SAOMenuForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        SAOMenuForgeSounds.SOUND_EVENTS.register(modBus);
        SAOMenuForgeParticles.PARTICLE_TYPES.register(modBus);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SAONetwork::init);
    }

    /** 玩家退网:清掉挂在他名下的邀请会话,并让他离队(同步给剩余队员)。 */
    @Mod.EventBusSubscriber(modid = SAOMenu.MOD_ID)
    private static final class ServerEvents {

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                SAOTeamManager.clearPending(sp.getUUID());
                if (SAOTeamManager.teamOf(sp.getServer(), sp) != null) {
                    SAOTeamManager.handleLeave(sp.getServer(), sp);
                }
            }
        }
    }
}
