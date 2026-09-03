package com.sao.saomenu.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * SAO 动漫同款目标标识:斜切玻璃血条 + 头顶红色菱形。
 *
 * <p>血条由三层构成——外层半透明玻璃housing(环绕整条,含左端厚度盖片)、
 * 内层随血量收缩的彩色填充、右侧分离的小尾块。血量下降时颜色由绿经黄、橙渐变到红,
 * 受击瞬间整条闪白。</p>
 *
 * <p>几何与配色是不依赖 Minecraft 的纯函数({@link #hpColor}、{@link #kiteHalfWidth} 等),
 * 可单元测试;绘制由 {@link SAOCombatHud} 投影出屏幕坐标后调用。</p>
 */
public final class SAOTargetBar {

    // ------------------------------------------------------------ 造型常量

    /** 平行四边形每行的横向偏移量(相对条高的比例):顶边比底边更靠右。 */
    public static final float SKEW = 1.15f;
    /** 右侧分离尾块宽度占主条宽的比例。 */
    public static final float TAIL_FRAC = 0.13f;
    /** 尾块与主条的间隙(像素)。 */
    public static final int TAIL_GAP = 3;
    /** 受击闪白时长。 */
    public static final long FLASH_MS = 320;

    // 玻璃外壳配色:高光边 + 极淡内衬。参考图里壳体本身几乎无色,
    // 靠白色亮边勾出轮廓,所以 body 只给很低的不透明度
    private static final int GLASS_EDGE = 0xE6F2FAFF;
    private static final int GLASS_BODY = 0x2ACFE6FA;
    private static final int GLASS_CAP = 0xA0D8ECFF;
    private static final int TRACK_DARK = 0x40121618;

    // 头顶菱形:深红实心 + 亮红高光
    private static final int KITE_FILL = 0xFFD81E3C;
    private static final int KITE_EDGE = 0xFFFF6A82;

    // 血量色标:红分量单调递增、绿分量单调递减(见 hpColor)
    private static final int HP_GREEN = 0xFF7CE04A;
    private static final int HP_YELLOW = 0xFFF2E23A;
    private static final int HP_ORANGE = 0xFFF57F22;
    private static final int HP_RED = 0xFFF53A26;
    private static final int HP_CRIT = 0xFFFF1520;

    private SAOTargetBar() {
    }

    // ------------------------------------------------------------ 纯函数(可测)

    /** 主条宽度:随 GUI 高度缩放,钳制到手感区间。 */
    public static int barWidth(int screenH) {
        return Mth.clamp(Math.round(screenH * 0.16f), 56, 150);
    }

    /** 主条高度。参考图里条身比原版血条厚不少,约为宽的 1/7。 */
    public static int barHeight(int screenH) {
        return Mth.clamp(Math.round(screenH * 0.026f), 7, 16);
    }

    /** 顶边相对底边的总偏移(像素);平行四边形的斜度。 */
    public static int skewOffset(int barH) {
        return Math.round(barH * SKEW);
    }

    /** 右侧尾块宽度。 */
    public static int tailWidth(int barW) {
        return Math.max(5, Math.round(barW * TAIL_FRAC));
    }

    /**
     * 血量颜色:满血亮绿 → 60% 黄 → 30% 橙 → 15% 以下正红。
     *
     * <p>分段线性插值。色标刻意选成「红分量随血量下降单调不减、绿分量单调不增」,
     * 这样任何一次掉血都只会让颜色朝更危险的方向走,不会出现回弹。</p>
     */
    public static int hpColor(float frac) {
        float f = Mth.clamp(frac, 0f, 1f);
        if (f >= 0.6f) {
            return lerpColor(HP_YELLOW, HP_GREEN, (f - 0.6f) / 0.4f);
        }
        if (f >= 0.3f) {
            return lerpColor(HP_ORANGE, HP_YELLOW, (f - 0.3f) / 0.3f);
        }
        if (f >= 0.15f) {
            return lerpColor(HP_RED, HP_ORANGE, (f - 0.15f) / 0.15f);
        }
        return lerpColor(HP_CRIT, HP_RED, f / 0.15f);
    }

    /**
     * 头顶菱形(风筝形)在给定纵向进度处的半宽比例。
     *
     * @param t 0 为顶点、1 为底尖
     * @return 半宽相对最大半宽的比例 0..1
     */
    public static float kiteHalfWidth(float t) {
        float u = Mth.clamp(t, 0f, 1f);
        // 上段 32% 迅速张开到最宽,下段收成长尖
        return u <= 0.32f ? u / 0.32f : 1f - (u - 0.32f) / 0.68f;
    }

    /** 受击闪白强度:0 表示无闪。 */
    public static float flashStrength(long now, long hurtAt) {
        if (hurtAt <= 0) {
            return 0f;
        }
        long age = now - hurtAt;
        if (age < 0 || age >= FLASH_MS) {
            return 0f;
        }
        float t = age / (float) FLASH_MS;
        return (1f - t) * (1f - t);
    }

    /** 菱形浮动位移(像素):缓慢上下呼吸。 */
    public static float kiteBob(long now) {
        return Mth.sin(now / 420f) * 1.6f;
    }

    // ------------------------------------------------------------ 绘制

    /**
     * 绘制目标血条:锚点是生物身侧的屏幕坐标,血条右端贴在锚点上并向左延伸,
     * 纵向以锚点为中心,所以整条会环绕在身体侧面而不是浮在头顶。
     *
     * @param anchorX 身侧锚点投影 X(主条右端所在处)
     * @param anchorY 身侧锚点投影 Y(条身纵向中心)
     */
    public static void renderBar(GuiGraphics g, Minecraft mc, LivingEntity le,
                                 float anchorX, float anchorY, int screenW, int screenH,
                                 long now, long hurtAt, float alpha) {
        int barW = barWidth(screenH);
        int barH = barHeight(screenH);
        int skew = skewOffset(barH);
        int tailW = tailWidth(barW);

        int totalW = barW + TAIL_GAP + tailW;
        int capW0 = Math.max(2, Math.round(barH * 0.42f)) + 1;
        int x = Math.round(anchorX) - barW;
        int y = Math.round(anchorY) - barH / 2;
        x = Mth.clamp(x, capW0 + 3, Math.max(capW0 + 3, screenW - totalW - skew - 3));
        y = Mth.clamp(y, 12, Math.max(12, screenH - barH - 6));

        float frac = le.getMaxHealth() <= 0f ? 0f
                : Mth.clamp(le.getHealth() / le.getMaxHealth(), 0f, 1f);
        float flash = flashStrength(now, hurtAt);

        // 1) 左端厚度盖片:让玻璃壳看起来是有厚度的环绕带,而不是一张贴片
        int capW = capW0 - 1;
        skewedQuad(g, x - capW - 1, y, capW, barH, SKEW, mulAlpha(GLASS_CAP, alpha * 0.9f));
        skewedOutline(g, x - capW - 1, y, capW, barH, SKEW, mulAlpha(GLASS_EDGE, alpha * 0.8f));

        // 2) 玻璃外壳:环绕整条(含空槽部分)
        skewedQuad(g, x, y, barW, barH, SKEW, mulAlpha(GLASS_BODY, alpha));
        skewedOutline(g, x, y, barW, barH, SKEW, mulAlpha(GLASS_EDGE, alpha));

        // 3) 内槽暗底 + 血量填充(自左向右收缩)
        int inset = 1;
        int innerW = Math.max(0, barW - inset * 2);
        int innerH = Math.max(1, barH - inset * 2);
        skewedQuad(g, x + inset, y + inset, innerW, innerH, SKEW,
                mulAlpha(TRACK_DARK, alpha));
        int fillW = Math.round(innerW * frac);
        if (fillW > 0) {
            int color = hpColor(frac);
            if (flash > 0f) {
                // 只提亮不洗白:0.42 上限保证受击时仍能看出当前血量档位的色相
                color = lerpColor(color, 0xFFFFFFFF, flash * 0.42f);
            }
            skewedQuad(g, x + inset, y + inset, fillW, innerH, SKEW, mulAlpha(color, alpha));
            // 顶部一行高光,做出玻璃管里的液面反光
            skewedQuad(g, x + inset, y + inset, fillW, 1, SKEW,
                    mulAlpha(lerpColor(color, 0xFFFFFFFF, 0.55f), alpha * 0.8f));
        }

        // 4) 右侧分离尾块:满血时亮起,残血时只留玻璃壳
        int tx = x + barW + TAIL_GAP;
        skewedQuad(g, tx, y, tailW, barH, SKEW, mulAlpha(GLASS_BODY, alpha));
        skewedOutline(g, tx, y, tailW, barH, SKEW, mulAlpha(GLASS_EDGE, alpha));
        if (frac > 0.98f) {
            skewedQuad(g, tx + inset, y + inset, Math.max(1, tailW - inset * 2), innerH, SKEW,
                    mulAlpha(hpColor(frac), alpha * 0.9f));
        }

        // 5) 名称与数值:名称在条左上方,数值在条右下方,都避开玻璃壳本体
        Font font = mc.font;
        String name = le.getDisplayName().getString();
        g.drawString(font, name, x + skew, y - font.lineHeight - 2,
                mulAlpha(0xFFF2F5F8, alpha), false);
        String hpText = trim(le.getHealth()) + "/" + trim(le.getMaxHealth());
        g.drawString(font, hpText, x + barW - font.width(hpText), y + barH + 3,
                mulAlpha(0xFFCED3D8, alpha), false);
    }

    /**
     * 绘制头顶红色菱形标识。
     *
     * @param cx 菱形中心 X(实体头顶正上方)
     * @param topY 菱形顶点 Y
     */
    public static void renderKite(GuiGraphics g, float cx, float topY, int screenH,
                                  long now, float alpha) {
        int kh = Mth.clamp(Math.round(screenH * 0.032f), 9, 20);
        int maxHalf = Math.max(2, Math.round(kh * 0.30f));
        int baseX = Math.round(cx);
        int baseY = Math.round(topY + kiteBob(now));

        for (int r = 0; r < kh; r++) {
            float t = kh <= 1 ? 0f : r / (float) (kh - 1);
            int half = Math.max(1, Math.round(maxHalf * kiteHalfWidth(t)));
            int yy = baseY + r;
            g.fill(baseX - half, yy, baseX + half, yy + 1, mulAlpha(KITE_FILL, alpha));
            // 左右各 1px 亮边,让菱形有动漫里的描边感
            g.fill(baseX - half, yy, baseX - half + 1, yy + 1, mulAlpha(KITE_EDGE, alpha));
            g.fill(baseX + half - 1, yy, baseX + half, yy + 1, mulAlpha(KITE_EDGE, alpha));
        }
    }

    // ------------------------------------------------------------ 斜切图元

    /**
     * 斜切平行四边形:逐行横移,顶行偏移最大。
     *
     * <p>{@code GuiGraphics.fill} 只能画轴对齐矩形,条高只有几像素,
     * 逐行填充的开销可以忽略。</p>
     */
    private static void skewedQuad(GuiGraphics g, int x, int y, int w, int h,
                                   float skewPerRow, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        for (int r = 0; r < h; r++) {
            int dx = Math.round((h - 1 - r) * skewPerRow);
            g.fill(x + dx, y + r, x + dx + w, y + r + 1, color);
        }
    }

    /** 斜切平行四边形的 1px 描边(上下横边 + 左右斜边)。 */
    private static void skewedOutline(GuiGraphics g, int x, int y, int w, int h,
                                      float skewPerRow, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        for (int r = 0; r < h; r++) {
            int dx = Math.round((h - 1 - r) * skewPerRow);
            boolean edgeRow = r == 0 || r == h - 1;
            if (edgeRow) {
                g.fill(x + dx, y + r, x + dx + w, y + r + 1, color);
            } else {
                g.fill(x + dx, y + r, x + dx + 1, y + r + 1, color);
                g.fill(x + dx + w - 1, y + r, x + dx + w, y + r + 1, color);
            }
        }
    }

    // ------------------------------------------------------------ 小工具

    /** 线性插值两个 ARGB(alpha 也参与)。 */
    public static int lerpColor(int from, int to, float t) {
        float f = Mth.clamp(t, 0f, 1f);
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * f);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f);
        int gg = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        return (Math.round(a * Mth.clamp(factor, 0f, 1f)) << 24) | (argb & 0xFFFFFF);
    }

    private static String trim(float v) {
        float r = Math.round(v * 10f) / 10f;
        return (r == Math.rint(r)) ? String.valueOf((int) r) : String.valueOf(r);
    }
}
