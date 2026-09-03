package com.sao.saomenu.forge;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.client.SAOShardParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 侧死亡碎裂粒子工厂注册(客户端 MOD 总线)。
 */
@Mod.EventBusSubscriber(modid = SAOMenu.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SAOMenuForgeParticleProviders {

    private SAOMenuForgeParticleProviders() {
    }

    @SubscribeEvent
    public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(SAOMenuForgeParticles.SHARD.get(),
                SAOShardParticle.ShardProvider::new);
        event.registerSpriteSet(SAOMenuForgeParticles.GLOW.get(),
                SAOShardParticle.GlowProvider::new);
    }
}
