package com.sao.saomenu.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跑马灯滚动的边界与单调性回归。
 */
class SAOScrollTextTest {

    @Test
    void textThatFitsNeverScrolls() {
        assertEquals(0, SAOScrollText.offset(40, 80, 12345L, 7), "放得下就不该平移");
        assertEquals(0, SAOScrollText.offset(80, 80, 99999L, 7), "刚好放下也不平移");
    }

    @Test
    void offsetStaysWithinOverflow() {
        int textW = 200;
        int maxW = 60;
        int over = textW - maxW;
        for (long t = 0; t < 20000; t += 37) {
            int off = SAOScrollText.offset(textW, maxW, t, 0);
            assertTrue(off >= 0 && off <= over,
                    "平移量必须在 [0, " + over + "] 内,实际 " + off + " @t=" + t);
        }
    }

    @Test
    void holdsAtStartThenScrolls() {
        int textW = 200;
        int maxW = 60;
        assertEquals(0, SAOScrollText.offset(textW, maxW, 0L, 0), "开头应停顿");
        assertEquals(0, SAOScrollText.offset(textW, maxW, SAOScrollText.HOLD_MS - 1, 0),
                "停顿期内不动");
        assertTrue(SAOScrollText.offset(textW, maxW, SAOScrollText.HOLD_MS + 200, 0) > 0,
                "停顿结束后应开始滚动");
    }

    @Test
    void reachesFullOverflowAtFarEnd() {
        int textW = 200;
        int maxW = 60;
        int over = textW - maxW;
        long travel = Math.round(over / SAOScrollText.SPEED_PX_PER_SEC * 1000f);
        int off = SAOScrollText.offset(textW, maxW, SAOScrollText.HOLD_MS + travel, 0);
        assertEquals(over, off, "滚到尾端应正好露出末尾");
    }

    @Test
    void cycleRepeats() {
        int textW = 160;
        int maxW = 50;
        int over = textW - maxW;
        long travel = Math.round(over / SAOScrollText.SPEED_PX_PER_SEC * 1000f);
        long cycle = 2L * (travel + SAOScrollText.HOLD_MS);
        for (long t = 0; t < 3000; t += 211) {
            assertEquals(SAOScrollText.offset(textW, maxW, t, 3),
                    SAOScrollText.offset(textW, maxW, t + cycle, 3),
                    "同一相位应给出相同平移量");
        }
    }

    @Test
    void differentSeedsDesynchronize() {
        int textW = 300;
        int maxW = 60;
        boolean differs = false;
        for (long t = 0; t < 4000 && !differs; t += 97) {
            if (SAOScrollText.offset(textW, maxW, t, 0)
                    != SAOScrollText.offset(textW, maxW, t, 12345)) {
                differs = true;
            }
        }
        assertTrue(differs, "不同种子应错开相位,避免同屏所有行同步滚动");
    }

    @Test
    void windowStartsWithBeginningAtZeroShift() {
        // 等宽字形 10px/字:shift 0 → 从第 0 字开始
        java.util.function.ToIntFunction<String> m = s -> s.length() * 10;
        assertEquals("abcde", SAOScrollText.window("abcdefghij", m, 0, 50),
                "shift=0 应从头显示");
    }

    @Test
    void windowSlidesByGlyph() {
        java.util.function.ToIntFunction<String> m = s -> s.length() * 10;
        assertEquals("bcdef", SAOScrollText.window("abcdefghij", m, 10, 50),
                "shift=10 应跳过第一个字形");
        assertEquals("cdefg", SAOScrollText.window("abcdefghij", m, 20, 50),
                "shift=20 应跳过前两个字形");
    }

    @Test
    void windowNeverExceedsMaxWidth() {
        java.util.function.ToIntFunction<String> m = String::length; // 1px/字
        for (int shift = 0; shift <= 20; shift += 3) {
            String w = SAOScrollText.window("abcdefghijklmnopqrstuvwxyz", m, shift, 7);
            assertTrue(w.length() <= 7, "窗口宽不该超过 maxW(实际 " + w.length() + ")");
        }
    }

    @Test
    void windowPastEndReturnsEmpty() {
        java.util.function.ToIntFunction<String> m = s -> s.length() * 10;
        assertEquals("", SAOScrollText.window("abc", m, 100, 50), "滚过末尾应为空串");
    }
}
