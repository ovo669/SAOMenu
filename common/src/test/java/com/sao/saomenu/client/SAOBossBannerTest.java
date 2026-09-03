package com.sao.saomenu.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boss 横幅状态机回归:登记淡入、无人再登记后淡出移除。
 *
 * <p>回归背景:SEEN 曾经永不清理,淡出分支永远走不到——
 * Boss 被打死/移出视野后横幅永远挂在屏幕上(末影龙、监守者均复现)。</p>
 *
 * <p>一帧的时序是「世界扫描 register → HUD 渲染 decayFrame」:
 * decayFrame 先跳过本帧已登记的条目,再清空 SEEN。所以看到 Boss 的那一帧
 * 不会衰减,衰减从下一个没有登记的帧开始。</p>
 */
class SAOBossBannerTest {

    @AfterEach
    void cleanUp() {
        SAOConfig.reset();
        SAOBossBanner.reset();
    }

    @Test
    void repeatedRegistrationRampsToFull() {
        for (int i = 0; i < 20; i++) {
            SAOBossBanner.register(1, "Warden", 1f);
        }
        assertEquals(1f, SAOBossBanner.strengthOf(1), 0.0001f, "多次登记应淡入到满强度且封顶 1");
    }

    @Test
    void seenFrameDoesNotDecay() {
        SAOBossBanner.register(1, "Warden", 1f);
        float before = SAOBossBanner.strengthOf(1);
        SAOBossBanner.decayFrame();
        assertEquals(before, SAOBossBanner.strengthOf(1), 0.0001f,
                "本帧登记过的 Boss 不应在同一帧被衰减");
    }

    @Test
    void unregisteredBossDecaysToZeroAndIsRemoved() {
        watch(7, "Wither");
        idle(20);
        assertEquals(0f, SAOBossBanner.strengthOf(7), "Boss 消失后强度应衰减到 0(旧版永远满值)");
        assertEquals(-1, SAOBossBanner.strongestId(), "条目应被移除,不再显示横幅");
        assertNull(SAOBossBanner.nameOf(7), "条目移除后名字一并清理");
    }

    @Test
    void fadeOutIsGradualNotInstant() {
        watch(7, "Wither");
        idle(1);
        float after = SAOBossBanner.strengthOf(7);
        assertTrue(after > 0.5f && after < 1f,
                "淡出应逐帧进行,第一帧不应直接归零(实际 " + after + ")");
    }

    @Test
    void namePersistsDuringFadeOut() {
        watch(3, "Warden");
        idle(1);
        assertNotNull(SAOBossBanner.nameOf(3), "淡出期间仍需能读出名字,横幅才能带着名字渐隐");
    }

    @Test
    void onlyReRegisteredBossSurvivesDecay() {
        for (int i = 0; i < 20; i++) {
            SAOBossBanner.register(1, "Evoker", 1f);
            SAOBossBanner.register(2, "Ravager", 1f);
        }
        SAOBossBanner.decayFrame();               // 结束这一帧
        SAOBossBanner.register(2, "Ravager", 1f); // 下一帧只有 2 号还在视线里
        SAOBossBanner.decayFrame();
        assertEquals(2, SAOBossBanner.strongestId(), "重新登记者胜出,未登记者继续衰减");
        assertTrue(SAOBossBanner.strengthOf(1) < 1f, "未登记者应开始淡出");
    }

    @Test
    void weakLookIsNotRegistered() {
        SAOBossBanner.register(5, "Ender Dragon", 0.2f);
        assertEquals(0f, SAOBossBanner.strengthOf(5), "视线未对准(门控 0.35 以下)不应登记");
    }

    @Test
    void disabledConfigSuppressesRegistration() {
        SAOConfig.setShowBossBanner(false);
        SAOBossBanner.register(9, "Elder Guardian", 1f);
        assertEquals(0f, SAOBossBanner.strengthOf(9), "开关关闭时不应登记");
    }

    /** 持续看着某 Boss 直到横幅满强度,并结束这一帧。 */
    private static void watch(int id, String name) {
        for (int i = 0; i < 20; i++) {
            SAOBossBanner.register(id, name, 1f);
        }
        SAOBossBanner.decayFrame();
    }

    /** n 个没有任何登记的帧(Boss 已死/已移出视野)。 */
    private static void idle(int frames) {
        for (int i = 0; i < frames; i++) {
            SAOBossBanner.decayFrame();
        }
    }
}
