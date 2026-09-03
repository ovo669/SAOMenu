package com.sao.saomenu.client;

import com.mojang.math.Axis;
import com.sao.saomenu.SAOMenuPlatform;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 女神异闻录(P5)风格模组设置界面,以「设置背景.mp4」帧动画为背景:
 *
 * <ul>
 *   <li>背景:视频帧贴图集({@code settings_bg.png},480x270@15fps,16 列网格)按
 *       播放状态机取帧——打开界面 4 倍速扫入桐人眼睛特写(帧 {@link #BASE_FRAME},
 *       视频 t≈5s)后定格,点击分类从定格处继续正播到亚斯娜脸特写
 *       (帧 {@link #END_FRAME},视频 t≈10s)停住,点击返回从当前帧
 *       倒放回定格点;全屏 cover 拉伸 + 轻微渐变叠加,尽量露出背景</li>
 *   <li>双主题:根页面为桐谷和人蓝白系,点击分类进入设置页后整体切换为
 *       亚斯娜粉白系(与背景从蓝色章节进入紫色章节同步),返回时切回蓝白</li>
 *   <li>根页面:4 个阶梯排布的分类条目(布局/战斗/界面/主题)从左侧级联滑入,
 *       平时只有色条+大字,不遮挡背景;悬停时半透明斜板展开、文字变亮</li>
 *   <li>点击分类:主题色斜切色带扫过全屏(转场),色带移开时设置行带过冲
 *       逐条弹出;返回:反向转场,分类条目从左侧滑回</li>
 * </ul>
 *
 * <p>配置项与旧 {@link SAOConfigScreen} 完全等价(并补上时钟大小、跟随鼠标),
 * 修改即时生效并持久化到 {@code config/saomenu.json}。</p>
 */
public class SAOSettingsScreen extends Screen {

    // ------------------------------------------------------------ 视频帧贴图集(由 tools/gen_settings_bg.py 生成)
    private static final ResourceLocation TEX_BG =
            new ResourceLocation("saomenu", "textures/gui/settings_bg.png");
    private static final int FRAME_W = 480;
    private static final int FRAME_H = 270;
    private static final int FRAME_COLS = 16;
    private static final int FRAME_COUNT = 156;
    private static final int FRAME_FPS = 15;
    private static final int ATLAS_W = FRAME_COLS * FRAME_W;
    private static final int ATLAS_H = ((FRAME_COUNT + FRAME_COLS - 1) / FRAME_COLS) * FRAME_H;
    /** 开场定格点:桐人眼睛特写(视频 t≈5s → 15fps 帧 75)。 */
    private static final int BASE_FRAME = 75;
    /** 分类页正播终点:亚斯娜脸特写(视频 t≈10s → 15fps 帧 150)。 */
    private static final int END_FRAME = 150;
    /** 开场扫入倍速(0 → BASE_FRAME)。 */
    private static final float SCAN_SPEED = 4f;

    // ------------------------------------------------------------ 转场时间轴(ms)
    private static final long TR_SWAP_MS = 290;
    private static final long TR_TOTAL_MS = 480;
    private static final long ENTER_STAGGER_MS = 55;

    // ------------------------------------------------------------ 双主题:桐人蓝白 / 亚斯娜粉白(RGB,透明度运行时合成)
    private static final int KIRITO_BLUE = 0x4FA8E8;
    private static final int KIRITO_BRIGHT = 0xA8DFFF;
    private static final int KIRITO_DEEP = 0x101C30;
    private static final int ASUNA_PINK = 0xFF8AB0;
    private static final int ASUNA_BRIGHT = 0xFFC9DA;
    private static final int ASUNA_DEEP = 0x2C1420;
    private static final int RGB_WHITE = 0xF8FAFC;
    private static final int RGB_GRAY = 0xB9BEC6;
    private static final int RGB_DARK_TEXT = 0x16171A;

    private final Screen lastScreen;

    /** 当前页;转场换页瞬间更新。 */
    private Page page = Page.ROOT;
    private Page transitionFrom = Page.ROOT;
    private long transitionStart = -1;
    private boolean transitionForward;
    private int clickedCat = -1;
    private long pageStartMs;
    private long lastFrameMs;
    private boolean initialized;

    /** 背景播放状态机:FORWARD 从 f0 正播到 f1;REVERSE 从 f0 倒放回 f1。 */
    private BgMode bgMode = BgMode.FORWARD;
    private int bgFrame0;
    private int bgFrame1 = BASE_FRAME;
    private float bgSpeed = SCAN_SPEED;
    private long bgModeAt;

    private enum BgMode {FORWARD, REVERSE}

    private final float[] catHover = new float[CATS.length];
    private float backHover;
    private int hoverRow = -1;
    private int dragRow = -1;
    private boolean btnResetHover;
    private boolean btnDoneHover;

    private enum Page {ROOT, LAYOUT, COMBAT, HUD, THEME}

    private static final Page[] CATS = {Page.LAYOUT, Page.COMBAT, Page.HUD, Page.THEME};

    public SAOSettingsScreen(Screen lastScreen) {
        super(Component.translatable("saomenu.settings.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        long now = Util.getMillis();
        if (!initialized) {
            initialized = true;
            // 开场:4 倍速从首帧扫入眼睛特写后定格
            bgMode = BgMode.FORWARD;
            bgFrame0 = 0;
            bgFrame1 = BASE_FRAME;
            bgSpeed = SCAN_SPEED;
            bgModeAt = now;
            pageStartMs = now;
            lastFrameMs = now;
        }
    }

    // ------------------------------------------------------------ 主题取色

    /** 当前页主题主色:根页面=桐人蓝,分类页=亚斯娜粉。 */
    private int uiAccent(Page p) {
        return p == Page.ROOT ? KIRITO_BLUE : ASUNA_PINK;
    }

    private int uiBright(Page p) {
        return p == Page.ROOT ? KIRITO_BRIGHT : ASUNA_BRIGHT;
    }

    /** 当前页深底色(半透明板用)。 */
    private int uiDeep(Page p) {
        return p == Page.ROOT ? KIRITO_DEEP : ASUNA_DEEP;
    }

    // ------------------------------------------------------------ 页面几何

    private int sideX() {
        return Math.max(14, (int) (this.width * 0.07f));
    }

    private int catW() {
        return Mth.clamp((int) (this.width * 0.36f), 170, 330);
    }

    private int catH() {
        return Mth.clamp((this.height - 120) / 4 - 8, 24, 44);
    }

    private int catGap() {
        return Math.max(8, catH() / 4);
    }

    private int catY0() {
        return Math.max(56, (this.height - (catH() * 4 + catGap() * 3)) / 2 + 4);
    }

    /** 阶梯排布:每项相对上一项右移一档。 */
    private int catX(int i) {
        return sideX() + i * 18 + Math.round(catHover[i] * 8f);
    }

    private int catY(int i) {
        return catY0() + i * (catH() + catGap());
    }

    private boolean catHovered(int i, int mx, int my) {
        int x = catX(i);
        return mx >= x - 16 && mx <= x + catW() - 10
                && my >= catY(i) - 2 && my < catY(i) + catH() + 2;
    }

    private int rowsTop() {
        return Math.max(58, (int) (this.height * 0.20f));
    }

    private int rowH() {
        int avail = this.height - rowsTop() - 46;
        return Mth.clamp(avail / Math.max(1, rowCount(this.page)), 16, 34);
    }

    private int rowY(int i) {
        return rowsTop() + i * rowH();
    }

    private int rowX0() {
        return sideX();
    }

    private int rowX1() {
        return this.width - sideX();
    }

    private boolean rowHovered(int i, int mx, int my) {
        int y = rowY(i);
        return mx >= rowX0() && mx <= rowX1() && my >= y && my < y + rowH();
    }

    private int smallBtnW() {
        return Math.min(96, (this.width - sideX() * 2 - 10) / 2);
    }

    private int smallBtnY() {
        return this.height - Math.max(24, catH()) - 8;
    }

    private int smallBtnH() {
        return Math.min(20, catH() - 2);
    }

    private boolean btnHovered(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx <= x + w + 8 && my >= y - 1 && my < y + h + 1;
    }

    // ------------------------------------------------------------ 行模型(滑块/开关/预设)

    private int rowCount(Page p) {
        return switch (p) {
            case LAYOUT -> 6;
            case COMBAT -> 7;
            case HUD -> 9;
            case THEME -> 2;
            default -> 0;
        };
    }

    private boolean rowIsSlider(Page p, int i) {
        return switch (p) {
            case LAYOUT -> i < 4;
            case COMBAT -> i == 5;
            case HUD -> i == 1 || i == 7;
            case THEME -> i == 0;
            default -> false;
        };
    }

    private String rowLabel(Page p, int i) {
        String key = switch (p) {
            case LAYOUT -> switch (i) {
                case 0 -> "saomenu.config.anchor_x";
                case 1 -> "saomenu.config.anchor_y";
                case 2 -> "saomenu.config.scale";
                case 3 -> "saomenu.config.bob";
                case 4 -> "saomenu.config.follow_mouse";
                default -> "saomenu.config.hide_hotbar";
            };
            case COMBAT -> switch (i) {
                case 0 -> "saomenu.config.show_hud";
                case 1 -> "saomenu.config.show_avatar";
                case 2 -> "saomenu.config.target_bar";
                case 3 -> "saomenu.config.damage_numbers";
                case 4 -> "saomenu.config.death_shatter";
                case 5 -> "saomenu.config.shatter_density";
                default -> "saomenu.config.boss_banner";
            };
            case HUD -> switch (i) {
                case 0 -> "saomenu.config.show_clock";
                case 1 -> "saomenu.config.clock_scale";
                case 2 -> "saomenu.config.clock_24h";
                case 3 -> "saomenu.config.clock_date";
                case 4 -> "saomenu.config.show_welcome";
                case 5 -> "saomenu.config.sao_toasts";
                case 6 -> "saomenu.config.sounds";
                case 7 -> "saomenu.config.hotbar_scale";
                default -> "saomenu.config.third_person";
            };
            default -> "saomenu.config.theme";
        };
        return tr(key);
    }

    private float sliderGet(Page p, int i) {
        return switch (p) {
            case LAYOUT -> switch (i) {
                case 0 -> SAOConfig.anchorX();
                case 1 -> SAOConfig.anchorY();
                case 2 -> SAOConfig.menuScale();
                default -> SAOConfig.bobAmp();
            };
            case COMBAT -> SAOConfig.deathShatterDensity();
            case HUD -> i == 1 ? SAOConfig.clockScale() : SAOConfig.hotbarScale();
            default -> SAOConfig.accentHue();
        };
    }

    private void sliderSet(Page p, int i, float v) {
        switch (p) {
            case LAYOUT -> {
                switch (i) {
                    case 0 -> SAOConfig.setAnchorX(v);
                    case 1 -> SAOConfig.setAnchorY(v);
                    case 2 -> SAOConfig.setMenuScale(v);
                    default -> SAOConfig.setBobAmp(v);
                }
            }
            case COMBAT -> SAOConfig.setDeathShatterDensity(v);
            case HUD -> {
                if (i == 1) {
                    SAOConfig.setClockScale(v);
                } else {
                    SAOConfig.setHotbarScale(v);
                }
            }
            default -> SAOConfig.setAccentHue(v);
        }
    }

    private float sliderMin(Page p, int i) {
        if (p == Page.THEME) {
            return 0f;
        }
        if (p == Page.LAYOUT) {
            return switch (i) {
                case 0, 1 -> SAOConfig.ANCHOR_MIN;
                case 2 -> SAOConfig.SCALE_MIN;
                default -> SAOConfig.BOB_MIN;
            };
        }
        if (p == Page.HUD) {
            return i == 1 ? SAOConfig.CLOCK_SCALE_MIN : SAOConfig.HOTBAR_MIN;
        }
        return p == Page.COMBAT ? SAOConfig.SHATTER_MIN : SAOConfig.CLOCK_SCALE_MIN;
    }

    private float sliderMax(Page p, int i) {
        if (p == Page.THEME) {
            return 360f;
        }
        if (p == Page.LAYOUT) {
            return switch (i) {
                case 0, 1 -> SAOConfig.ANCHOR_MAX;
                case 2 -> SAOConfig.SCALE_MAX;
                default -> SAOConfig.BOB_MAX;
            };
        }
        if (p == Page.HUD) {
            return i == 1 ? SAOConfig.CLOCK_SCALE_MAX : SAOConfig.HOTBAR_MAX;
        }
        return p == Page.COMBAT ? SAOConfig.SHATTER_MAX : SAOConfig.CLOCK_SCALE_MAX;
    }

    private String sliderText(Page p, int i, float v) {
        if (p == Page.THEME) {
            return Math.round(v) + "°";
        }
        if (p == Page.LAYOUT && i < 2) {
            return String.format(Locale.ROOT, "%.0f%%", v * 100f);
        }
        if ((p == Page.LAYOUT && i == 2) || p == Page.HUD) {
            return String.format(Locale.ROOT, "%.2fx", v);
        }
        return String.format(Locale.ROOT, "%.1fx", v);
    }

    private boolean toggleGet(Page p, int i) {
        return switch (p) {
            case LAYOUT -> i == 4 ? SAOConfig.anchorFollowMouse() : SAOConfig.hideHotbar();
            case COMBAT -> switch (i) {
                case 0 -> SAOConfig.showHud();
                case 1 -> SAOConfig.showAvatar();
                case 2 -> SAOConfig.showTargetBar();
                case 3 -> SAOConfig.showDamageNumbers();
                case 4 -> SAOConfig.deathShatter();
                default -> SAOConfig.showBossBanner();
            };
            case HUD -> switch (i) {
                case 0 -> SAOConfig.showClock();
                case 2 -> SAOConfig.clock24h();
                case 3 -> SAOConfig.clockDate();
                case 4 -> SAOConfig.showWelcome();
                case 5 -> SAOConfig.saoToasts();
                case 6 -> SAOConfig.sounds();
                default -> SAOConfig.thirdPersonMenu();
            };
            default -> false;
        };
    }

    private void toggleFlip(Page p, int i) {
        switch (p) {
            case LAYOUT -> {
                if (i == 4) {
                    SAOConfig.setAnchorFollowMouse(!SAOConfig.anchorFollowMouse());
                } else {
                    SAOConfig.setHideHotbar(!SAOConfig.hideHotbar());
                }
            }
            case COMBAT -> {
                switch (i) {
                    case 0 -> SAOConfig.setShowHud(!SAOConfig.showHud());
                    case 1 -> SAOConfig.setShowAvatar(!SAOConfig.showAvatar());
                    case 2 -> SAOConfig.setShowTargetBar(!SAOConfig.showTargetBar());
                    case 3 -> SAOConfig.setShowDamageNumbers(!SAOConfig.showDamageNumbers());
                    case 4 -> SAOConfig.setDeathShatter(!SAOConfig.deathShatter());
                    default -> SAOConfig.setShowBossBanner(!SAOConfig.showBossBanner());
                }
            }
            case HUD -> {
                switch (i) {
                    case 0 -> SAOConfig.setShowClock(!SAOConfig.showClock());
                    case 2 -> SAOConfig.setClock24h(!SAOConfig.clock24h());
                    case 3 -> SAOConfig.setClockDate(!SAOConfig.clockDate());
                    case 4 -> SAOConfig.setShowWelcome(!SAOConfig.showWelcome());
                    case 5 -> SAOConfig.setSaoToasts(!SAOConfig.saoToasts());
                    case 6 -> SAOConfig.setSounds(!SAOConfig.sounds());
                    default -> SAOConfig.setThirdPersonMenu(!SAOConfig.thirdPersonMenu());
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ 渲染主流程

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = Util.getMillis();
        float dt = Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        renderVideoBackground(g);
        // 轻微渐变叠加:顶部几乎全透,仅底部略暗保文字可读,尽量露出背景
        g.fillGradient(0, 0, this.width, this.height, 0x1E000000, 0x61000000);
        renderDecorStripes(g, now);

        updateHovers(dt, mouseX, mouseY);

        boolean inTransition = this.transitionStart >= 0;
        long trT = inTransition ? now - this.transitionStart : 0;
        Page toRender = inTransition && trT < TR_SWAP_MS ? this.transitionFrom : this.page;
        renderPage(g, toRender, mouseX, mouseY, now, inTransition, trT);

        if (inTransition) {
            renderWipe(g, trT);
            if (trT >= TR_SWAP_MS && this.page != transitionTarget()) {
                this.page = transitionTarget();
                this.pageStartMs = now;
                playPanel();
            }
            if (trT >= TR_TOTAL_MS) {
                this.transitionStart = -1;
                this.clickedCat = -1;
            }
        }
    }

    private Page transitionTarget() {
        return this.transitionForward ? CATS[this.clickedCat] : Page.ROOT;
    }

    /** 按播放状态机取当前帧(FORWARD 正播到 f1;REVERSE 倒放回 f1;到端点钳制停住)。 */
    private int currentBgFrame(long now) {
        long step = Math.round((now - bgModeAt) * FRAME_FPS * bgSpeed / 1000.0);
        if (bgMode == BgMode.FORWARD) {
            return (int) Mth.clamp(bgFrame0 + step, Math.min(bgFrame0, bgFrame1),
                    Math.max(bgFrame0, bgFrame1));
        }
        return (int) Mth.clamp(bgFrame0 - step, Math.min(bgFrame0, bgFrame1),
                Math.max(bgFrame0, bgFrame1));
    }

    /** 视频帧背景:按当前帧取贴图集,cover 方式铺满屏幕。 */
    private void renderVideoBackground(GuiGraphics g) {
        int idx = Mth.clamp(currentBgFrame(Util.getMillis()), 0, FRAME_COUNT - 1);
        int col = idx % FRAME_COLS;
        int row = idx / FRAME_COLS;
        float scale = Math.max(this.width / (float) FRAME_W, this.height / (float) FRAME_H);
        int dw = Math.round(FRAME_W * scale);
        int dh = Math.round(FRAME_H * scale);
        g.blit(TEX_BG, (this.width - dw) / 2, (this.height - dh) / 2, dw, dh,
                (float) (col * FRAME_W), (float) (row * FRAME_H),
                FRAME_W, FRAME_H, ATLAS_W, ATLAS_H);
    }

    /** 缓慢横移的斜切装饰线(颜色随页主题)。 */
    private void renderDecorStripes(GuiGraphics g, long now) {
        float drift = (now % 6000L) / 6000f;
        float a1 = (drift * 1.6f - 0.3f) * this.width;
        float a2 = (drift * 1.6f - 1.1f) * this.width;
        fillSlab(g, a1, this.height * 0.16f, this.width * 0.5f, 2f, -18f, 0x30FFFFFF);
        fillSlab(g, a2, this.height * 0.88f, this.width * 0.5f, 2f, -18f, 0x2699CCDD);
        fillSlab(g, this.width * 0.86f, this.height * 0.5f, 3f, this.height * 1.6f, -18f,
                withAlpha(uiAccent(this.page), 46));
    }

    private void updateHovers(float dt, int mouseX, int mouseY) {
        float k = Math.min(1f, dt * 12f);
        for (int i = 0; i < CATS.length; i++) {
            boolean target = this.page == Page.ROOT && this.transitionStart < 0
                    && catHovered(i, mouseX, mouseY);
            catHover[i] += ((target ? 1f : 0f) - catHover[i]) * k;
        }
        boolean backTarget = this.page != Page.ROOT && this.transitionStart < 0
                && backHovered(mouseX, mouseY);
        backHover += ((backTarget ? 1f : 0f) - backHover) * k;

        int newHover = -1;
        if (this.page != Page.ROOT && this.transitionStart < 0) {
            for (int i = 0; i < rowCount(this.page); i++) {
                if (rowHovered(i, mouseX, mouseY)) {
                    newHover = i;
                    break;
                }
            }
        }
        hoverRow = newHover;

        int bw = smallBtnW();
        int by = smallBtnY();
        int bh = smallBtnH();
        btnResetHover = btnHovered(sideX(), by, bw, bh, mouseX, mouseY);
        btnDoneHover = btnHovered(this.width - sideX() - bw, by, bw, bh, mouseX, mouseY);
    }

    // ------------------------------------------------------------ 页面渲染

    private void renderPage(GuiGraphics g, Page p, int mx, int my,
                            long now, boolean inTransition, long trT) {
        float exitP = inTransition && trT < TR_SWAP_MS
                ? Mth.clamp(trT / (float) TR_SWAP_MS, 0f, 1f) : 0f;
        boolean exiting = exitP > 0f;
        if (p == Page.ROOT) {
            renderRootPage(g, mx, my, now, exiting, exitP);
        } else {
            renderRowsPage(g, p, mx, my, now, exiting, exitP);
        }
        renderBottomBar(g, p, now, exiting, exitP);
    }

    private void renderRootPage(GuiGraphics g, int mx, int my,
                                long now, boolean exiting, float exitP) {
        long enter = now - this.pageStartMs;
        float titleIn = easeOutCubic(clamp01((enter - 60f) / 340f));

        float titleOff = exiting ? -easeInCubic(exitP) * this.width * 0.6f : (1f - titleIn) * -260f;
        float titleY = Math.max(12, catY0() - catH() - 26);
        fillSlab(g, sideX() + 10 + titleOff * 0.4f, titleY + 11, 24, 24, -8f,
                withAlpha(KIRITO_BLUE, alpha(titleIn, 1f)));
        drawScaled(g, tr("saomenu.settings.title"), sideX() + 30 + titleOff,
                titleY, 1.7f, withAlpha(RGB_WHITE, alpha(titleIn, 1f)), false);
        drawScaled(g, tr("saomenu.settings.subtitle"), sideX() + 32 + titleOff,
                titleY + 20, 0.8f, withAlpha(RGB_GRAY, alpha(titleIn, 0.88f)), false);

        for (int i = 0; i < CATS.length; i++) {
            float off;
            float alphaF;
            if (exiting) {
                boolean lead = this.transitionForward && i == this.clickedCat;
                float local = clamp01((exitP * TR_SWAP_MS - (lead ? 0f : 40f + i * 30f)) / 230f);
                off = (lead ? -1f : 1f) * easeInCubic(local) * this.width * 0.75f;
                alphaF = 1f - easeInCubic(local);
            } else {
                float enterT = clamp01((enter - 140f - i * ENTER_STAGGER_MS) / 380f);
                off = (1f - easeOutBack(enterT)) * -220f;
                alphaF = clamp01(enterT * 1.8f);
            }
            renderCategory(g, i, off, alphaF, mx, my);
        }

        // 悬停分类的巨型编号水印
        int hi = -1;
        for (int i = 0; i < CATS.length; i++) {
            if (catHover[i] > 0.35f) {
                hi = i;
            }
        }
        if (hi >= 0 && !exiting) {
            String num = String.format(Locale.ROOT, "0%d", hi + 1);
            float gs = this.height / 26f;
            drawScaledRot(g, num, this.width * 0.70f, this.height * 0.26f, gs, -10f,
                    withAlpha(KIRITO_BLUE, Math.round(catHover[hi] * 52)), false);
        }
    }

    /** 根页面分类条目:阶梯排布,平时只有色条+大字(不挡背景),悬停半透明斜板展开。 */
    private void renderCategory(GuiGraphics g, int i, float xOff, float alphaF, int mx, int my) {
        if (alphaF <= 0.02f) {
            return;
        }
        int a = alpha(alphaF, 1f);
        int w = catW();
        int h = catH();
        int x = catX(i) + Math.round(xOff);
        int y = catY(i);
        float hv = catHover[i];
        boolean hovered = hv > 0.5f && this.transitionStart < 0 && alphaF > 0.9f;

        // 悬停斜板:随 hover 进度从色条处向右展开,半透明露出背景
        if (hv > 0.02f) {
            float spread = 0.55f + 0.45f * hv; // 伸展宽度比例
            fillSlab(g, x + 3 + w * spread * hv * 0.5f, y + h * 0.5f,
                    w * spread * hv, h, -3f,
                    withAlpha(hovered ? KIRITO_BLUE : KIRITO_DEEP, Math.round(a * hv * 0.68f)));
        }

        // 左端竖色条(悬停变亮加宽)
        fillSlab(g, x + 3f, y + h * 0.5f, hovered ? 7f : 5f, h - 6f, -3f,
                withAlpha(hovered ? KIRITO_BRIGHT : KIRITO_BLUE, a));
        // 悬停前缘箭头
        if (hv > 0.35f) {
            drawScaled(g, "▶", x - 15, y + h * 0.5f - 5,
                    0.9f, withAlpha(KIRITO_BRIGHT, Math.round(a * (hv - 0.35f) / 0.65f)), false);
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 14, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-2f));
        drawScaled(g, String.format(Locale.ROOT, "0%d", i + 1), 0, 2, 0.7f,
                withAlpha(hovered ? KIRITO_BRIGHT : RGB_GRAY, Math.round(a * 0.85f)), false);
        drawScaled(g, tr(catTitleKey(i)), 0, h * 0.5f - 6, Math.min(1.6f, h / 20f),
                withAlpha(hovered ? KIRITO_BRIGHT : RGB_WHITE, a), false);
        drawScaled(g, tr(catSubKey(i)), 1, h - 10, 0.62f,
                withAlpha(RGB_GRAY, Math.round(a * 0.8f)), false);
        pose.popPose();
    }

    private void renderRowsPage(GuiGraphics g, Page p, int mx, int my,
                                long now, boolean exiting, float exitP) {
        long enter = now - this.pageStartMs;
        int accent = ASUNA_PINK;

        float headIn = exiting ? 1f : easeOutCubic(clamp01(enter / 300f));
        float headOff = exiting ? easeInCubic(exitP) * this.width * 0.55f : (1f - headIn) * 200f;
        renderBackButton(g, headOff, alpha(headIn, 1f), mx, my);
        int titleX = sideX() + 76;
        drawScaled(g, tr(pageTitleKey(p)), titleX + headOff, rowsTop() - 32, 1.45f,
                withAlpha(RGB_WHITE, alpha(headIn, 1f)), false);
        fillSlab(g, titleX - 8 + headOff, rowsTop() - 22, 3.4f, 21f, -6f,
                withAlpha(accent, alpha(headIn, 1f)));
        drawScaled(g, tr(pageSubKey(p)), titleX + 2 + headOff, rowsTop() - 14, 0.62f,
                withAlpha(RGB_GRAY, Math.round(headIn * 220)), false);

        int n = rowCount(p);
        for (int i = 0; i < n; i++) {
            float local;
            if (exiting) {
                local = clamp01((exitP * TR_SWAP_MS - i * 22f) / 200f);
            } else {
                local = clamp01((enter - 60f - i * ENTER_STAGGER_MS) / 360f);
            }
            float off = exiting
                    ? easeInCubic(local) * this.width * 0.7f
                    : (1f - easeOutBack(local)) * 190f;
            float alphaF = exiting ? 1f - easeInCubic(local) : clamp01(local * 2.2f);
            renderRow(g, p, i, off, alphaF, mx, my);
        }
    }

    private void renderRow(GuiGraphics g, Page p, int i, float off, float alphaF, int mx, int my) {
        if (alphaF <= 0.02f) {
            return;
        }
        int a = alpha(alphaF, 1f);
        int accent = ASUNA_PINK;
        int x0 = rowX0() + Math.round(off);
        int x1 = rowX1() + Math.round(off);
        int y = rowY(i);
        int rh = rowH();
        boolean hovered = this.hoverRow == i && this.transitionStart < 0 && alphaF > 0.95f;

        // 行底板:低透明度,尽量露出背景
        g.fill(x0 + 10, y + 1, x1, y + rh - 1,
                withAlpha(ASUNA_DEEP, Math.round(a * (hovered ? 0.72f : 0.34f))));
        fillSlab(g, x0 + 7f, y + rh / 2f, 4, rh - 6f, -8f,
                withAlpha(hovered ? ASUNA_BRIGHT : accent, Math.round(a * (hovered ? 1f : 0.72f))));
        g.drawString(this.font, rowLabel(p, i), x0 + 18, y + (rh - 8) / 2,
                withAlpha(RGB_WHITE, Math.round(a * (hovered ? 1f : 0.92f))), false);

        if (rowIsSlider(p, i)) {
            renderSliderControl(g, p, i, x0, x1, y, rh, a);
        } else if (p == Page.THEME && i == 1) {
            renderPresets(g, x0, x1, y, rh, a);
        } else {
            renderToggleControl(g, p, i, x1, y, rh, a);
        }
    }

    private void renderSliderControl(GuiGraphics g, Page p, int i, int x0, int x1,
                                     int y, int rh, int a) {
        int accent = ASUNA_PINK;
        int tx0 = x0 + (x1 - x0) * 55 / 100;
        int tx1 = x1 - 58;
        float v = sliderGet(p, i);
        float lo = sliderMin(p, i);
        float hi = sliderMax(p, i);
        float frac = Mth.clamp((v - lo) / (hi - lo), 0f, 1f);

        int trackH = Math.max(4, rh / 5);
        int trackY = y + (rh - trackH) / 2 + 1;
        g.fill(tx0, trackY, tx1, trackY + trackH, withAlpha(0xFFFFFF, Math.round(a * 0.20f)));
        int fillW = Math.round((tx1 - tx0) * frac);
        if (fillW > 1) {
            g.fill(tx0, trackY, tx0 + fillW, trackY + trackH, withAlpha(accent, a));
        }
        int kx = tx0 + fillW;
        int ky = y + rh / 2;
        int d = Math.max(9, Math.min(rh - 6, 12));
        boolean dragging = this.dragRow == i;
        fillSlab(g, kx, ky, d, d, 45f, withAlpha(dragging ? ASUNA_BRIGHT : accent, a));
        fillSlab(g, kx, ky, d * 0.45f, d * 0.45f, 45f, withAlpha(dragging ? accent : RGB_DARK_TEXT, a));

        g.drawString(this.font, sliderText(p, i, v), tx1 + 6, y + (rh - 8) / 2,
                withAlpha(RGB_GRAY, a), false);
    }

    private void renderToggleControl(GuiGraphics g, Page p, int i, int x1, int y, int rh, int a) {
        boolean on = toggleGet(p, i);
        int accent = ASUNA_PINK;
        String s = tr(on ? "saomenu.config.on" : "saomenu.config.off");
        g.drawString(this.font, s, x1 - 10 - this.font.width(s), y + (rh - 8) / 2,
                on ? withAlpha(accent, a) : withAlpha(RGB_GRAY, a), false);
        fillSlab(g, x1 - 20 - this.font.width(s), y + rh / 2f - 1f, 7, 7, 45f,
                withAlpha(on ? accent : 0x55565A, a));
    }

    private void renderPresets(GuiGraphics g, int x0, int x1, int y, int rh, int a) {
        String[] keys = {"saomenu.theme.sao", "saomenu.theme.alo", "saomenu.theme.ggo"};
        float[] hues = {41.44f, 202f, 355f};
        int bw = Math.min(78, (x1 - x0 - 150 - 12) / 3);
        int bh = Math.max(12, rh - 10);
        for (int t = 0; t < 3; t++) {
            int bx = x1 - 10 - (3 - t) * (bw + 6) + 6;
            boolean sel = Math.round(SAOConfig.accentHue()) == Math.round(hues[t]);
            fillSlab(g, bx + bw / 2f, y + rh / 2f, bw, bh, -4f,
                    withAlpha(sel ? RGB_WHITE : hsvToArgb(hues[t]),
                            Math.round(a * (sel ? 1f : 0.85f))));
            var pose = g.pose();
            pose.pushPose();
            pose.translate(bx, y, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-4f));
            drawScaled(g, tr(keys[t]), 3, (rh - 8) / 2 + 1, 0.85f,
                    sel ? RGB_DARK_TEXT : RGB_WHITE, false);
            pose.popPose();
        }
    }

    private void renderBackButton(GuiGraphics g, float off, int a, int mx, int my) {
        int x = sideX() + Math.round(off);
        int y = rowsTop() - 36;
        int w = 64;
        int h = 20;
        boolean hovered = backHover > 0.5f && this.transitionStart < 0;
        fillSlab(g, x + w / 2f, y + h / 2f, w, h, -4f,
                withAlpha(hovered ? ASUNA_PINK : ASUNA_DEEP, Math.round(a * (hovered ? 1f : 0.72f))));
        fillSlab(g, x - 4f, y + h / 2f, 4, h, -4f, withAlpha(ASUNA_PINK, a));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-4f));
        drawScaled(g, "◀ " + tr("saomenu.settings.back"), 6, (h - 8) / 2 + 1, 0.95f,
                withAlpha(hovered ? RGB_DARK_TEXT : RGB_WHITE, a), false);
        pose.popPose();
    }

    private boolean backHovered(int mx, int my) {
        int x = sideX();
        int y = rowsTop() - 36;
        return mx >= x - 6 && mx <= x + 72 && my >= y - 2 && my < y + 22;
    }

    /** 底部:恢复默认 / 完成(两页通用)。 */
    private void renderBottomBar(GuiGraphics g, Page p, long now, boolean exiting, float exitP) {
        long enter = now - this.pageStartMs;
        float in = exiting
                ? 1f - easeInCubic(clamp01(exitP * 1.4f))
                : easeOutCubic(clamp01((enter - 200f) / 320f));
        if (in <= 0.02f) {
            return;
        }
        int a = alpha(in, 1f);
        int accent = uiAccent(p);
        int deep = uiDeep(p);
        int bw = smallBtnW();
        int by = smallBtnY();
        int bh = smallBtnH();
        renderSmallBtn(g, p, sideX(), by, bw, bh, tr("saomenu.config.reset"), btnResetHover, a, accent, deep, 0f);
        renderSmallBtn(g, p, this.width - sideX() - bw, by, bw, bh, tr("saomenu.config.done"),
                btnDoneHover, a, accent, deep, -4f);
    }

    private void renderSmallBtn(GuiGraphics g, Page p, int x, int y, int w, int h,
                                String label, boolean hovered, int a, int accent, int deep, float rot) {
        fillSlab(g, x + w / 2f, y + h / 2f, w, h, rot,
                withAlpha(hovered ? accent : deep, Math.round(a * (hovered ? 1f : 0.62f))));
        fillSlab(g, x - 3f, y + h / 2f, 3, h, rot, withAlpha(accent, a));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(rot));
        drawScaled(g, label, (w - this.font.width(label)) / 2f, (h - 8) / 2f + 1, 0.95f,
                withAlpha(hovered ? RGB_DARK_TEXT : RGB_WHITE, a), false);
        pose.popPose();
    }

    /** 转场斜切色带:尾部黑带 + 目标页主题色主带 + 前缘白条,从左扫到右。 */
    private void renderWipe(GuiGraphics g, long trT) {
        if (trT < 110 || trT > TR_TOTAL_MS - 30) {
            return;
        }
        float p = easeInOutCubic(clamp01((trT - 110f) / (float) (TR_TOTAL_MS - 140f)));
        float w = this.width;
        float h = this.height;
        float cx = Mth.lerp(p, -0.5f, 1.5f) * w;
        int band = this.transitionForward ? ASUNA_PINK : KIRITO_BLUE;
        fillSlab(g, cx - w * 0.42f, h * 0.5f, w * 0.30f, h * 2.6f, -12f, 0xF4000000);
        fillSlab(g, cx, h * 0.5f, w * 0.52f, h * 2.6f, -12f, 0xFF000000 | band);
        fillSlab(g, cx + w * 0.30f, h * 0.5f, w * 0.035f, h * 2.6f, -12f, 0xF2F9F9F9);
    }

    // ------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.transitionStart >= 0) {
            return true;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        long now = Util.getMillis();

        int bw = smallBtnW();
        int by = smallBtnY();
        int bh = smallBtnH();
        if (btnHovered(sideX(), by, bw, bh, mx, my)) {
            SAOConfig.reset();
            saveNow();
            playClick();
            return true;
        }
        if (btnHovered(this.width - sideX() - bw, by, bw, bh, mx, my)) {
            playClick();
            onClose();
            return true;
        }

        if (this.page == Page.ROOT) {
            for (int i = 0; i < CATS.length; i++) {
                if (catHovered(i, mx, my)) {
                    this.clickedCat = i;
                    this.transitionFrom = Page.ROOT;
                    this.transitionForward = true;
                    this.transitionStart = now;
                    // 背景从当前帧(通常为定格点)继续正播到亚斯娜脸特写,与 UI 转粉同步
                    bgMode = BgMode.FORWARD;
                    bgFrame0 = currentBgFrame(now);
                    bgFrame1 = END_FRAME;
                    bgSpeed = 1f;
                    bgModeAt = now;
                    playClick();
                    return true;
                }
            }
        } else {
            if (backHovered(mx, my)) {
                this.transitionFrom = this.page;
                this.transitionForward = false;
                this.transitionStart = now;
                // 背景从当前帧倒放回眼睛特写定格点,与 UI 转回蓝白同步
                bgMode = BgMode.REVERSE;
                bgFrame0 = currentBgFrame(now);
                bgFrame1 = BASE_FRAME;
                bgSpeed = 1f;
                bgModeAt = now;
                playClick();
                return true;
            }
            for (int i = 0; i < rowCount(this.page); i++) {
                if (!rowHovered(i, mx, my)) {
                    continue;
                }
                if (rowIsSlider(this.page, i)) {
                    this.dragRow = i;
                    applySliderAt(i, mx);
                } else if (this.page == Page.THEME && i == 1) {
                    applyPresetClick(mx);
                } else {
                    toggleFlip(this.page, i);
                    saveNow();
                }
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragRow >= 0 && this.transitionStart < 0) {
            applySliderAt(this.dragRow, (int) mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragRow = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applySliderAt(int rowIdx, int mx) {
        int x0 = rowX0() + (rowX1() - rowX0()) * 55 / 100;
        int x1 = rowX1() - 58;
        float frac = Mth.clamp((mx - x0) / (float) (x1 - x0), 0f, 1f);
        float lo = sliderMin(this.page, rowIdx);
        float hi = sliderMax(this.page, rowIdx);
        sliderSet(this.page, rowIdx, lo + frac * (hi - lo));
        saveNow();
    }

    private void applyPresetClick(int mx) {
        float[] hues = {41.44f, 202f, 355f};
        int bw = Math.min(78, (rowX1() - rowX0() - 150 - 12) / 3);
        for (int t = 0; t < 3; t++) {
            int bx = rowX1() - 10 - (3 - t) * (bw + 6) + 6;
            if (mx >= bx - 4 && mx <= bx + bw + 4) {
                SAOConfig.setAccentHue(hues[t]);
                saveNow();
                return;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // O 键层级返回(设置→菜单),消费掉按键避免 keybind 计数触发双重关闭;
        // Esc 走 Screen 默认 onClose → 返回菜单
        if (keyCode == GLFW.GLFW_KEY_O) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        saveNow();
        if (this.minecraft != null) {
            this.minecraft.setScreen(lastScreen);
        }
    }

    // ------------------------------------------------------------ 绘制工具

    private static void fillSlab(GuiGraphics g, float cx, float cy, float w, float h,
                                 float angleDeg, int argb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angleDeg));
        g.fill(Math.round(-w / 2f), Math.round(-h / 2f), Math.round(w / 2f), Math.round(h / 2f), argb);
        pose.popPose();
    }

    private void drawScaled(GuiGraphics g, String text, float x, float y, float scale,
                            int argb, boolean shadow) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        g.drawString(this.font, text, 0, 0, argb, shadow);
        pose.popPose();
    }

    private void drawScaledRot(GuiGraphics g, String text, float x, float y, float scale,
                               float rotDeg, int argb, boolean shadow) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(rotDeg));
        pose.scale(scale, scale, 1f);
        g.drawString(this.font, text, 0, 0, argb, shadow);
        pose.popPose();
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int alpha(float fade, float base) {
        return Math.round(Mth.clamp(fade, 0f, 1f) * base * 255f);
    }

    /** HSV(H,1,1)→ARGB,用于主题预设色块(与 SAOConfig.accent() 同源)。 */
    private static int hsvToArgb(float hue) {
        float hp = (hue % 360f) / 60f;
        float x = 1f - Math.abs(hp % 2f - 1f);
        float r = 0f;
        float gg = 0f;
        float b = 0f;
        switch ((int) hp) {
            case 0 -> { r = 1f; gg = x; }
            case 1 -> { r = x; gg = 1f; }
            case 2 -> { gg = 1f; b = x; }
            case 3 -> { gg = x; b = 1f; }
            case 4 -> { r = x; b = 1f; }
            default -> { r = 1f; b = x; }
        }
        return 0xFF000000
                | (Math.round(r * 255f) << 16)
                | (Math.round(gg * 255f) << 8)
                | Math.round(b * 255f);
    }

    private static float clamp01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }

    private static float easeOutCubic(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    private static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    private static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f * 1.25f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    // ------------------------------------------------------------ 文案键

    private String catTitleKey(int i) {
        return switch (CATS[i]) {
            case LAYOUT -> "saomenu.settings.cat_layout";
            case COMBAT -> "saomenu.settings.cat_combat";
            case HUD -> "saomenu.settings.cat_hud";
            default -> "saomenu.settings.cat_theme";
        };
    }

    private String catSubKey(int i) {
        return switch (CATS[i]) {
            case LAYOUT -> "saomenu.settings.cat_layout_sub";
            case COMBAT -> "saomenu.settings.cat_combat_sub";
            case HUD -> "saomenu.settings.cat_hud_sub";
            default -> "saomenu.settings.cat_theme_sub";
        };
    }

    private String pageTitleKey(Page p) {
        return switch (p) {
            case LAYOUT -> "saomenu.settings.cat_layout";
            case COMBAT -> "saomenu.settings.cat_combat";
            case HUD -> "saomenu.settings.cat_hud";
            default -> "saomenu.settings.cat_theme";
        };
    }

    private String pageSubKey(Page p) {
        return switch (p) {
            case LAYOUT -> "saomenu.settings.cat_layout_sub";
            case COMBAT -> "saomenu.settings.cat_combat_sub";
            case HUD -> "saomenu.settings.cat_hud_sub";
            default -> "saomenu.settings.cat_theme_sub";
        };
    }

    private void saveNow() {
        Path p = SAOConfig.path();
        if (p == null) {
            p = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("saomenu.json");
        }
        SAOConfig.save(p);
    }

    private void playClick() {
        if (SAOConfig.sounds()) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SAOMenuPlatform.clickSound(), 1.0F));
        }
    }

    private void playPanel() {
        if (SAOConfig.sounds()) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SAOMenuPlatform.panelSound(), 1.0F));
        }
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }
}
