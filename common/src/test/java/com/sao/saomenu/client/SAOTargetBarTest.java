package com.sao.saomenu.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标血条造型与配色回归:尺寸钳制、血量分段渐变、菱形轮廓、受击闪白衰减。
 */
class SAOTargetBarTest {

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    @Test
    void barSizeClampsToUsableRange() {
        assertEquals(56, SAOTargetBar.barWidth(100), "极小窗口钳到下限");
        assertEquals(150, SAOTargetBar.barWidth(4000), "超大窗口钳到上限");
        assertTrue(SAOTargetBar.barHeight(1080) >= 7 && SAOTargetBar.barHeight(1080) <= 16);
    }

    @Test
    void skewGrowsWithBarHeight() {
        assertTrue(SAOTargetBar.skewOffset(10) > SAOTargetBar.skewOffset(5),
                "条越高斜切偏移越大");
    }

    @Test
    void tailIsSmallFractionOfBar() {
        int barW = SAOTargetBar.barWidth(1080);
        int tail = SAOTargetBar.tailWidth(barW);
        assertTrue(tail >= 5 && tail < barW / 4, "尾块应明显小于主条,实际 " + tail);
    }

    @Test
    void fullHealthIsGreen() {
        int c = SAOTargetBar.hpColor(1f);
        assertTrue(green(c) > red(c), "满血偏绿");
    }

    @Test
    void lowHealthIsRed() {
        int c = SAOTargetBar.hpColor(0.05f);
        assertTrue(red(c) > green(c) * 2, "残血明显偏红");
    }

    @Test
    void midHealthPassesThroughYellow() {
        int c = SAOTargetBar.hpColor(0.6f);
        assertTrue(red(c) > 200 && green(c) > 200, "60% 处为黄色(红绿都高)");
    }

    @Test
    void redRisesMonotonicallyAsHealthDrops() {
        int prev = -1;
        for (float f = 1f; f >= 0f; f -= 0.05f) {
            int r = red(SAOTargetBar.hpColor(f));
            if (prev >= 0) {
                assertTrue(r >= prev - 2,
                        "血量下降时红色分量不应回落: frac=" + f + " r=" + r + " prev=" + prev);
            }
            prev = r;
        }
    }

    @Test
    void greenFallsAsHealthDrops() {
        assertTrue(green(SAOTargetBar.hpColor(1f)) > green(SAOTargetBar.hpColor(0.2f)),
                "残血时绿色分量应显著降低");
    }

    @Test
    void kiteIsWidestNearTop() {
        assertEquals(0f, SAOTargetBar.kiteHalfWidth(0f), 0.001f, "顶点收拢");
        assertEquals(1f, SAOTargetBar.kiteHalfWidth(0.32f), 0.001f, "上段 32% 处最宽");
        assertEquals(0f, SAOTargetBar.kiteHalfWidth(1f), 0.001f, "底尖收拢");
        assertTrue(SAOTargetBar.kiteHalfWidth(0.6f) > SAOTargetBar.kiteHalfWidth(0.9f),
                "下段逐渐收成长尖");
    }

    @Test
    void flashDecaysToZero() {
        assertEquals(1f, SAOTargetBar.flashStrength(1000, 1000), 0.001f, "命中当帧最强");
        assertTrue(SAOTargetBar.flashStrength(1000 + SAOTargetBar.FLASH_MS / 2, 1000) < 0.5f,
                "二次衰减,中点应低于一半");
        assertEquals(0f, SAOTargetBar.flashStrength(1000 + SAOTargetBar.FLASH_MS, 1000), 0.001f);
        assertEquals(0f, SAOTargetBar.flashStrength(1000, 0), 0.001f, "从未受击不闪");
    }

    @Test
    void kiteBobStaysSmall() {
        for (long t = 0; t < 4000; t += 97) {
            float b = SAOTargetBar.kiteBob(t);
            assertTrue(Math.abs(b) <= 1.61f, "浮动幅度应在 ±1.6px 内,实际 " + b);
        }
    }

    @Test
    void lerpColorInterpolatesEndpoints() {
        assertEquals(0xFF000000, SAOTargetBar.lerpColor(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, SAOTargetBar.lerpColor(0xFF000000, 0xFFFFFFFF, 1f));
        int mid = SAOTargetBar.lerpColor(0xFF000000, 0xFFFFFFFF, 0.5f);
        assertTrue(red(mid) > 120 && red(mid) < 136, "中点应接近灰,实际 " + red(mid));
    }
}
