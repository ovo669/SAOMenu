package com.sao.saomenu.client;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3D 环绕血条回归:视线锥门控、弧带几何、淡入淡出节奏。
 */
class SAOTargetBar3DTest {

    @Test
    void looksAwayHidesBar() {
        // 2 格外的僵尸:角半径约 0.45 rad,视线偏 1.2 rad 完全在轮廓外
        float ar = SAOTargetBar3D.angularRadius(0.6f, 1.95f, 2.0);
        assertEquals(0f, SAOTargetBar3D.lookFactor(1.2f, ar), 0.001f, "视线大幅偏开不显示");
        assertEquals(0f, SAOTargetBar3D.lookFactor(
                        ar + SAOTargetBar3D.LOOK_FADE_MARGIN, ar), 0.001f,
                "刚到淡出边界为 0");
    }

    @Test
    void looksAtTargetShowsFullBar() {
        float ar = SAOTargetBar3D.angularRadius(0.6f, 1.95f, 6.0);
        assertEquals(1f, SAOTargetBar3D.lookFactor(0f, ar), 0.001f, "正对中心完全显示");
        assertEquals(1f, SAOTargetBar3D.lookFactor(ar, ar), 0.001f,
                "视线落在轮廓边缘也算看向它");
    }

    @Test
    void lookFactorRampsBetweenThresholds() {
        float ar = 0.1f;
        float full = ar + SAOTargetBar3D.LOOK_FULL_MARGIN;
        float fade = ar + SAOTargetBar3D.LOOK_FADE_MARGIN;
        float f = SAOTargetBar3D.lookFactor((full + fade) / 2f, ar);
        assertTrue(f > 0.4f && f < 0.6f, "两阈值中点应约为 0.5,实际 " + f);
    }

    @Test
    void lookFactorDecaysMonotonically() {
        float ar = 0.12f;
        float prev = 2f;
        for (float ang = 0f; ang <= 1.0f; ang += 0.01f) {
            float f = SAOTargetBar3D.lookFactor(ang, ar);
            assertTrue(f <= prev + 1e-4f, "视线越偏,强度不应回升: angle=" + ang);
            prev = f;
        }
    }

    @Test
    void closeTargetsAreEasierToLookAt() {
        // 同一只僵尸,近处角半径更大 → 允许更大的视线偏差
        float near = SAOTargetBar3D.angularRadius(0.6f, 1.95f, 1.5);
        float far = SAOTargetBar3D.angularRadius(0.6f, 1.95f, 20.0);
        assertTrue(near > far, "近处角半径更大");
        float angle = 0.3f;
        assertTrue(SAOTargetBar3D.lookFactor(angle, near)
                        > SAOTargetBar3D.lookFactor(angle, far),
                "同样偏差角下,近处目标更容易被判定为看向");
    }

    @Test
    void angularRadiusUsesLargerBodyDimension() {
        // 高瘦生物按高度算张角,矮宽生物按宽度算
        float tall = SAOTargetBar3D.angularRadius(0.6f, 2.9f, 5.0);
        float wide = SAOTargetBar3D.angularRadius(2.9f, 0.6f, 5.0);
        assertEquals(tall, wide, 0.001f, "取较大边,朝向无关");
        assertTrue(SAOTargetBar3D.angularRadius(0.6f, 1.95f, 0.1) > 0.5f,
                "贴脸时张角很大");
    }

    @Test
    void arcSpansHalfOfPreviousLength() {
        float span = SAOTargetBar3D.arcSpan();
        float totalDeg = SAOTargetBar3D.ARC_DEGREES;
        assertTrue(totalDeg <= 100f, "总弧应控制在 100° 内,实际 " + totalDeg + "°");
        assertTrue(totalDeg >= 80f, "总弧不应短到看不出形状,实际 " + totalDeg + "°");
        assertTrue(span > 0f && span < Mth.TWO_PI);
    }

    @Test
    void fillAngleScalesWithHealth() {
        assertEquals(0f, SAOTargetBar3D.fillAngle(0f), 0.001f);
        assertEquals(SAOTargetBar3D.arcSpan(), SAOTargetBar3D.fillAngle(1f), 0.001f);
        assertEquals(SAOTargetBar3D.arcSpan() / 2f, SAOTargetBar3D.fillAngle(0.5f), 0.001f);
    }

    @Test
    void fillAngleClampsOutOfRangeHealth() {
        assertEquals(0f, SAOTargetBar3D.fillAngle(-1f), 0.001f);
        assertEquals(SAOTargetBar3D.arcSpan(), SAOTargetBar3D.fillAngle(9f), 0.001f);
    }

    @Test
    void arcMidpointAnchorsToBodyFacing() {
        // 弧带锚定生物身体朝向、随生物转身而转:中点角 = 90° − bodyRot。
        // MC 朝向向量 (sin yRot, cos yRot) 与弧角方向 (cos t, sin t) 重合即得。
        double[][] cases = {
                {0, 90}, {90, 0}, {180, -90}, {270, -180}, {45, 45}, {-30, 120}
        };
        for (double[] c : cases) {
            float bodyRot = (float) c[0];
            float expected = (float) Math.toRadians(c[1]);
            float mid = SAOTargetBar3D.arcStart((float) Math.toRadians(90.0 - bodyRot))
                + SAOTargetBar3D.arcSpan() / 2f;
            assertEquals(expected, mid, 0.0001f, "bodyRot=" + bodyRot + "° 时弧中点应朝向生物正前方");
        }
    }

    @Test
    void arcStartTurnsWithTheBody() {
        // 身体连续转身时,弧中点每步恒定跟随 −5°(跨 ±180° 环绕边界也不破)
        float prev = SAOTargetBar3D.arcStart((float) Math.toRadians(90.0));
        for (float rot = 5f; rot <= 360f; rot += 5f) {
            float cur = SAOTargetBar3D.arcStart((float) Math.toRadians(90.0 - rot));
            assertEquals(-5f * Mth.DEG_TO_RAD, normalizedDiff(cur - prev), 0.001f,
                    "身体每转 5°,弧中点应反向跟随 5°: rot=" + rot);
            prev = cur;
        }
    }

    @Test
    void fillAnchorsToOneEndAndNeverOverlapsTheEmptyTrack() {
        // 血量段锚定主弧高角端(屏幕最左),向低角方向生长;
        // 空槽段占剩下的低角区间,两段角度区间不相交——否则渲染器不按提交顺序
        // 出批时(光影)其中一段会盖掉另一段
        float start = SAOTargetBar3D.arcStart(0f);
        float span = SAOTargetBar3D.arcSpan();
        assertEquals(start + span, SAOTargetBar3D.fillStart(start, 0f), 0.0001f,
                "空血时填充段退化到高角端");
        assertEquals(start, SAOTargetBar3D.fillStart(start, 1f), 0.0001f,
                "满血时填充段铺满整条主弧");
        for (float frac = 0f; frac <= 1f; frac += 0.05f) {
            float fillFrom = SAOTargetBar3D.fillStart(start, frac);
            float emptyEnd = start + span - SAOTargetBar3D.fillAngle(frac);
            assertEquals(emptyEnd, fillFrom, 0.0001f,
                    "空槽段末端应正好接上填充段起点: frac=" + frac);
            assertTrue(fillFrom >= start - 1e-4f && fillFrom + SAOTargetBar3D.fillAngle(frac)
                            <= start + span + 1e-4f,
                    "填充段不应越出主弧: frac=" + frac);
        }
    }

    @Test
    void readoutBandLeavesRoomBetweenTheLightEdges() {
        // 上下亮边条各占带高的固定比例,中间必须给血量读数区留下净空间;
        // 三段纵向不重叠是「光影下血量不被白边盖住」的前提
        for (float w : new float[]{0.05f, 0.6f, 1.2f, 4f}) {
            float bandH = SAOTargetBar3D.bandHeight(w);
            float edge = SAOTargetBar3D.edgeHeight(bandH);
            assertTrue(edge > 0f, "亮边条应有正高度,bbWidth=" + w);
            assertTrue(bandH - edge * 2f > 0f,
                    "读数区高度应为正,bbWidth=" + w + " bandH=" + bandH + " edge=" + edge);
            assertTrue(bandH - edge * 2f > edge,
                    "读数区应比单条亮边更高,bbWidth=" + w);
        }
    }

    @Test
    void bandShellHasPositiveThickness() {
        // 内外两个同心面构成壳体,厚度即半径差;为 0 会退化成单面、丢掉环绕感
        assertTrue(SAOTargetBar3D.BAND_THICK > 0,
                "壳体应有径向厚度,实际 " + SAOTargetBar3D.BAND_THICK);
        double rIn = SAOTargetBar3D.ringRadius(0.9f);
        assertTrue(rIn + SAOTargetBar3D.BAND_THICK > rIn, "外沿应大于内沿");
    }

    @Test
    void proximityFadeKillsRingOnlyWhenCameraIsOnTopOfIt() {
        // 正常观察距离(>2.8 格)环带完全显示;贴脸(<1.6 格)环带收掉,
        // 之间平滑过渡——避免近景时环带被透视放大成「漂在旁边的大片弧」
        assertEquals(1f, SAOTargetBar3D.proximityFade(5.0), 0.001f);
        assertEquals(1f, SAOTargetBar3D.proximityFade(2.8), 0.001f, "淡出终点之外不受影响");
        assertEquals(0f, SAOTargetBar3D.proximityFade(1.6), 0.001f, "贴脸完全收掉");
        assertEquals(0f, SAOTargetBar3D.proximityFade(0.5), 0.001f);
        float mid = SAOTargetBar3D.proximityFade(2.2);
        assertTrue(mid > 0.3f && mid < 0.7f, "中点应约为 0.5,实际 " + mid);
        assertTrue(SAOTargetBar3D.proximityFade(1.8) < SAOTargetBar3D.proximityFade(2.6),
                "越近越透明");
    }

    private static float normalizedDiff(float angle) {
        float a = angle % Mth.TWO_PI;
        if (a > Math.PI) {
            a -= Mth.TWO_PI;
        }
        if (a < -Math.PI) {
            a += Mth.TWO_PI;
        }
        return a;
    }

    @Test
    void ringRadiusGrowsWithBodyWidth() {
        double small = SAOTargetBar3D.ringRadius(0.4f);
        double large = SAOTargetBar3D.ringRadius(2.0f);
        assertTrue(large > small, "身体越宽环越大");
        assertTrue(small >= 0.42, "瘦长生物也有半径下限,实际 " + small);
        // 明显离开身体:半径至少比半身宽大 0.7 格
        assertTrue(SAOTargetBar3D.ringRadius(0.9f) > 0.9f * 0.5 + 0.7,
                "环应与身体保持明显间隙");
    }

    @Test
    void bandHeightScalesWithWidthNotHeight() {
        // 巨人:体高 12+ 但身宽与僵尸相近 → 带厚不应显著超过僵尸
        float zombie = SAOTargetBar3D.bandHeight(0.6f);
        float giant = SAOTargetBar3D.bandHeight(1.2f);
        float enderman = SAOTargetBar3D.bandHeight(0.6f);
        assertEquals(zombie, enderman, 0.001f, "同身宽不同体高,带厚应一致");
        assertTrue(giant <= SAOTargetBar3D.BAND_H_MAX, "巨物钳到上限,实际 " + giant);
        assertTrue(SAOTargetBar3D.bandHeight(0.05f) >= SAOTargetBar3D.BAND_H_MIN,
                "极瘦生物钳到下限");
    }

    @Test
    void alphaFadesInAndOutByStep() {
        assertEquals(SAOTargetBar3D.FADE_STEP, SAOTargetBar3D.stepAlpha(0f, 1f), 0.001f,
                "淡入一步走固定步长");
        assertEquals(0f, SAOTargetBar3D.stepAlpha(SAOTargetBar3D.FADE_STEP, 0f), 0.001f,
                "淡出一步回到 0");
        assertEquals(1f, SAOTargetBar3D.stepAlpha(0.99f, 1f), 0.001f, "不会越过目标值");
        assertEquals(0.5f, SAOTargetBar3D.stepAlpha(0.5f, 0.5f), 0.001f, "已达目标保持不动");
    }

    @Test
    void alphaReachesFullInBoundedTicks() {
        float a = 0f;
        int ticks = 0;
        while (a < 1f && ticks < 100) {
            a = SAOTargetBar3D.stepAlpha(a, 1f);
            ticks++;
        }
        assertEquals(1f, a, 0.001f);
        assertTrue(ticks <= 10, "应在 10 tick 内完成淡入,实际 " + ticks);
    }

    @Test
    void bandRotationIsDampedAndTakesTheShortestWay() {
        // 怪物 AI 每 tick 调整朝向,弧带若直接跟随会疯转;
        // 限速阻尼后每步最多走 ROT_SPEED_DEG_PER_TICK
        float step = SAOTargetBar3D.ROT_SPEED_DEG_PER_TICK;
        assertEquals(step, SAOTargetBar3D.approachAngle(0f, 100f, step), 0.001f,
                "大幅偏离时一步只推进限速值");
        assertEquals(-step, SAOTargetBar3D.approachAngle(0f, -100f, step), 0.001f,
                "反向偏离同理");
        assertEquals(2f, SAOTargetBar3D.approachAngle(0f, 2f, step), 0.001f,
                "限速内的目标一步到位,不越过");
        assertEquals(350f + step, SAOTargetBar3D.approachAngle(350f, 10f, step), 0.001f,
                "跨 ±180° 边界应沿最短方向推进");
        float cur = 0f;
        for (int i = 0; i < 100; i++) {
            cur = SAOTargetBar3D.approachAngle(cur, 10f, step);
        }
        assertEquals(10f, cur, 0.001f, "多步追赶应收敛到目标角");
    }

    @Test
    void scoreFavoursAimedTargetOverCloserOne() {
        // 远处但准星对准 vs 近处但没看:对准的该胜出
        float aimedFar = SAOTargetBar3D.targetScore(1f, 30);
        float unaimedNear = SAOTargetBar3D.targetScore(0f, 2);
        assertTrue(aimedFar > unaimedNear, "视线对准应比单纯距离近更优先");
    }

    @Test
    void scoreFavoursCloserAtEqualAim() {
        float near = SAOTargetBar3D.targetScore(0.8f, 4);
        float far = SAOTargetBar3D.targetScore(0.8f, 35);
        assertTrue(near > far, "同等对准程度下近的优先");
    }

    @Test
    void scoreStaysNormalised() {
        assertEquals(1f, SAOTargetBar3D.targetScore(1f, 0), 0.001f, "满分=正对且贴身");
        assertEquals(0f, SAOTargetBar3D.targetScore(0f, SAOTargetBar3D.MAX_DISTANCE), 0.001f,
                "零分=没看且在最远处");
        for (float g = 0f; g <= 1f; g += 0.25f) {
            for (double d = 0; d <= SAOTargetBar3D.MAX_DISTANCE; d += 7) {
                float sc = SAOTargetBar3D.targetScore(g, d);
                assertTrue(sc >= 0f && sc <= 1f, "评分应落在 [0,1]:" + sc);
            }
        }
    }

    @Test
    void targetCapIsThree() {
        assertEquals(3, SAOTargetBar3D.MAX_TARGETS, "同屏最多 3 个目标");
    }
}
