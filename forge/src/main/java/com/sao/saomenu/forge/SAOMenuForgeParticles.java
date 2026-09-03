package com.sao.saomenu.forge;

import com.sao.saomenu.SAOMenu;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 侧粒子类型注册:SAO 死亡碎裂的碎片与中心闪光。
 *
 * <p>{@code SimpleParticleType} 构造是 protected,只能通过继承旁路访问;
 * {@code overrideLimiter=false} 表示粒子受客户端「粒子」画质选项限制,
 * 大量碎片在低配机器上会被自动削减。</p>
 */
public final class SAOMenuForgeParticles {

    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, SAOMenu.MOD_ID);

    public static final RegistryObject<SimpleParticleType> SHARD =
            PARTICLE_TYPES.register("sao_shard", () -> new SimpleType(false));
    public static final RegistryObject<SimpleParticleType> GLOW =
            PARTICLE_TYPES.register("sao_glow", () -> new SimpleType(false));

    private SAOMenuForgeParticles() {
    }

    /** SimpleParticleType 的构造受保护,子类仅用于放开可见性。 */
    private static class SimpleType extends SimpleParticleType {
        SimpleType(boolean overrideLimiter) {
            super(overrideLimiter);
        }
    }
}
