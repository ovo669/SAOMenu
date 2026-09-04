package com.sao.saomenu.client;

/**
 * 跑马灯滚动的纯数学(不依赖 Minecraft 类,可单元测试)。
 *
 * <p>时间轴按「停顿 → 匀速滚到尾 → 停顿 → 匀速滚回头」循环:
 * 两端各留停顿是为了让人读清开头与结尾,不做首尾相接的无缝环绕——
 * 物品名接在自己后面读起来更乱。</p>
 */
public final class SAOScrollText {

    /** 滚动速度(GUI 像素 / 秒)。 */
    public static final float SPEED_PX_PER_SEC = 26f;
    /** 两端停顿(毫秒)。 */
    public static final long HOLD_MS = 900L;

    private SAOScrollText() {
    }

    /**
     * 当前该把文字向左平移多少像素。
     *
     * @param textW 文字总宽
     * @param maxW  可见窗口宽
     * @param nowMs 当前时间(毫秒)
     * @param seed  每行独立的相位种子(同屏多行不整齐同步,观感更自然)
     * @return 平移量,恒在 {@code [0, textW - maxW]} 内;放得下时为 0
     */
    public static int offset(int textW, int maxW, long nowMs, int seed) {
        int over = textW - maxW;
        if (over <= 0) {
            return 0;
        }
        long travel = Math.max(1L, Math.round(over / SPEED_PX_PER_SEC * 1000f));
        long cycle = 2L * (travel + HOLD_MS);
        // 相位错开:seed 取模一个周期,避免同屏所有行同步滚动
        long t = Math.floorMod(nowMs + Math.floorMod(seed, (int) Math.min(cycle, Integer.MAX_VALUE)), cycle);
        if (t < HOLD_MS) {
            return 0;
        }
        t -= HOLD_MS;
        if (t < travel) {
            return (int) Math.min(over, Math.round(over * (t / (double) travel)));
        }
        t -= travel;
        if (t < HOLD_MS) {
            return over;
        }
        t -= HOLD_MS;
        return (int) Math.max(0, Math.round(over * (1.0 - t / (double) travel)));
    }

    /**
     * 滚动窗口:取「从 shift 像素处开始、宽不超过 maxW」的一段子串。
     *
     * <p>逐字形推进而不是截像素——GuiGraphics 的文字绘制没有子像素裁剪,
     * 字形宽度由 {@code measurer} 提供(生产环境传 {@code Font::width},
     * 测试里可以传任意宽度函数)。返回串保证 {@code width(串) <= maxW}。</p>
     */
    public static String window(String text, java.util.function.ToIntFunction<String> measurer,
                                int shift, int maxW) {
        if (text == null || text.isEmpty() || maxW <= 0) {
            return "";
        }
        int from = 0;
        int acc = 0;
        // 找到起点字形:起点前的累计宽度恰好超过 shift 时停
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int clen = Character.charCount(cp);
            String ch = new String(Character.toChars(cp));
            int cw = measurer.applyAsInt(ch);
            if (acc + cw > shift) {
                from = i;
                break;
            }
            acc += cw;
            i += clen;
            if (i >= text.length()) {
                return ""; // 已滚过末尾
            }
        }
        // 从起点开始装满 maxW
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = from; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int cw = measurer.applyAsInt(ch);
            if (used + cw > maxW) {
                break;
            }
            sb.append(ch);
            used += cw;
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
