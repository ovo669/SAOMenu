package com.sao.saomenu;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;

import java.util.Map;

/**
 * 平台桥接:音效事件与粒子类型在 Forge/Fabric 各自注册后从这里取用。
 */
public class SAOMenuPlatform {

    @ExpectPlatform
    public static SoundEvent launcherSound() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static SoundEvent clickSound() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static SoundEvent panelSound() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static SoundEvent alertSound() {
        throw new AssertionError();
    }

    /** 已解锁成就进度表(平台侧通过 accessor mixin 读取私有字段)。 */
    @ExpectPlatform
    public static Map<Advancement, AdvancementProgress> advancementProgress(ClientAdvancements ca) {
        throw new AssertionError();
    }

    /** SAO 死亡碎裂的碎片粒子类型。 */
    @ExpectPlatform
    public static SimpleParticleType shardParticle() {
        throw new AssertionError();
    }

    /** SAO 死亡碎裂的中心闪光粒子类型。 */
    @ExpectPlatform
    public static SimpleParticleType glowParticle() {
        throw new AssertionError();
    }
}
