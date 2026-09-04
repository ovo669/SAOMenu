package com.sao.saomenu.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sao.saomenu.SAOMenu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端配置:纯数据 + Gson 持久化(不依赖 Minecraft 类,可单元测试)。
 *
 * <p>由 {@link SAOKeybinds} 在客户端初始化时调用 {@link #load(Path)},
 * {@link SAOConfigScreen} 修改后调用 {@link #save(Path)} 落盘到
 * {@code config/saomenu.json}。布局数学(MenuLayout)、渲染与 HUD 只读配置。</p>
 */
public final class SAOConfig {

    // ------------------------------------------------------------ 默认值(参考截图实测)
    public static final float DEF_ANCHOR_X = MenuLayout.ANCHOR_X_FRAC;
    public static final float DEF_ANCHOR_Y = MenuLayout.ANCHOR_Y_FRAC;
    public static final float DEF_MENU_SCALE = 1f;
    public static final float DEF_BOB_AMP = 1f;

    // 范围
    public static final float ANCHOR_MIN = 0.05f;
    public static final float ANCHOR_MAX = 0.95f;
    public static final float SCALE_MIN = 0.6f;
    public static final float SCALE_MAX = 1.5f;
    public static final float BOB_MIN = 0f;
    public static final float BOB_MAX = 3f;
    /** 死亡碎裂粒子密度倍率范围。 */
    public static final float SHATTER_MIN = 0.2f;
    public static final float SHATTER_MAX = 2.5f;
    public static final float DEF_SHATTER_DENSITY = 1f;

    /** 主题色(色相 0-360)。默认 41.44° = SAO 橙 #EFA603。 */
    public static final float DEF_ACCENT_HUE = 41.44f;

    /** 血条板默认锚点(屏幕比例;SAO 原版位置=左上角)。 */
    public static final float DEF_PLATE_PANEL_X = 0f;
    public static final float DEF_PLATE_PANEL_Y = 0f;

    /** 时钟默认常显;开启「仅菜单内显示」后随菜单关闭而隐藏。 */
    public static final boolean DEF_CLOCK_MENU_ONLY = false;

    /** 按住 W 自动疾跑(程序性按住疾跑键,原版条件照常生效),默认开。 */
    public static final boolean DEF_AUTO_SPRINT = true;

    /** 隐藏原版血条,饥饿条居中(氧气泡保留),默认开。 */
    public static final boolean DEF_HIDE_VANILLA_HEALTH = true;

    /** 饥饿条锚点默认值:X 居中、Y 贴原版行高(与未拖动前位置一致)。 */
    public static final float DEF_FOOD_PANEL_X = 0.5f;
    public static final float DEF_FOOD_PANEL_Y = 1f;

    /** 地图面板默认锚点(屏幕比例;参照动画里地图卡浮在人物左前方)。 */
    public static final float DEF_MAP_PANEL_X = 0.10f;
    public static final float DEF_MAP_PANEL_Y = 0.28f;

    /** 时钟面板默认锚点(屏幕比例;默认顶部居中偏右,接近旧时钟位置)。 */
    public static final float DEF_CLOCK_PANEL_X = 0.42f;
    public static final float DEF_CLOCK_PANEL_Y = 0.04f;

    /** 时钟大小倍率范围。 */
    public static final float CLOCK_SCALE_MIN = 0.5f;
    public static final float CLOCK_SCALE_MAX = 2.0f;
    public static final float DEF_CLOCK_SCALE = 1.0f;

    /** 底部圆点物品栏大小倍率范围。 */
    public static final float HOTBAR_MIN = 0.6f;
    public static final float HOTBAR_MAX = 2.4f;
    public static final float DEF_HOTBAR_SCALE = 1.3f;
    /** 第三人称菜单板(打开菜单时角色面前出现 SAO 菜单,F5 可见)默认开。 */
    public static final boolean DEF_THIRD_PERSON = true;
    /** Boss「Immortal Object」横幅默认开。 */
    public static final boolean DEF_BOSS_BANNER = true;

    private static float anchorX = DEF_ANCHOR_X;
    private static float anchorY = DEF_ANCHOR_Y;
    private static float menuScale = DEF_MENU_SCALE;
    private static float bobAmp = DEF_BOB_AMP;
    private static boolean sounds = true;
    private static boolean hideHotbar = true;
    private static boolean showHud = true;
    private static boolean showAvatar = true;
    private static boolean anchorFollowMouse = false;
    private static boolean showTargetBar = true;
    private static boolean showDamageNumbers = true;
    private static boolean saoToasts = true;
    private static boolean showClock = true;
    private static boolean clock24h = true;
    private static boolean clockDate = false;
    private static boolean showWelcome = true;
    private static boolean deathShatter = true;
    private static float deathShatterDensity = 1f;
    private static float accentHue = DEF_ACCENT_HUE;
    private static float mapPanelX = DEF_MAP_PANEL_X;
    private static float mapPanelY = DEF_MAP_PANEL_Y;
    private static boolean mapPinned = false;
    private static float clockPanelX = DEF_CLOCK_PANEL_X;
    private static float clockPanelY = DEF_CLOCK_PANEL_Y;
    private static float clockScale = DEF_CLOCK_SCALE;
    private static float hotbarScale = DEF_HOTBAR_SCALE;
    private static boolean thirdPersonMenu = DEF_THIRD_PERSON;
    private static boolean showBossBanner = DEF_BOSS_BANNER;
    private static float platePanelX = DEF_PLATE_PANEL_X;
    private static float platePanelY = DEF_PLATE_PANEL_Y;
    private static boolean clockOnlyInMenu = DEF_CLOCK_MENU_ONLY;
    private static boolean autoSprint = DEF_AUTO_SPRINT;
    private static boolean hideVanillaHealth = DEF_HIDE_VANILLA_HEALTH;
    private static float foodPanelX = DEF_FOOD_PANEL_X;
    private static float foodPanelY = DEF_FOOD_PANEL_Y;
    /** 置顶物品(注册名),按加入顺序排在物品列最前。 */
    private static final java.util.List<String> pinnedItems = new java.util.ArrayList<>();
    /** 手动拖动排出的顺序(注册名);与置顶无关,不带角标。 */
    private static final java.util.List<String> itemOrder = new java.util.ArrayList<>();
    private static boolean hasOpenedSettings = false; // 是否打开过设置界面

    private static Path loadedFrom;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SAOConfig() {
    }

    // ------------------------------------------------------------ 读取(布局/渲染用)

    public static float anchorX() {
        return anchorX;
    }

    public static float anchorY() {
        return anchorY;
    }

    public static float menuScale() {
        return menuScale;
    }

    public static float bobAmp() {
        return bobAmp;
    }

    public static boolean sounds() {
        return sounds;
    }

    public static boolean hideHotbar() {
        return hideHotbar;
    }

    public static boolean showHud() {
        return showHud;
    }

    /** 血条板名字下方是否显示皮肤头像。 */
    public static boolean showAvatar() {
        return showAvatar;
    }

    /** 菜单是否在鼠标位置打开(参照 SAO_Utils;关闭时使用锚点 X/Y)。 */
    public static boolean anchorFollowMouse() {
        return anchorFollowMouse;
    }

    /** 准星对准目标时是否显示目标血条。 */
    public static boolean showTargetBar() {
        return showTargetBar;
    }

    /** 是否显示伤害数字。 */
    public static boolean showDamageNumbers() {
        return showDamageNumbers;
    }

    /** 当前主题色 ARGB(由色相实时换算)。 */
    public static int accent() {
        return hsvToRgb(accentHue, 0.987f, 0.937f);
    }

    /** 地图面板锚点 X(屏幕比例 0-1,拖动后持久化)。 */
    public static float mapPanelX() {
        return mapPanelX;
    }

    /** 地图面板锚点 Y(屏幕比例 0-1)。 */
    public static float mapPanelY() {
        return mapPanelY;
    }

    /** 地图面板是否图钉固定(关菜单后仍显示)。 */
    public static boolean mapPinned() {
        return mapPinned;
    }

    /** 时钟面板锚点 X(屏幕比例 0-1,拖动后持久化)。 */
    public static float clockPanelX() {
        return clockPanelX;
    }

    /** 时钟面板锚点 Y(屏幕比例 0-1)。 */
    public static float clockPanelY() {
        return clockPanelY;
    }

    /** 时钟大小倍率(0.5-2.0)。 */
    public static float clockScale() {
        return clockScale;
    }

    /** 底部圆点物品栏大小倍率(0.6-1.6)。 */
    public static float hotbarScale() {
        return hotbarScale;
    }

    /** 打开菜单时是否在角色面前渲染世界空间菜单板(第三人称可见)。 */
    public static boolean thirdPersonMenu() {
        return thirdPersonMenu;
    }

    /** 视线对准 Boss 时是否显示「Immortal Object」横幅。 */
    public static boolean showBossBanner() {
        return showBossBanner;
    }

    /** 血条板锚点(屏幕比例;SAO 原版位置 = 左上角 (0,0))。 */
    public static float platePanelX() {
        return platePanelX;
    }

    public static float platePanelY() {
        return platePanelY;
    }

    public static float accentHue() {
        return accentHue;
    }

    public static boolean saoToasts() {
        return saoToasts;
    }

    public static boolean showClock() {
        return showClock;
    }

    public static boolean clock24h() {
        return clock24h;
    }

    public static boolean clockDate() {
        return clockDate;
    }

    /** 进入世界时是否播放 SAO 欢迎动画。 */
    public static boolean showWelcome() {
        return showWelcome;
    }

    /** 生物死亡时是否播放 SAO 碎裂特效。 */
    public static boolean deathShatter() {
        return deathShatter;
    }

    /** 碎裂粒子密度倍率(1.0 为默认)。 */
    public static float deathShatterDensity() {
        return deathShatterDensity;
    }
    
    /** 是否打开过设置界面(用于判断是否播放完整转场动画)。 */
    public static boolean hasOpenedSettings() {
        return hasOpenedSettings;
    }
    
    /** 标记已打开过设置界面。 */
    public static void markSettingsOpened() {
        hasOpenedSettings = true;
    }

    /** HSV(H,1,1)→ARGB,颜色分量随色相旋转。 */
    private static int hsvToRgb(float hue, float s, float v) {
        float c = v * s;
        float hp = (hue % 360f) / 60f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r = 0f;
        float g = 0f;
        float b = 0f;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        float m = v - c;
        return 0xFF000000
                | (Math.round((r + m) * 255f) << 16)
                | (Math.round((g + m) * 255f) << 8)
                | Math.round((b + m) * 255f);
    }

    // ------------------------------------------------------------ 修改(带钳制;由界面负责 save)

    public static void setAnchorX(float v) {
        anchorX = clamp(v, ANCHOR_MIN, ANCHOR_MAX);
    }

    public static void setAnchorY(float v) {
        anchorY = clamp(v, ANCHOR_MIN, ANCHOR_MAX);
    }

    public static void setMenuScale(float v) {
        menuScale = clamp(v, SCALE_MIN, SCALE_MAX);
    }

    public static void setBobAmp(float v) {
        bobAmp = clamp(v, BOB_MIN, BOB_MAX);
    }

    public static void setSounds(boolean v) {
        sounds = v;
    }

    public static void setHideHotbar(boolean v) {
        hideHotbar = v;
    }

    public static void setShowHud(boolean v) {
        showHud = v;
    }

    public static void setShowAvatar(boolean v) {
        showAvatar = v;
    }

    public static void setAnchorFollowMouse(boolean v) {
        anchorFollowMouse = v;
    }

    public static void setShowTargetBar(boolean v) {
        showTargetBar = v;
    }

    public static void setShowDamageNumbers(boolean v) {
        showDamageNumbers = v;
    }

    public static void setAccentHue(float v) {
        accentHue = clamp(v, 0f, 360f);
    }

    public static void setMapPanelX(float v) {
        mapPanelX = clamp(v, 0f, 1f);
    }

    public static void setMapPanelY(float v) {
        mapPanelY = clamp(v, 0f, 1f);
    }

    public static void setMapPinned(boolean v) {
        mapPinned = v;
    }

    public static void setClockPanelX(float v) {
        clockPanelX = clamp(v, 0f, 1f);
    }

    public static void setClockPanelY(float v) {
        clockPanelY = clamp(v, 0f, 1f);
    }

    public static void setClockScale(float v) {
        clockScale = clamp(v, CLOCK_SCALE_MIN, CLOCK_SCALE_MAX);
    }

    public static void setHotbarScale(float v) {
        hotbarScale = clamp(v, HOTBAR_MIN, HOTBAR_MAX);
    }

    public static void setThirdPersonMenu(boolean v) {
        thirdPersonMenu = v;
    }

    public static void setShowBossBanner(boolean v) {
        showBossBanner = v;
    }

    public static void setPlatePanelX(float v) {
        platePanelX = clamp(v, 0f, 1f);
    }

    public static void setPlatePanelY(float v) {
        platePanelY = clamp(v, 0f, 1f);
    }

    /** 时钟是否仅 SAO 菜单打开期间显示(关闭菜单即隐藏)。 */
    public static boolean clockOnlyInMenu() {
        return clockOnlyInMenu;
    }

    public static void setClockOnlyInMenu(boolean v) {
        clockOnlyInMenu = v;
    }

    /** 按住 W 自动疾跑。 */
    public static boolean autoSprint() {
        return autoSprint;
    }

    public static void setAutoSprint(boolean v) {
        autoSprint = v;
    }

    /** 隐藏原版血条(饥饿条居中显示,氧气泡保留)。 */
    public static boolean hideVanillaHealth() {
        return hideVanillaHealth;
    }

    public static void setHideVanillaHealth(boolean v) {
        hideVanillaHealth = v;
    }

    /** 饥饿条锚点(屏幕比例)。 */
    public static float foodPanelX() {
        return foodPanelX;
    }

    public static float foodPanelY() {
        return foodPanelY;
    }

    public static void setFoodPanelX(float v) {
        foodPanelX = clamp(v, 0f, 1f);
    }

    public static void setFoodPanelY(float v) {
        foodPanelY = clamp(v, 0f, 1f);
    }

    /** 置顶物品注册名列表(只读快照)。 */
    public static java.util.List<String> pinnedItems() {
        return java.util.List.copyOf(pinnedItems);
    }

    /** 该物品是否已置顶。 */
    public static boolean isPinned(String id) {
        return pinnedItems.contains(id);
    }

    /**
     * 置顶顺序号;未置顶返回 {@link Integer#MAX_VALUE}(排序时自然沉底)。
     */
    public static int pinOrder(String id) {
        int i = pinnedItems.indexOf(id);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** 手动顺序号;不在自定义顺序里返回 {@link Integer#MAX_VALUE}。 */
    public static int orderIndex(String id) {
        int i = itemOrder.indexOf(id);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** 自定义顺序快照(只读)。 */
    public static java.util.List<String> itemOrder() {
        return java.util.List.copyOf(itemOrder);
    }

    /**
     * 整体覆盖自定义顺序(右键拖动换序时由菜单传入「当前完整显示顺序」)。
     *
     * <p>整表覆盖而不是只记两件:只记被拖的两件会让「已排序」与「未排序」
     * 物品之间无从比较,必须给所有物品一个确定位次。这也是为什么
     * 拖动不再顺带把物品置顶——置顶是独立的标记,不再被排序借用。</p>
     */
    public static void setItemOrder(java.util.List<String> order) {
        itemOrder.clear();
        if (order != null) {
            for (String id : order) {
                if (id != null && !id.isEmpty() && !itemOrder.contains(id)) {
                    itemOrder.add(id);
                }
            }
        }
    }

    /** 切换置顶;返回切换后是否为置顶态。 */
    public static boolean togglePinned(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (pinnedItems.remove(id)) {
            return false;
        }
        pinnedItems.add(id);
        return true;
    }

    public static void setSaoToasts(boolean v) {
        saoToasts = v;
    }

    public static void setShowClock(boolean v) {
        showClock = v;
    }

    public static void setClock24h(boolean v) {
        clock24h = v;
    }

    public static void setClockDate(boolean v) {
        clockDate = v;
    }

    public static void setShowWelcome(boolean v) {
        showWelcome = v;
    }

    public static void setDeathShatter(boolean v) {
        deathShatter = v;
    }

    public static void setDeathShatterDensity(float v) {
        deathShatterDensity = clamp(v, SHATTER_MIN, SHATTER_MAX);
    }

    public static void reset() {
        anchorX = DEF_ANCHOR_X;
        anchorY = DEF_ANCHOR_Y;
        menuScale = DEF_MENU_SCALE;
        bobAmp = DEF_BOB_AMP;
        sounds = true;
        hideHotbar = true;
        showHud = true;
        showAvatar = true;
        anchorFollowMouse = false;
        showTargetBar = true;
        showDamageNumbers = true;
        saoToasts = true;
        showClock = true;
        clock24h = true;
        clockDate = false;
        showWelcome = true;
        deathShatter = true;
        deathShatterDensity = DEF_SHATTER_DENSITY;
        accentHue = DEF_ACCENT_HUE;
        mapPanelX = DEF_MAP_PANEL_X;
        mapPanelY = DEF_MAP_PANEL_Y;
        mapPinned = false;
        clockPanelX = DEF_CLOCK_PANEL_X;
        clockPanelY = DEF_CLOCK_PANEL_Y;
        clockScale = DEF_CLOCK_SCALE;
        hotbarScale = DEF_HOTBAR_SCALE;
        thirdPersonMenu = DEF_THIRD_PERSON;
        showBossBanner = DEF_BOSS_BANNER;
        platePanelX = DEF_PLATE_PANEL_X;
        platePanelY = DEF_PLATE_PANEL_Y;
        clockOnlyInMenu = DEF_CLOCK_MENU_ONLY;
        autoSprint = DEF_AUTO_SPRINT;
        hideVanillaHealth = DEF_HIDE_VANILLA_HEALTH;
        foodPanelX = DEF_FOOD_PANEL_X;
        foodPanelY = DEF_FOOD_PANEL_Y;
        pinnedItems.clear();
        itemOrder.clear();
    }

    // ------------------------------------------------------------ 持久化

    /** 最近一次 load 的文件;从未加载时返回 null。 */
    public static Path path() {
        return loadedFrom;
    }

    public static void load(Path file) {
        loadedFrom = file;
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Data d = GSON.fromJson(json, Data.class);
            if (d == null) {
                return;
            }
            // 旧默认锚点 0.32 迁移到新默认:二级展开时整组左移,0.44 才放得下
            if (Math.abs(d.anchorX - 0.32f) < 0.0001f) {
                setAnchorX(DEF_ANCHOR_X);
            } else {
                setAnchorX(d.anchorX);
            }
            setAnchorY(d.anchorY);
            setMenuScale(d.menuScale);
            setBobAmp(d.bobAmp);
            sounds = d.sounds;
            hideHotbar = d.hideHotbar;
            showHud = d.showHud;
            showAvatar = d.showAvatar;
            anchorFollowMouse = d.anchorFollowMouse;
            showTargetBar = d.showTargetBar;
            showDamageNumbers = d.showDamageNumbers;
            saoToasts = d.saoToasts;
            showClock = d.showClock;
            clock24h = d.clock24h;
            clockDate = d.clockDate;
            showWelcome = d.showWelcome;
            deathShatter = d.deathShatter;
            setDeathShatterDensity(d.deathShatterDensity);
            setAccentHue(d.accentHue);
            setMapPanelX(d.mapPanelX);
            setMapPanelY(d.mapPanelY);
            mapPinned = d.mapPinned;
            setClockPanelX(d.clockPanelX);
            setClockPanelY(d.clockPanelY);
            setClockScale(d.clockScale);
            setHotbarScale(d.hotbarScale);
            thirdPersonMenu = d.thirdPersonMenu;
            showBossBanner = d.showBossBanner;
            setPlatePanelX(d.platePanelX);
            setPlatePanelY(d.platePanelY);
            clockOnlyInMenu = d.clockOnlyInMenu;
            autoSprint = d.autoSprint;
            hideVanillaHealth = d.hideVanillaHealth;
            setFoodPanelX(d.foodPanelX);
            setFoodPanelY(d.foodPanelY);
            pinnedItems.clear();
            if (d.pinnedItems != null) {
                for (String id : d.pinnedItems) {
                    if (id != null && !id.isEmpty() && !pinnedItems.contains(id)) {
                        pinnedItems.add(id);
                    }
                }
            }
            setItemOrder(d.itemOrder);
            hasOpenedSettings = d.hasOpenedSettings; // 加载是否打开过设置
        } catch (IOException | JsonSyntaxException e) {
            SAOMenu.LOGGER.warn("[SAOMenu] config load failed, keeping defaults: {}", e.toString());
        }
    }

    public static void save(Path file) {
        if (file == null) {
            return;
        }
        Data d = new Data(anchorX, anchorY, menuScale, bobAmp, sounds, hideHotbar, showHud, showAvatar, anchorFollowMouse, showTargetBar, showDamageNumbers, saoToasts, showClock, clock24h, clockDate, showWelcome, deathShatter, deathShatterDensity, accentHue, mapPanelX, mapPanelY, mapPinned, clockPanelX, clockPanelY, clockScale, hasOpenedSettings, hotbarScale, thirdPersonMenu, showBossBanner, platePanelX, platePanelY, clockOnlyInMenu, autoSprint, hideVanillaHealth, foodPanelX, foodPanelY, new java.util.ArrayList<>(pinnedItems), new java.util.ArrayList<>(itemOrder));
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SAOMenu.LOGGER.error("[SAOMenu] config save failed: {}", e.toString());
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Gson 序列化载体;无参构造带默认值,旧文件缺字段不会清空设置。 */
    private static class Data {
        float anchorX;
        float anchorY;
        float menuScale;
        float bobAmp;
        boolean sounds;
        boolean hideHotbar;
        boolean showHud;
        boolean showAvatar;
        boolean anchorFollowMouse;
        boolean showTargetBar;
        boolean showDamageNumbers;
        boolean saoToasts;
        boolean showClock;
        boolean clock24h;
        boolean clockDate;
        boolean showWelcome;
        boolean deathShatter;
        float deathShatterDensity;
        float accentHue;
        float mapPanelX;
        float mapPanelY;
        boolean mapPinned;
        float clockPanelX;
        float clockPanelY;
        float clockScale;
        float hotbarScale;
        boolean thirdPersonMenu;
        boolean showBossBanner;
        float platePanelX;
        float platePanelY;
        boolean clockOnlyInMenu;
        boolean autoSprint;
        boolean hideVanillaHealth;
        float foodPanelX;
        float foodPanelY;
        java.util.List<String> pinnedItems;
        java.util.List<String> itemOrder;
        boolean hasOpenedSettings;

        Data() {
            this(DEF_ANCHOR_X, DEF_ANCHOR_Y, DEF_MENU_SCALE, DEF_BOB_AMP, true, true, true, true, true, true, true, true, true, true, false, true, true, DEF_SHATTER_DENSITY, DEF_ACCENT_HUE, DEF_MAP_PANEL_X, DEF_MAP_PANEL_Y, false, DEF_CLOCK_PANEL_X, DEF_CLOCK_PANEL_Y, DEF_CLOCK_SCALE, false, DEF_HOTBAR_SCALE, DEF_THIRD_PERSON, DEF_BOSS_BANNER, DEF_PLATE_PANEL_X, DEF_PLATE_PANEL_Y, DEF_CLOCK_MENU_ONLY, DEF_AUTO_SPRINT, DEF_HIDE_VANILLA_HEALTH, DEF_FOOD_PANEL_X, DEF_FOOD_PANEL_Y, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        }

        Data(float ax, float ay, float ms, float bob, boolean s, boolean hh, boolean sh, boolean av, boolean fm, boolean tb, boolean dn, boolean st, boolean sc, boolean c24, boolean cd, boolean sw, boolean ds, float dsd, float hue, float mx, float my, boolean mp, float cx, float cy, float cs, boolean hos, float hbs, boolean tpm, boolean bb, float ppx, float ppy, boolean cim, boolean asp, boolean hvh, float fpx, float fpy, java.util.List<String> pin, java.util.List<String> ord) {
            anchorX = ax;
            anchorY = ay;
            menuScale = ms;
            bobAmp = bob;
            sounds = s;
            hideHotbar = hh;
            showHud = sh;
            showAvatar = av;
            anchorFollowMouse = fm;
            showTargetBar = tb;
            showDamageNumbers = dn;
            saoToasts = st;
            showClock = sc;
            clock24h = c24;
            clockDate = cd;
            showWelcome = sw;
            deathShatter = ds;
            deathShatterDensity = dsd;
            accentHue = hue;
            mapPanelX = mx;
            mapPanelY = my;
            mapPinned = mp;
            clockPanelX = cx;
            clockPanelY = cy;
            clockScale = cs;
            hasOpenedSettings = hos;
            hotbarScale = hbs;
            thirdPersonMenu = tpm;
            showBossBanner = bb;
            platePanelX = ppx;
            platePanelY = ppy;
            clockOnlyInMenu = cim;
            autoSprint = asp;
            hideVanillaHealth = hvh;
            foodPanelX = fpx;
            foodPanelY = fpy;
            pinnedItems = pin;
            itemOrder = ord;
        }
    }
}
