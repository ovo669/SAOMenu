package com.sao.saomenu.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 死亡碎裂编排回归:碎片数量随体型缩放并钳制、爆散速度方向、补碎节奏。
 */
class SAODeathEffectTest {

    @AfterEach
    void restoreDefaults() {
        SAOConfig.reset();
    }

    @Test
    void shardCountScalesWithBodySize() {
        int chicken = SAODeathEffect.shardCount(0.4f, 0.7f, 1f);
        int zombie = SAODeathEffect.shardCount(0.6f, 1.95f, 1f);
        int dragon = SAODeathEffect.shardCount(16f, 8f, 1f);
        assertTrue(chicken <= zombie, "体型更大的僵尸碎片不应少于鸡");
        assertTrue(zombie < dragon, "巨物碎片应更多");
    }

    @Test
    void shardCountStaysWithinBounds() {
        assertEquals(SAODeathEffect.MIN_SHARDS, SAODeathEffect.shardCount(0.05f, 0.05f, 0.2f),
                "极小体型钳到下限");
        assertEquals(SAODeathEffect.MAX_SHARDS, SAODeathEffect.shardCount(16f, 8f, 3f),
                "巨物 + 高密度钳到上限");
    }

    @Test
    void densityMultiplierIsClamped() {
        int low = SAODeathEffect.shardCount(0.6f, 1.95f, 0.2f);
        int normal = SAODeathEffect.shardCount(0.6f, 1.95f, 1f);
        int high = SAODeathEffect.shardCount(0.6f, 1.95f, 2.5f);
        assertTrue(low <= normal && normal <= high, "密度越高碎片越多");
        assertEquals(SAODeathEffect.shardCount(0.6f, 1.95f, 3f),
                SAODeathEffect.shardCount(0.6f, 1.95f, 99f), "密度上限被钳制");
    }

    @Test
    void burstVelocityPointsOutwardAndUp() {
        // 角度 0 → 正 X 方向
        double[] v = SAODeathEffect.burstVelocity(0, 1f, 0.34f);
        assertTrue(v[0] > 0, "水平分量朝角度方向");
        assertEquals(0, v[2], 1e-9, "角度 0 时 Z 分量为 0");
        assertTrue(v[1] > 0, "竖直分量为上抛");
    }

    @Test
    void outerShardsFlyFasterHorizontally() {
        double inner = SAODeathEffect.burstVelocity(0, 0f, 0.34f)[0];
        double outer = SAODeathEffect.burstVelocity(0, 1f, 0.34f)[0];
        assertTrue(outer > inner, "外圈碎片水平速度更大");
    }

    @Test
    void innerShardsGetMoreLift() {
        double inner = SAODeathEffect.burstVelocity(0, 0f, 0.34f)[1];
        double outer = SAODeathEffect.burstVelocity(0, 1f, 0.34f)[1];
        assertTrue(inner > outer, "中心碎片上抛更高,形成向上喷涌的形状");
    }

    @Test
    void glowCountStaysSmall() {
        int chicken = SAODeathEffect.glowCount(0.4f, 0.7f);
        int dragon = SAODeathEffect.glowCount(16f, 8f);
        assertTrue(chicken >= 1 && chicken <= 2, "小生物闪光 1-2 个,实际 " + chicken);
        assertTrue(chicken <= dragon, "巨物闪光不少于小生物");
        assertEquals(5, dragon, "巨物闪光钳到上限 5");
    }

    @Test
    void trailShardsAreAFractionOfTotal() {
        assertEquals(2, SAODeathEffect.trailShardsPerTick(6), "下限 2 个");
        assertEquals(20, SAODeathEffect.trailShardsPerTick(120), "总量的六分之一");
    }

    @Test
    void configTogglePersists() {
        SAOConfig.setDeathShatter(false);
        SAOConfig.setDeathShatterDensity(2f);
        assertEquals(false, SAOConfig.deathShatter());
        assertEquals(2f, SAOConfig.deathShatterDensity(), 0.001f);
        SAOConfig.setDeathShatterDensity(99f);
        assertEquals(SAOConfig.SHATTER_MAX, SAOConfig.deathShatterDensity(), 0.001f);
    }
}
