package com.sao.saomenu.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入世欢迎动画时间轴回归:各段淡入顺序、停留期不透明、末段整体淡出。
 */
class SAOWelcomeTest {

    @AfterEach
    void restoreDefaults() {
        SAOConfig.reset();
    }

    @Test
    void bannerFadesInFirst() {
        assertEquals(0f, SAOWelcome.bannerAlpha(0), 0.001f, "起始帧横幅不可见");
        assertTrue(SAOWelcome.bannerAlpha(SAOWelcome.BANNER_IN_MS / 2) > 0.5f,
                "缓出曲线在中点应已过半");
        assertEquals(1f, SAOWelcome.bannerAlpha(SAOWelcome.BANNER_IN_MS), 0.001f);
    }

    @Test
    void panelWaitsForItsDelay() {
        assertEquals(0f, SAOWelcome.panelAlpha(SAOWelcome.PANEL_DELAY_MS), 0.001f,
                "延迟结束瞬间面板仍不可见");
        assertEquals(1f, SAOWelcome.panelAlpha(SAOWelcome.PANEL_DELAY_MS + SAOWelcome.PANEL_IN_MS),
                0.001f);
    }

    @Test
    void textAppearsAfterPanelSettles() {
        assertEquals(0f, SAOWelcome.textAlpha(SAOWelcome.TEXT_DELAY_MS - 1), 0.001f);
        assertEquals(1f, SAOWelcome.textAlpha(SAOWelcome.TEXT_DELAY_MS + SAOWelcome.TEXT_IN_MS),
                0.001f);
    }

    @Test
    void everythingIsOpaqueDuringHold() {
        long mid = SAOWelcome.TEXT_DELAY_MS + SAOWelcome.TEXT_IN_MS + SAOWelcome.HOLD_MS / 2;
        assertEquals(1f, SAOWelcome.globalFade(mid), 0.001f);
        assertEquals(1f, SAOWelcome.bannerAlpha(mid), 0.001f);
        assertEquals(1f, SAOWelcome.panelAlpha(mid), 0.001f);
        assertEquals(1f, SAOWelcome.textAlpha(mid), 0.001f);
    }

    @Test
    void globalFadeDrivesAllLayersToZero() {
        long half = SAOWelcome.FADE_AT_MS + SAOWelcome.FADE_MS / 2;
        assertEquals(0.5f, SAOWelcome.globalFade(half), 0.02f);
        assertEquals(0.5f, SAOWelcome.bannerAlpha(half), 0.02f, "横幅随整体淡出");
        assertEquals(0f, SAOWelcome.globalFade(SAOWelcome.TOTAL_MS), 0.001f);
    }

    @Test
    void finishedOnlyOutsideTimeline() {
        assertTrue(SAOWelcome.finished(-1), "负数视为未开始");
        assertFalse(SAOWelcome.finished(0));
        assertFalse(SAOWelcome.finished(SAOWelcome.TOTAL_MS - 1));
        assertTrue(SAOWelcome.finished(SAOWelcome.TOTAL_MS));
    }

    @Test
    void panelScaleSettlesAtOne() {
        assertEquals(0.88f, SAOWelcome.panelScale(0), 0.001f);
        assertEquals(1f, SAOWelcome.panelScale(SAOWelcome.PANEL_DELAY_MS + SAOWelcome.PANEL_IN_MS),
                0.001f);
    }

    @Test
    void bannerRevealsFromCenter() {
        assertEquals(0f, SAOWelcome.bannerReveal(0), 0.001f);
        assertEquals(1f, SAOWelcome.bannerReveal(SAOWelcome.BANNER_IN_MS), 0.001f);
    }

    @Test
    void disabledConfigSuppressesPlayback() {
        SAOConfig.setShowWelcome(false);
        SAOWelcome.dismiss();
        SAOWelcome.start();
        assertFalse(SAOWelcome.active(), "关闭开关后不应播放");
    }
}
