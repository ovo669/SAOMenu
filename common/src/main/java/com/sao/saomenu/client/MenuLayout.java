package com.sao.saomenu.client;

/**
 * 菜单布局的纯数学模型,不依赖 Minecraft 类,可被单元测试直接覆盖。
 *
 * <p>所有尺寸按 SAO-World 参考截图测量,以 <b>屏幕高度比例</b> 表达,
 * 在任意分辨率/GUI 缩放下都与原版观感一致:</p>
 * <ul>
 *   <li>主按钮列:首个(活动)按钮圆心锚定在 (38.9%W, 36.3%H),向下排列</li>
 *   <li>按钮直径 = 5.2%H,相邻圆心距 = 1.5 倍直径</li>
 *   <li>菜单项:高 5.0%H、宽 3.54 倍高(SAO Utils 官方条目贴图比例)、间隔 0.45%H,垂直围绕活动按钮</li>
 *   <li>玩家卡:宽 36.1%H、高 46.3%H,在按钮列左侧</li>
 *   <li>底部圆点:直径 2.8%H,圆心在 97.2%H 处水平居中</li>
 * </ul>
 * <p>坐标均为 GUI 虚拟像素(Screen#width/height 坐标系)。</p>
 */
public final class MenuLayout {

    /** 锚点比例(按钮列 X;放中线附近,给二级展开后的整组左移留位)。 */
    public static final float ANCHOR_X_FRAC = 0.44f;
    public static final float ANCHOR_Y_FRAC = 0.363f;

    /** 主按钮数量。 */
    public static final int BTN_COUNT = 4;
    /** 底部圆点数量。 */
    public static final int DOT_COUNT = 10;

    private MenuLayout() {
    }

    // ---------------------------------------------------------------- 尺寸

    public static int btnSize(int screenH) {
        return Math.max(13, Math.round(screenH * 0.052f));
    }

    public static int btnStep(int screenH) {
        return Math.round(btnSize(screenH) * 1.5f);
    }

    public static int itemH(int screenH) {
        return Math.max(13, Math.round(screenH * 0.050f));
    }

    public static int itemGap(int screenH) {
        return Math.max(1, Math.round(screenH * 0.0045f));
    }

    public static int itemW(int screenH) {
        // 按 SAO Utils 官方 list 条目贴图比例(163x46 ≈ 3.54:1)
        return Math.round(itemH(screenH) * 3.54f);
    }

    /** 按钮圆周与菜单项列之间的箭头区宽度。 */
    public static int arrowGap(int screenH) {
        return Math.round(btnSize(screenH) * 0.9f);
    }

    /** 一级菜单与二级菜单列之间的间隙。 */
    public static int childGap(int screenH) {
        return Math.max(2, Math.round(screenH * 0.015f));
    }

    public static int cardW(int screenH) {
        return Math.max(70, Math.round(screenH * 0.361f));
    }

    public static int cardH(int screenH) {
        return Math.round(screenH * 0.463f);
    }

    /** 未缩放的圆点直径(布局基准)。 */
    private static int baseDotSize(int screenH) {
        return Math.max(4, Math.round(screenH * 0.028f));
    }

    /** 圆点直径(乘配置里的物品栏缩放)。 */
    public static int dotSize(int screenH) {
        return Math.max(4, Math.round(baseDotSize(screenH) * SAOConfig.hotbarScale()));
    }

    public static int dotStep(int screenH) {
        return Math.round(dotSize(screenH) * 1.7f);
    }

    /** SAO 血条板:宽 34%H(下限 120),高为宽的 24%。 */
    public static int plateW(int screenW) {
        return Math.max(130, Math.round(screenW * 0.30f));
    }

    public static int plateH(int screenW) {
        // hp_bar.png 基准 360x83,保持贴图比例不拉伸
        return Math.max(18, Math.round(plateW(screenW) * 83f / 360f));
    }

    /** 血条板右下边缘 Y(贴屏幕左上角 (0,0))。 */
    public static int plateBottom(int screenW) {
        return plateH(screenW);
    }

    // ---------------------------------------------------------------- 锚点

    /** 首个主按钮(列顶)的圆心(锚点比例可经 SAOConfig 调整)。 */
    public static int firstButtonCenterX(int screenW) {
        return Math.round(screenW * SAOConfig.anchorX());
    }

    public static int firstButtonCenterY(int screenH) {
        return Math.round(screenH * SAOConfig.anchorY());
    }

    /** 第 index 个主按钮的圆心(向下排列)。 */
    public static int buttonCenterY(int screenH, int index) {
        return buttonCenterYAt(screenH, firstButtonCenterY(screenH), index);
    }

    // ------------------------------------------------------ 任意锚点变体
    // 跟随鼠标模式等非配置锚点场景使用;旧 API 均委托到配置锚点,保持兼容。

    /** 任意首按钮锚点下,第 index 个主按钮的圆心 Y。 */
    public static int buttonCenterYAt(int screenH, int anchorY, int index) {
        return anchorY + index * btnStep(screenH);
    }

    /** 任意锚点 X 下一级菜单列的 X。 */
    public static int itemColumnXAt(int anchorX, int screenH) {
        return anchorX + btnSize(screenH) / 2 + arrowGap(screenH);
    }

    /** 任意锚点 X 下二级菜单列的 X。 */
    public static int childColumnXAt(int anchorX, int screenH) {
        return itemColumnXAt(anchorX, screenH) + itemW(screenH) + childGap(screenH) + arrowGap(screenH) / 2;
    }

    /** 装备条目列(第三列)与二级菜单列之间的间隙。 */
    public static int equipGap(int screenH) {
        return childGap(screenH);
    }

    /** 任意锚点 X 下装备条目列(第三列)的 X。 */
    public static int equipColumnXAt(int anchorX, int screenH) {
        return childColumnXAt(anchorX, screenH) + itemW(screenH) + equipGap(screenH) + arrowGap(screenH) / 2;
    }

    /**
     * 装备条目(第三列)第 index 项的矩形。条目比普通菜单项矮一号,
     * 间距更小,贴近参考图里护甲列表的紧凑排布。
     */
    public static Rect equipItemRectAt(int screenW, int screenH, int count, int anchorX, int anchorY, int index) {
        int h = equipItemH(screenH);
        int step = h + equipItemGap(screenH);
        int totalH = (Math.max(1, count) - 1) * step + h;
        int top = clampedAnchorYAt(screenH, totalH, anchorY) - totalH / 2 + index * step;
        return new Rect(equipColumnXAt(anchorX, screenH), top, equipItemW(screenH), h);
    }

    /** 装备条目高度:约为普通菜单项的 0.8 倍。 */
    public static int equipItemH(int screenH) {
        return Math.max(11, Math.round(itemH(screenH) * 0.8f));
    }

    /** 装备条目纵向间距。 */
    public static int equipItemGap(int screenH) {
        return Math.max(1, Math.round(itemGap(screenH) * 0.7f));
    }

    /** 装备条目宽度:同普通菜单项,保持同一贴图比例。 */
    public static int equipItemW(int screenH) {
        return itemW(screenH);
    }

    /** 装备条目列数较多时把锚点 Y 钳制,保证整列不越出屏幕。 */
    public static int clampedAnchorYAt(int screenH, int totalH, int anchorY) {
        int top = anchorY - totalH / 2;
        if (top < 2) {
            return 2 + totalH / 2;
        }
        if (top + totalH > screenH - 2) {
            return screenH - 2 - totalH + totalH / 2;
        }
        return anchorY;
    }

    /** 任意锚点下第 index 项(共 count 项)的矩形。 */
    public static Rect menuItemRectAt(int screenW, int screenH, int count, int anchorX, int anchorY, int index) {
        int step = itemH(screenH) + itemGap(screenH);
        int totalH = (count - 1) * step + itemH(screenH);
        int top = clampedAnchorY(screenH, count, anchorY) - totalH / 2 + index * step;
        return new Rect(itemColumnXAt(anchorX, screenH), top, itemW(screenH), itemH(screenH));
    }

    /** 任意锚点下二级第 index 项(共 count 项)的矩形。 */
    public static Rect childItemRectAt(int screenW, int screenH, int count, int anchorX, int anchorY, int index) {
        int step = itemH(screenH) + itemGap(screenH);
        int totalH = (count - 1) * step + itemH(screenH);
        int top = clampedAnchorY(screenH, count, anchorY) - totalH / 2 + index * step;
        return new Rect(childColumnXAt(anchorX, screenH), top, itemW(screenH), itemH(screenH));
    }

    /** 任意锚点下的玩家卡矩形。 */
    public static Rect cardRectAt(int screenW, int screenH, int anchorX, int anchorY) {
        int gap = Math.max(4, Math.round(btnSize(screenH) * 0.55f));
        int w = Math.min(cardW(screenH), Math.max(60, anchorX - btnSize(screenH) / 2 - gap - 4));
        int right = anchorX - btnSize(screenH) / 2 - gap;
        int top = clampedAnchorY(screenH, 1, anchorY) - cardH(screenH) / 2;
        top = Math.min(top, screenH - cardH(screenH) - 2);
        // 不与左上角血条板 + 状态效果行重叠
        top = Math.max(top, plateBottom(screenW) + 24);
        // 锚点偏左时右缘可能把卡片推出屏幕:整张卡钳回屏幕内(保持最小可读宽)
        return new Rect(Math.max(0, right - w), top, w, cardH(screenH));
    }

    /** 任意锚点下第 index 个主按钮的命中判定。 */
    public static boolean inMainButtonAt(int screenW, int screenH, int anchorX, int anchorY, int index, int x, int y) {
        return inCircle(anchorX, buttonCenterYAt(screenH, anchorY, index), btnSize(screenH) / 2, x, y);
    }

    /** 任意锚点下悬停的主按钮序号;不在任何按钮上返回 -1。 */
    public static int hoveredMainButtonAt(int screenW, int screenH, int anchorX, int anchorY, int x, int y) {
        for (int i = 0; i < BTN_COUNT; i++) {
            if (inMainButtonAt(screenW, screenH, anchorX, anchorY, i, x, y)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------ 跟随鼠标锚点
    // 参照 SAO_Utils Web:菜单出现在光标处,自动钳制保证玩家卡与两级菜单列完整可见。

    /**
     * 跟随鼠标打开时首按钮锚点 X:光标为锚,左侧留出玩家卡(上限宽)+间隙,
     * 右侧留出一级+二级菜单列;屏幕过窄放不下时取可行区间中点。
     */
    public static int cursorAnchorX(int screenW, int screenH, int mouseX) {
        int margin = 4;
        int btnHalf = btnSize(screenH) / 2;
        int gap = Math.max(4, Math.round(btnSize(screenH) * 0.55f));
        int lo = margin + btnHalf + gap + cardW(screenH);
        int rightExtent = btnHalf + arrowGap(screenH) + itemW(screenH) + childGap(screenH)
                + arrowGap(screenH) / 2 + itemW(screenH) + margin;
        int hi = screenW - rightExtent;
        if (lo > hi) {
            return (lo + hi) / 2;
        }
        return clampInt(mouseX, lo, hi);
    }

    /** 跟随鼠标打开时首按钮锚点 Y:首个与末个按钮完整可见;放不下时取屏幕中线。 */
    public static int cursorAnchorY(int screenH, int mouseY) {
        int top = btnSize(screenH) / 2 + 6;
        int bottom = screenH - (BTN_COUNT - 1) * btnStep(screenH) - btnSize(screenH) / 2 - 4;
        if (top > bottom) {
            return screenH / 2;
        }
        return clampInt(mouseY, top, bottom);
    }

    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ---------------------------------------------------------------- 命中

    public static boolean inCircle(int cx, int cy, int r, int x, int y) {
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    public static boolean inMainButton(int screenW, int screenH, int index, int x, int y) {
        return inMainButtonAt(screenW, screenH, firstButtonCenterX(screenW), firstButtonCenterY(screenH), index, x, y);
    }

    /** 悬停的主按钮序号;不在任何按钮上返回 -1。 */
    public static int hoveredMainButton(int screenW, int screenH, int x, int y) {
        return hoveredMainButtonAt(screenW, screenH, firstButtonCenterX(screenW), firstButtonCenterY(screenH), x, y);
    }

    // ---------------------------------------------------------------- 菜单项

    /** 展开项较多时把锚点 Y 钳制,保证整列不越出屏幕。 */
    public static int clampedAnchorY(int screenH, int count, int anchorY) {
        int step = itemH(screenH) + itemGap(screenH);
        int totalH = (count - 1) * step + itemH(screenH);
        int top = anchorY - totalH / 2;
        if (top < 2) {
            return 2 + totalH / 2;
        }
        if (top + totalH > screenH - 2) {
            return screenH - 2 - totalH + totalH / 2;
        }
        return anchorY;
    }

    /** 第 index 项(共 count 项)的矩形,围绕活动按钮垂直展开。 */
    public static Rect menuItemRect(int screenW, int screenH, int count, int anchorY, int index) {
        return menuItemRectAt(screenW, screenH, count, firstButtonCenterX(screenW), anchorY, index);
    }

    /** 一级菜单列的 X。 */
    public static int itemColumnX(int screenW, int screenH) {
        return itemColumnXAt(firstButtonCenterX(screenW), screenH);
    }

    /** 二级菜单列的 X(一级右侧再隔一个箭头区)。 */
    public static int childColumnX(int screenW, int screenH) {
        return childColumnXAt(firstButtonCenterX(screenW), screenH);
    }

    public static Rect childItemRect(int screenW, int screenH, int count, int anchorY, int index) {
        return childItemRectAt(screenW, screenH, count, firstButtonCenterX(screenW), anchorY, index);
    }

    // ---------------------------------------------------------------- 玩家卡

    /** 玩家卡矩形:与按钮列留出一个箭头间隙,右缘不超过按钮圆周。 */
    public static Rect cardRect(int screenW, int screenH, int anchorY) {
        return cardRectAt(screenW, screenH, firstButtonCenterX(screenW), anchorY);
    }

    // ---------------------------------------------------------------- 底部圆点

    /** 副手圆点与主栏(快捷栏 9 格)之间的间隔,占圆点直径的比例。 */
    public static final float DOT_OFFHAND_GAP_FRAC = 0.55f;

    /**
     * 圆点物品栏布局:index 0 = 副手(最左,与主栏隔开一档),
     * index 1..9 = 快捷栏槽位 0..8;整组仍水平居中。
     */
    public static int dotCenterX(int screenW, int screenH, int index) {
        int step = dotStep(screenH);
        int gap = Math.round(dotSize(screenH) * DOT_OFFHAND_GAP_FRAC);
        // 最左圆心到最右圆心跨度 = 9 个 step + 副手额外间隔
        int total = (DOT_COUNT - 1) * step + gap;
        int left = screenW / 2 - total / 2;
        return index == 0 ? left : left + step + gap + (index - 1) * step;
    }

    /** 圆心 Y:底缘锚定——圆点随配置放大时中心上移,底缘位置不变。 */
    public static int dotCenterY(int screenH) {
        float baseEdge = screenH * 0.972f + baseDotSize(screenH) * 0.5f;
        return Math.round(baseEdge - dotSize(screenH) * 0.5f);
    }

    public static boolean inDot(int screenW, int screenH, int index, int x, int y) {
        return inCircle(dotCenterX(screenW, screenH, index), dotCenterY(screenH),
                dotSize(screenH) / 2 + 1, x, y);
    }

    /** 布局矩形。 */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        public int centerX() {
            return x + w / 2;
        }

        public int centerY() {
            return y + h / 2;
        }
    }
}
