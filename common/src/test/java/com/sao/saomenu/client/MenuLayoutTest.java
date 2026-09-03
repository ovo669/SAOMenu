package com.sao.saomenu.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布局数学回归:比例尺寸、锚点位置、命中判定、屏幕钳制。
 * 参考值取自 SAO-World 截图实测(按钮直径 5.2%H、锚点 38.9%/36.3%)。
 */
class MenuLayoutTest {

    private static final int W = 427;
    private static final int H = 240;

    @Test
    void buttonSizeFollowsScreenHeightFraction() {
        assertEquals(13, MenuLayout.btnSize(240));
        assertEquals(Math.round(360 * 0.052f), MenuLayout.btnSize(360));
        assertEquals(13, MenuLayout.btnSize(120), "极小屏高也不得低于可点击下限");
    }

    @Test
    void firstButtonAnchorsAtReferencePosition() {
        // 固定锚点 = 屏幕中线附近(留出二级展开后的整组左移空间)
        assertEquals(Math.round(W * 0.44f), MenuLayout.firstButtonCenterX(W));
        assertEquals(Math.round(H * 0.363f), MenuLayout.firstButtonCenterY(H));
    }

    @Test
    void buttonColumnExtendsDownwardWithoutOverlap() {
        for (int i = 0; i < MenuLayout.BTN_COUNT - 1; i++) {
            int gap = MenuLayout.buttonCenterY(H, i + 1) - MenuLayout.buttonCenterY(H, i);
            assertEquals(MenuLayout.btnStep(H), gap);
            assertTrue(gap >= MenuLayout.btnSize(H), "相邻按钮不得重叠");
        }
        int last = MenuLayout.buttonCenterY(H, MenuLayout.BTN_COUNT - 1) + MenuLayout.btnSize(H) / 2;
        assertTrue(last <= H);
    }

    @Test
    void hitTestCenterAndOutside() {
        int cx = MenuLayout.firstButtonCenterX(W);
        int cy = MenuLayout.buttonCenterY(H, 1);
        int r = MenuLayout.btnSize(H) / 2;
        assertTrue(MenuLayout.inMainButton(W, H, 1, cx, cy));
        assertTrue(MenuLayout.inMainButton(W, H, 1, cx, cy + r - 1));
        assertFalse(MenuLayout.inMainButton(W, H, 1, cx, cy + r + 2));
        assertFalse(MenuLayout.inMainButton(W, H, 1, cx + 100, cy));
        assertEquals(-1, MenuLayout.hoveredMainButton(W, H, 2, 2));
        assertEquals(2, MenuLayout.hoveredMainButton(W, H, cx, MenuLayout.buttonCenterY(H, 2)));
    }

    @Test
    void itemProportionsMatchReference() {
        assertEquals(Math.max(13, Math.round(H * 0.050f)), MenuLayout.itemH(H));
        assertEquals(Math.round(MenuLayout.itemH(H) * 3.54f), MenuLayout.itemW(H),
                "条目宽高比应与 SAO Utils 官方 list 贴图一致(163:46)");
    }

    @Test
    void menuItemsCenterOnActiveButtonWithoutOverlap() {
        int count = 3;
        int anchor = MenuLayout.buttonCenterY(H, 0);
        for (int i = 0; i < count - 1; i++) {
            MenuLayout.Rect a = MenuLayout.menuItemRect(W, H, count, anchor, i);
            MenuLayout.Rect b = MenuLayout.menuItemRect(W, H, count, anchor, i + 1);
            assertTrue(b.y() >= a.y() + a.h() + MenuLayout.itemGap(H), "菜单项不得重叠");
        }
        MenuLayout.Rect first = MenuLayout.menuItemRect(W, H, count, anchor, 0);
        MenuLayout.Rect last = MenuLayout.menuItemRect(W, H, count, anchor, count - 1);
        assertEquals(anchor, (first.y() + last.y() + last.h()) / 2);
    }

    @Test
    void clampedMenuNeverLeavesScreen() {
        int anchorTop = MenuLayout.buttonCenterY(H, 0);
        MenuLayout.Rect first = MenuLayout.menuItemRect(W, H, 3, anchorTop, 0);
        MenuLayout.Rect last = MenuLayout.menuItemRect(W, H, 3, anchorTop, 2);
        assertTrue(first.y() >= 0, "菜单首项不得高于屏幕顶");
        assertTrue(last.y() + last.h() <= H, "菜单末项不得低于屏幕底");
        int anchorBottom = MenuLayout.buttonCenterY(H, MenuLayout.BTN_COUNT - 1);
        MenuLayout.Rect lastBottom = MenuLayout.menuItemRect(W, H, 3, anchorBottom, 2);
        assertTrue(lastBottom.y() + lastBottom.h() <= H);
    }

    @Test
    void childColumnSitsRightOfParentColumn() {
        assertTrue(MenuLayout.childColumnX(W, H) > MenuLayout.itemColumnX(W, H) + MenuLayout.itemW(H),
                "二级列必须在一级列右侧留出间隙");
        int anchorTop = MenuLayout.buttonCenterY(H, 0);
        MenuLayout.Rect child = MenuLayout.childItemRect(W, H, 3, anchorTop, 0);
        assertTrue(child.y() >= 0);
        assertTrue(child.x() + child.w() <= W, "二级项不得越出屏幕右缘");
    }

    @Test
    void cardSitsLeftOfButtonsWithClamp() {
        int anchorY = MenuLayout.buttonCenterY(H, 0);
        MenuLayout.Rect card = MenuLayout.cardRect(W, H, anchorY);
        assertTrue(card.x() >= 0, "卡片不得越出屏幕左缘");
        assertTrue(card.x() + card.w() <= MenuLayout.firstButtonCenterX(W) + MenuLayout.btnSize(H) / 4,
                "卡片右缘不得超过按钮圆心右限");
        assertTrue(card.y() >= 0 && card.y() + card.h() <= H, "卡片垂直方向不得越界");
    }

    @Test
    void cardWidthShrinksOnNarrowScreens() {
        int narrowW = 200;
        MenuLayout.Rect card = MenuLayout.cardRect(narrowW, H, H / 2);
        assertTrue(card.x() >= 0);
        assertTrue(card.w() >= 60, "钳制后仍保持可读最小宽");
    }

    @Test
    void dotsAreCenteredNearBottom() {
        int x0 = MenuLayout.dotCenterX(W, H, 0);
        int xN = MenuLayout.dotCenterX(W, H, MenuLayout.DOT_COUNT - 1);
        assertEquals(W / 2, (x0 + xN) / 2);
        assertTrue(MenuLayout.dotCenterY(H) <= H);
        assertTrue(MenuLayout.dotCenterY(H) >= H * 9 / 10, "圆点应贴近底部");
    }

    @Test
    void dotHitTest() {
        int cx = MenuLayout.dotCenterX(W, H, 0);
        int cy = MenuLayout.dotCenterY(H);
        assertTrue(MenuLayout.inDot(W, H, 0, cx, cy));
        assertFalse(MenuLayout.inDot(W, H, 1, cx, cy));
    }

    @Test
    void itemsKeepClearanceFromButtonColumn() {
        int anchorY = MenuLayout.buttonCenterY(H, 0);
        MenuLayout.Rect first = MenuLayout.menuItemRect(W, H, 3, anchorY, 0);
        int btnRight = MenuLayout.firstButtonCenterX(W) + MenuLayout.btnSize(H) / 2;
        assertTrue(first.x() > btnRight, "菜单列必须整体位于按钮列右侧");
    }

    @Test
    void anchoredVariantsMatchConfigAnchor() {
        int ax = MenuLayout.firstButtonCenterX(W);
        int ay = MenuLayout.firstButtonCenterY(H);
        assertEquals(MenuLayout.itemColumnX(W, H), MenuLayout.itemColumnXAt(ax, H));
        assertEquals(MenuLayout.buttonCenterY(H, 2), MenuLayout.buttonCenterYAt(H, ay, 2));
        MenuLayout.Rect a = MenuLayout.menuItemRect(W, H, 3, ay, 1);
        MenuLayout.Rect b = MenuLayout.menuItemRectAt(W, H, 3, ax, ay, 1);
        assertEquals(a.x(), b.x());
        assertEquals(a.y(), b.y());
        MenuLayout.Rect ca = MenuLayout.cardRect(W, H, ay);
        MenuLayout.Rect cb = MenuLayout.cardRectAt(W, H, ax, ay);
        assertEquals(ca.x(), cb.x());
        assertEquals(ca.y(), cb.y());
        assertEquals(MenuLayout.hoveredMainButton(W, H, ax, MenuLayout.buttonCenterY(H, 1)),
                MenuLayout.hoveredMainButtonAt(W, H, ax, ay, ax, MenuLayout.buttonCenterY(H, 1)));
    }

    @Test
    void cursorAnchorFollowsMouseInsideFeasibleRange() {
        // W=427 H=240:可行区间约 [104, 289],光标在区间内锚点跟随光标
        assertEquals(200, MenuLayout.cursorAnchorX(W, H, 200));
        assertEquals(100, MenuLayout.cursorAnchorY(H, 100));
    }

    @Test
    void cursorAnchorClampsToKeepMenuVisible() {
        int axLeft = MenuLayout.cursorAnchorX(W, H, 10);
        assertTrue(axLeft >= 0 && axLeft <= W, "光标贴左时锚点仍应留在屏幕内");
        int axRight = MenuLayout.cursorAnchorX(W, H, W + 50);
        assertTrue(axRight >= 0 && axRight <= W, "光标贴右时锚点仍应留在屏幕内");
        // 左钳制后玩家卡(上限宽)必须完整可见
        assertTrue(axLeft - MenuLayout.btnSize(H) / 2 - Math.max(4, Math.round(MenuLayout.btnSize(H) * 0.55f))
                - MenuLayout.cardW(H) >= 0, "玩家卡不得越出左缘");
        // 右钳制后两级菜单列必须完整可见
        assertTrue(axRight + MenuLayout.btnSize(H) / 2 + MenuLayout.arrowGap(H) + MenuLayout.itemW(H)
                + MenuLayout.childGap(H) + MenuLayout.arrowGap(H) / 2 + MenuLayout.itemW(H) <= W,
                "两级菜单列不得越出右缘");
        int ayTop = MenuLayout.cursorAnchorY(H, -10);
        assertTrue(ayTop >= MenuLayout.btnSize(H) / 2, "首按钮必须完整可见");
        int ayBottom = MenuLayout.cursorAnchorY(H, H + 50);
        assertTrue(ayBottom + (MenuLayout.BTN_COUNT - 1) * MenuLayout.btnStep(H) + MenuLayout.btnSize(H) / 2 <= H,
                "末按钮必须完整可见");
    }

    @Test
    void equipColumnSitsRightOfChildColumn() {
        // 装备条目列(第三列)必须完整落在二级列右侧,不与其重叠
        int ax = MenuLayout.firstButtonCenterX(W);
        int childRight = MenuLayout.childColumnXAt(ax, H) + MenuLayout.itemW(H);
        assertTrue(MenuLayout.equipColumnXAt(ax, H) >= childRight + MenuLayout.equipGap(H),
                "第三列应在二级列右侧并留间隙");
        assertTrue(MenuLayout.equipItemW(H) == MenuLayout.itemW(H), "条目宽度与普通菜单项一致");
        assertTrue(MenuLayout.equipItemH(H) <= MenuLayout.itemH(H),
                "装备条目应比普通菜单项矮一号");
        assertTrue(MenuLayout.equipItemGap(H) >= 1 && MenuLayout.equipItemGap(H) <= MenuLayout.itemGap(H),
                "装备条目间距应更紧凑");
    }

    @Test
    void equipColumnStacksVerticallyAroundAnchor() {
        int ax = MenuLayout.firstButtonCenterX(W);
        int anchorY = 120;
        int count = 4;
        int step = MenuLayout.equipItemH(H) + MenuLayout.equipItemGap(H);
        int total = (count - 1) * step + MenuLayout.equipItemH(H);
        MenuLayout.Rect first = MenuLayout.equipItemRectAt(W, H, count, ax, anchorY, 0);
        MenuLayout.Rect last = MenuLayout.equipItemRectAt(W, H, count, ax, anchorY, count - 1);
        assertEquals(first.y() + total - MenuLayout.equipItemH(H), last.y(),
                "末条目应紧贴整列末端");
        assertEquals(first.x(), last.x(), "同列条目 X 一致");
        assertTrue(first.y() + first.h() / 2 >= anchorY - total / 2 - 1
                        && first.y() + first.h() / 2 <= anchorY + total / 2 + 1,
                "整列应围绕锚点展开");
    }

    @Test
    void equipColumnClampsToScreen() {
        int ax = MenuLayout.firstButtonCenterX(W);
        // 锚点贴顶/贴底时整列不越出屏幕
        MenuLayout.Rect top = MenuLayout.equipItemRectAt(W, H, 5, ax, 0, 0);
        assertTrue(top.y() >= 2, "贴顶时首条目不得越出上缘");
        MenuLayout.Rect bottom = MenuLayout.equipItemRectAt(W, H, 5, ax, H, 4);
        assertTrue(bottom.y() + bottom.h() <= H - 1, "贴底时末条目不得越出下缘");
        // 单条(暂无装备占位)也不崩
        MenuLayout.Rect single = MenuLayout.equipItemRectAt(W, H, 1, ax, H / 2, 0);
        assertEquals(single.x(), MenuLayout.equipColumnXAt(ax, H));
    }

    @Test
    void offhandDotSitsFirstWithAGap() {
        // index 0 = 副手(最左),与主栏第一格之间隔开一档;主栏其余圆心等距
        int x0 = MenuLayout.dotCenterX(W, H, 0);
        int x1 = MenuLayout.dotCenterX(W, H, 1);
        int gap = x1 - x0 - MenuLayout.dotStep(H);
        assertTrue(gap >= 3, "副手与主栏应有可见间隔,实际 " + gap);
        for (int i = 2; i < MenuLayout.DOT_COUNT; i++) {
            assertEquals(MenuLayout.dotStep(H),
                    MenuLayout.dotCenterX(W, H, i) - MenuLayout.dotCenterX(W, H, i - 1),
                    0.001f, "主栏圆心距应恒为 step: i=" + i);
        }
        // 副手在最左端,整组关于屏幕中线大体居中
        int xLast = MenuLayout.dotCenterX(W, H, MenuLayout.DOT_COUNT - 1);
        assertTrue(x0 < xLast, "副手应在最左");
        assertTrue(Math.abs((x0 + xLast) / 2f - W / 2f) <= 2, "整组应居中");
    }

    @Test
    void hotbarScaleGrowsDotsAndKeepsBaseEdge() {
        int baseSize = MenuLayout.dotSize(H);
        float baseEdge = MenuLayout.dotCenterY(H) + MenuLayout.dotSize(H) * 0.5f;
        SAOConfig.setHotbarScale(1.5f);
        try {
            assertTrue(MenuLayout.dotSize(H) > baseSize, "调大物品栏缩放后圆点应变大");
            float edgeBig = MenuLayout.dotCenterY(H) + MenuLayout.dotSize(H) * 0.5f;
            assertEquals(baseEdge, edgeBig, 1.0f, "放大后底缘应保持不变");
        } finally {
            SAOConfig.setHotbarScale(0.7f);
        }
        try {
            assertTrue(MenuLayout.dotSize(H) < baseSize, "调小物品栏缩放后圆点应变小");
            float edgeSmall = MenuLayout.dotCenterY(H) + MenuLayout.dotSize(H) * 0.5f;
            assertEquals(baseEdge, edgeSmall, 1.0f, "缩小后底缘应保持不变");
        } finally {
            SAOConfig.setHotbarScale(1.0f);
        }
    }
}
