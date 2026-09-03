package com.sao.saomenu.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置持久化回归:默认值、范围钳制、JSON 往返、布局联动。
 */
class SAOConfigTest {

    @TempDir
    Path tmp;

    @AfterEach
    void restoreDefaults() {
        SAOConfig.reset();
    }

    @Test
    void defaultsMatchReferenceScreenshot() {
        assertEquals(MenuLayout.ANCHOR_X_FRAC, SAOConfig.anchorX());
        assertEquals(MenuLayout.ANCHOR_Y_FRAC, SAOConfig.anchorY());
        assertEquals(1f, SAOConfig.menuScale());
        assertEquals(1f, SAOConfig.bobAmp());
        assertTrue(SAOConfig.sounds());
        assertTrue(SAOConfig.hideHotbar());
        assertTrue(SAOConfig.showHud());
        assertTrue(SAOConfig.showAvatar());
        assertFalse(SAOConfig.anchorFollowMouse(), "菜单位置固定,不跟随鼠标");
        assertTrue(SAOConfig.showTargetBar());
        assertTrue(SAOConfig.showDamageNumbers());
        assertTrue(SAOConfig.saoToasts());
    }

    @Test
    void settersClampToBounds() {
        SAOConfig.setAnchorX(2f);
        assertEquals(SAOConfig.ANCHOR_MAX, SAOConfig.anchorX());
        SAOConfig.setAnchorX(-1f);
        assertEquals(SAOConfig.ANCHOR_MIN, SAOConfig.anchorX());
        SAOConfig.setMenuScale(99f);
        assertEquals(SAOConfig.SCALE_MAX, SAOConfig.menuScale());
        SAOConfig.setBobAmp(-5f);
        assertEquals(SAOConfig.BOB_MIN, SAOConfig.bobAmp());
    }

    @Test
    void menuLayoutReadsConfigAnchor() {
        SAOConfig.setAnchorX(0.5f);
        assertEquals(100, MenuLayout.firstButtonCenterX(200));
        SAOConfig.setAnchorY(0.5f);
        assertEquals(120, MenuLayout.firstButtonCenterY(240));
    }

    @Test
    void saveAndLoadRoundTrip() {
        SAOConfig.setAnchorX(0.44f);
        SAOConfig.setAnchorY(0.55f);
        SAOConfig.setMenuScale(1.2f);
        SAOConfig.setBobAmp(2f);
        SAOConfig.setSounds(false);
        SAOConfig.setHideHotbar(false);
        SAOConfig.setShowHud(false);
        SAOConfig.setSaoToasts(false);
        Path file = tmp.resolve("saomenu.json");
        SAOConfig.save(file);
        assertTrue(java.nio.file.Files.exists(file));

        SAOConfig.reset();
        SAOConfig.load(file);
        assertEquals(0.44f, SAOConfig.anchorX());
        assertEquals(0.55f, SAOConfig.anchorY());
        assertEquals(1.2f, SAOConfig.menuScale());
        assertEquals(2f, SAOConfig.bobAmp());
        assertFalse(SAOConfig.sounds());
        assertFalse(SAOConfig.hideHotbar());
        assertFalse(SAOConfig.showHud());
        assertFalse(SAOConfig.saoToasts());
    }

    @Test
    void loadMissingFileKeepsDefaults() {
        SAOConfig.load(tmp.resolve("nope.json"));
        assertEquals(MenuLayout.ANCHOR_X_FRAC, SAOConfig.anchorX());
        assertTrue(SAOConfig.sounds());
    }

    @Test
    void accentDefaultsToSaoOrange() {
        assertEquals(0xFFEFA603, SAOConfig.accent(), "默认主题色应为 SAO 橙");
        assertEquals(41.44f, SAOConfig.accentHue(), 0.01f);
    }

    @Test
    void accentHueRotatesColor() {
        SAOConfig.setAccentHue(0f);
        assertEquals(0xFFEF0303, SAOConfig.accent(), "色相 0 应为红");
        SAOConfig.setAccentHue(999f);
        assertEquals(360f, SAOConfig.accentHue(), "色相应钳制到 360");
        SAOConfig.setAccentHue(-5f);
        assertEquals(0f, SAOConfig.accentHue(), "色相应钳制到 0");
    }

    @Test
    void accentHuePersistsInJson() {
        SAOConfig.setAccentHue(200f);
        Path file = tmp.resolve("accent.json");
        SAOConfig.save(file);
        SAOConfig.reset();
        SAOConfig.load(file);
        assertEquals(200f, SAOConfig.accentHue(), 0.01f);
    }

    @Test
    void partialJsonFallsBackToDefaultsForMissingFields() throws Exception {
        Path file = tmp.resolve("partial.json");
        java.nio.file.Files.writeString(file,
                "{\"anchorX\":0.5,\"anchorY\":0.5}");
        SAOConfig.load(file);
        assertEquals(0.5f, SAOConfig.anchorX());
        assertEquals(0.5f, SAOConfig.anchorY());
        assertEquals(SAOConfig.DEF_MENU_SCALE, SAOConfig.menuScale(), "缺失字段应回退默认");
        assertTrue(SAOConfig.sounds(), "缺失布尔字段应回退默认 true");
        assertEquals(SAOConfig.DEF_ACCENT_HUE, SAOConfig.accentHue(), 0.01f, "缺失 accentHue 应回退默认");
    }
}
