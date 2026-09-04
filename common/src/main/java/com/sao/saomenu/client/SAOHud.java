package com.sao.saomenu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sao.saomenu.SAOMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 常驻 SAO HUD:左上角血条/等级板(参照 Kirito 血条样式) +
 * 底部圆点物品栏(取代原版快捷栏,选中槽位橙色高亮)。
 */
public final class SAOHud {

    private static final ResourceLocation TEX_DOT = ResourceLocationHelper.hud("dot.png");
    // SAO Utils 原版血条板贴图(基准尺寸 360x83 / 绿条 258x25 / 徽章 22x22)
    private static final ResourceLocation TEX_HP_BAR = ResourceLocationHelper.hud("hp_bar.png");
    private static final ResourceLocation TEX_HP_GREEN = ResourceLocationHelper.hud("hp_green.png");
    private static final ResourceLocation TEX_HP_ICON = ResourceLocationHelper.hud("hp_icon.png");
    /** 原版 GUI 图标集(饥饿/氧气泡),与 Gui.GUI_ICONS_LOCATION 同源。 */
    private static final ResourceLocation GUI_ICONS = new ResourceLocation("textures/gui/icons.png");

    private static final int TEXT_WHITE = 0xFFF4F4F4;
    // 血条板浅色底上的文字色
    private static final int TEXT_DARK = 0xFF262829;

    // hp_bar.png(360x83)凹槽与标签的逐像素测量值
    private static final float GROOVE_X0 = 87f;         // 凹槽左缘
    private static final float GROOVE_STEP = 211f;      // 粗段→细段台阶处
    private static final float GROOVE_X1 = 328f;        // 凹槽右缘
    private static final float GROOVE_TOP = 39f;        // 凹槽上缘
    private static final float GROOVE_THICK_BOT = 51f;  // 粗段下缘
    private static final float GROOVE_THIN_BOT = 43f;   // 细段下缘
    private static final float GREEN_STEP_U = 134f;     // hp_green 自身台阶 uv

    // 贴图不透明主体在 360x83 画布内的偏移:左 13px、上 25px 的纯透明留白。
    // 绘制时反向平移,让可见主体精确落在调用方给的 (x,y) 上(贴角时才真的贴角)
    private static final float PLATE_PAD_L = 13f;
    private static final float PLATE_PAD_T = 25f;

    // 事件检测状态(升级 / 低血量)
    private static int lastLevel = -1;
    private static boolean lastLow = false;
    // 受击反馈
    private static float lastHp = -1f;
    private static long flashAt = Long.MIN_VALUE;
    private static final long FLASH_MS = 400;

    private SAOHud() {
    }

    public static int plateW(int screenW) {
        return MenuLayout.plateW(screenW);
    }

    public static int plateH(int screenW) {
        return MenuLayout.plateH(screenW);
    }

    /**
     * 血条板整组(板 + 队友血条 + 状态效果行)的总高度,拖动命中范围按整组算。
     *
     * <p>命中比可见范围多留 4 格余量:队友列表为空、也没有药水效果时,
     * 板下方其实什么都没有,但拖动目标太小不好抓;反之整组最高时不超过此值。
     * 命中框恒定也避免拖动中队伍人数变化导致命中区跳变。</p>
     */
    public static int plateGroupH(int screenW, Minecraft mc) {
        return plateH(screenW) + 4 + Math.max(teamRows(mc) * (compactRowH(screenW) + 2), 24);
    }

    /** 血条板左上角 X(锚点比例换算,随配置拖动)。 */
    public static int plateX(int screenW) {
        float fx = Mth.clamp(SAOConfig.platePanelX(), 0f, 1f);
        return Math.max(0, Math.min(Math.round(fx * (screenW - plateW(screenW))), screenW - plateW(screenW)));
    }

    /** 血条板左上角 Y(锚点比例换算,随配置拖动)。 */
    public static int plateY(int screenH) {
        float fy = Mth.clamp(SAOConfig.platePanelY(), 0f, 1f);
        return Math.max(0, Math.min(Math.round(fy * Math.max(1, screenH - 20)), screenH - 20));
    }

    // ------------------------------------------------------------ 血条板拖动
    // 与 SAOClockPanel 同一套交互:菜单屏内按住拖动,松手写盘;
    // 命中范围是整组(板 + 队友血条 + 状态效果行),见 plateGroupH。

    private static boolean plateDragging;
    private static float plateGrabFx;
    private static float plateGrabFy;
    private static boolean plateDraggedSinceDown;

    /** 命中检测:点在血条板整组范围内(仅菜单打开时可拖)。 */
    public static boolean hitPlateGroup(Minecraft mc, int screenW, int screenH, int mx, int my) {
        int gh = plateGroupH(screenW, mc);
        int gx = plateX(screenW);
        int gy = plateY(screenH);
        return mx >= gx && mx < gx + plateW(screenW) && my >= gy && my < gy + gh;
    }

    public static void beginPlateDrag(Minecraft mc, int screenW, int screenH, int mx, int my) {
        int gx = plateX(screenW);
        int gy = plateY(screenH);
        plateGrabFx = (mx - gx) / (float) plateW(screenW);
        plateGrabFy = (my - gy) / (float) Math.max(1, plateGroupH(screenW, mc));
        plateDragging = true;
        plateDraggedSinceDown = false;
    }

    public static void dragPlateTo(int screenW, int screenH, int mx, int my) {
        if (!plateDragging) {
            return;
        }
        int w = plateW(screenW);
        float fx = (mx - plateGrabFx * w) / (float) Math.max(1, screenW - w);
        // Y 的可动范围与 plateY 的钳制一致(底部留 20 格给圆点物品栏)
        float fy = (my - plateGrabFy * 20f) / (float) Math.max(1, screenH - 20);
        SAOConfig.setPlatePanelX(fx);
        SAOConfig.setPlatePanelY(fy);
        plateDraggedSinceDown = true;
    }

    public static void endPlateDragAndSave() {
        if (plateDragging && plateDraggedSinceDown) {
            java.nio.file.Path p = SAOConfig.path();
            if (p == null) {
                p = Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("config").resolve("saomenu.json");
            }
            SAOConfig.save(p);
        }
        plateDragging = false;
    }

    /** 常驻渲染入口(平台 HUD 钩子调用)。 */
    public static void render(GuiGraphics g, Minecraft mc) {
        if (mc.options.hideGui) {
            return;
        }
        // 目标名称/血量文字:世界渲染阶段算好投影,这里在 HUD 层落笔,
        // 绕开光影对世界空间字体批的干扰。菜单打开时也照常显示
        SAOTargetBar3D.renderLabels(g, mc);
        renderHud(g, mc);
        // 图钉固定的地图面板:菜单关闭后仍常显(HUD 层)
        SAOMapPanel.renderHud(g, mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        // 入世欢迎动画画在 HUD 之上,且不受 showHud / 菜单状态影响
        SAOWelcome.render(g, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        // Boss 横幅(HUD 之上)
        SAOBossBanner.render(g, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private static void renderHud(GuiGraphics g, Minecraft mc) {
        if (!SAOConfig.showHud()) {
            return;
        }
        Player p = mc.player;
        if (p == null) {
            return;
        }
        // 菜单打开期间由 SAOMenuScreen 接管绘制(带开关动画 alpha),避免双绘
        if (mc.screen instanceof SAOMenuScreen) {
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int px = plateX(w);
        int py = plateY(h);
        renderPlate(g, px, py, plateW(w), plateH(w),
                p.getGameProfile().getName(), p, 1f);
        // 队友血条:参照 SAO 左上角队伍血条组,排在自己血条板下方
        renderTeamBars(g, mc, px, py + plateH(w) + 2, w, p);
        SAOCombatHud.render(g, mc, w, h, 1f);
        renderHotbarDots(g, w, h, p, 1f);

        // 悬停圆点时显示对应槽位/副手物品的提示
        int mx = (int) (mc.mouseHandler.xpos() * w / (double) mc.getWindow().getScreenWidth());
        int my = (int) (mc.mouseHandler.ypos() * h / (double) mc.getWindow().getScreenHeight());
        for (int i = 0; i < MenuLayout.DOT_COUNT; i++) {
            if (MenuLayout.inDot(w, h, i, mx, my)) {
                ItemStack stack = i == 0 ? p.getOffhandItem() : p.getInventory().getItem(i - 1);
                if (!stack.isEmpty()) {
                    g.renderTooltip(mc.font, stack, mx, my);
                }
                break;
            }
        }

        detectEvents(p);
        SAONotification.render(g, w, h, net.minecraft.Util.getMillis());
        SAOClockPanel.render(g, mc, w, h, 1f);
        renderLowHpVignette(g, w, h, p.getMaxHealth() <= 0f ? 0f : p.getHealth() / p.getMaxHealth());
    }

    /**
     * 居中的原版饥饿条(由 GuiMixin 取消 renderPlayerHealth 后代画)。
     *
     * <p>贴图 u/v 与原版 1.20.1 Gui.renderPlayerHealth 字节码逐项核对:
     * 空格 (16,27)、满格 (52,27)、半格 (61,27);饥饿药水效果时
     * 空格 u+117、满格 u+36、半格 u+36。氧气泡 (16,18)、破裂泡 (25,18),
     * 画在饥饿条上方一行,数量公式 ceil((air-2)*10/max) 同原版。
     * 原版血条/护甲行被取消,坐骑血条(renderVehicleHealth)是独立方法不受影响。</p>
     */
    public static void renderVanillaFoodCentered(GuiGraphics g, Player p) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        int y = h - 39;
        // 10 个图标,步进 8px,末格宽 9px → 整条 81px,水平居中
        int x0 = (w - 81) / 2;
        int food = p.getFoodData().getFoodLevel();
        boolean hungerFx = p.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER);
        RenderSystem.enableBlend();
        for (int i = 0; i < 10; i++) {
            int x = x0 + i * 8;
            g.blit(GUI_ICONS, x, y, 16 + (hungerFx ? 117 : 0), 27, 9, 9);
            int v = food - i * 2;
            if (v >= 2) {
                g.blit(GUI_ICONS, x, y, 52 + (hungerFx ? 36 : 0), 27, 9, 9);
            } else if (v == 1) {
                g.blit(GUI_ICONS, x, y, 61 + (hungerFx ? 36 : 0), 27, 9, 9);
            }
        }
        // 氧气泡:眼睛入水或氧气未满时显示,居中悬在饥饿条上方 10px
        int maxAir = p.getMaxAirSupply();
        int air = Math.min(p.getAirSupply(), maxAir);
        if (!p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) && air >= maxAir) {
            return;
        }
        int yAir = y - 10;
        int full = Mth.ceil((air - 2) * 10.0D / maxAir);
        int total = Mth.ceil(air * 10.0D / maxAir);
        for (int i = 0; i < total; i++) {
            g.blit(GUI_ICONS, x0 + i * 8, yAir, i < full ? 16 : 25, 18, 9, 9);
        }
    }

    /** 低血量屏幕边缘红晕:血量越低越浓,呼吸式闪烁。 */
    static void renderLowHpVignette(GuiGraphics g, int w, int h, float hpFrac) {        if (hpFrac >= 0.2f || hpFrac <= 0f) {
            return;
        }
        float danger = Mth.clamp((0.25f - hpFrac) * 4f, 0f, 1f); // 25% 以下开始出现,越低越浓
        float pulse = 0.6f + 0.4f * Mth.sin(net.minecraft.Util.getMillis() / 150f);
        int layers = 6;
        for (int i = 0; i < layers; i++) {
            int t = Math.round(h * 0.03f + i * (h * 0.06f / layers));
            int a = Math.round(150f * danger * pulse * (layers - i) / layers);
            if (a <= 0) {
                continue;
            }
            int color = (a << 24) | 0xFF2020;
            g.fill(0, 0, w, t, color);
            g.fill(0, h - t, w, h, color);
            g.fill(0, 0, t, h, color);
            g.fill(w - t, 0, w, h, color);
        }
    }

    /** 升级 / 低血量检测:状态变化时推送 SAO 通知。 */
    private static void detectEvents(Player p) {
        if (lastLevel >= 0 && p.experienceLevel > lastLevel) {
            SAONotification.push(tr("saomenu.notify.levelup.title"),
                    tr("saomenu.notify.levelup.msg", p.experienceLevel));
        }
        lastLevel = p.experienceLevel;
        float hpNow = p.getHealth();
        if (lastHp >= 0f && hpNow < lastHp - 0.01f) {
            flashAt = net.minecraft.Util.getMillis();
        }
        lastHp = hpNow;
        float frac = p.getMaxHealth() <= 0f ? 0f : p.getHealth() / p.getMaxHealth();
        boolean low = frac > 0f && frac < 0.2f;
        if (low && !lastLow) {
            SAONotification.push(tr("saomenu.notify.lowhp"), "");
        }
        lastLow = low;
    }

    static String tr(String key, Object... args) {
        // 不依赖 translatable 的 MessageFormat 替换(Forge/Fabric 行为不一致),
        // 手动替换 {n} 占位符,双平台结果一致
        String s = net.minecraft.network.chat.Component.translatable(key).getString();
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    /**
     * 底部圆点物品栏:第 1 个圆点是副手(与主栏隔开一档,不参与选中高亮),
     * 其后 9 个圆点对应快捷栏槽位 0..8,选中槽位橙色高亮;圆点内渲染真实物品图标。
     */
    public static void renderHotbarDots(GuiGraphics g, int screenW, int screenH, Player player, float alpha) {
        // 原版快捷栏可见时,SAO 圆点物品栏让位,避免重叠
        if (player == null || !SAOConfig.hideHotbar()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < MenuLayout.DOT_COUNT; i++) {
            boolean offhand = i == 0;
            int slot = i - 1;
            boolean active = !offhand && slot == Mth.clamp(player.getInventory().selected, 0, 8);
            int d = MenuLayout.dotSize(screenH);
            int x = MenuLayout.dotCenterX(screenW, screenH, i) - d / 2;
            int y = MenuLayout.dotCenterY(screenH) - d / 2;
            if (active) {
                // 白色圆点染成主题色(替代硬编码橙色贴图)
                setTint(SAOConfig.accent(), alpha);
                RenderSystem.enableBlend();
                g.blit(TEX_DOT, x, y, 0, 0, d, d, d, d);
            } else {
                RenderSystem.enableBlend();
                shaderAlpha(alpha);
                g.blit(TEX_DOT, x, y, 0, 0, d, d, d, d);
            }
            shaderAlpha(1f);

            ItemStack stack = offhand ? player.getOffhandItem() : player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                int isz = Mth.clamp(Math.round(d * 0.62f), 4, Math.max(4, Math.min(16, d - 2)));
                float s = isz / 16f;
                // 圆点较小时,原版数量文字会随物品一起缩到不可读,改为下面用固定字号补画。
                // 两者必须互斥:同时画会叠成一团糊字
                boolean ownCount = s < 0.75f && stack.getCount() > 1;
                g.pose().pushPose();
                g.pose().translate(x + d / 2f, y + d / 2f, 120f);
                g.pose().scale(s, s, 1f);
                g.renderItem(stack, -8, -8);
                // renderItemDecorations 只在 count != 1 时画数量,传 count=1 的副本
                // 可以只保留耐久条与冷却遮罩,把数量让给下面的固定字号绘制
                if (ownCount) {
                    ItemStack noCount = stack.copy();
                    noCount.setCount(1);
                    g.renderItemDecorations(mc.font, noCount, -8, -8);
                } else {
                    g.renderItemDecorations(mc.font, stack, -8, -8);
                }
                g.pose().popPose();
                if (ownCount) {
                    String cnt = String.valueOf(stack.getCount());
                    var font = mc.font;
                    // 数字画到圆点外的右下缝隙里,不压在图标上:
                    // 全字号文字(9px 高)在小圆点(约 13-16px)内部必然盖掉大半个物品,
                    // 而圆点间距是直径的 1.7 倍,右侧缝隙约 0.7 直径,正好容得下两位数
                    int tx = x + d - 3;
                    int ty = y + d - font.lineHeight + 2;
                    g.pose().pushPose();
                    // z 抬到物品模型之上:物品是 3D 模型批次,深度比 GUI 平面元素深
                    g.pose().translate(0, 0, 300f);
                    // 不加底衬,只靠原版字体自带投影与背景区分(与 SAO 观感一致)
                    g.drawString(font, cnt, tx, ty, mulAlpha(0xFFFFFFFF, alpha), true);
                    g.pose().popPose();
                }
            }
        }
    }

    // ------------------------------------------------------------ 队友血条

    /** 队友血条紧凑行高(相对主血条板宽的比例,与 hp_bar 凹槽高度一致观感)。 */
    static int compactRowH(int screenW) {
        return Math.max(8, Math.round(plateW(screenW) * 0.13f));
    }

    /** 当前应显示的队友行数(不含自己;无队伍为 0;菜单打开时同样显示)。 */
    public static int teamRows(Minecraft mc) {
        if (!SAOConfig.showHud() || !com.sao.saomenu.party.SAOClientPartyState.inParty()) {
            return 0;
        }
        String self = mc.player != null ? mc.player.getGameProfile().getName() : "";
        int n = 0;
        for (String name : com.sao.saomenu.party.SAOClientPartyState.teamMembers()) {
            if (!name.equals(self) && mc.getConnection() != null
                    && mc.getConnection().getPlayerInfo(name) != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * 队友血条组:每个在线队友一行紧凑血条板(复用 hp_bar.png,右端不带标签格)。
     * 参照 SAO 动画:名字白色、绿条按队友真实血量涨落。
     */
    static void renderTeamBars(GuiGraphics g, Minecraft mc, int x, int y, int screenW, Player self) {
        if (!com.sao.saomenu.party.SAOClientPartyState.inParty()) {
            return;
        }
        String selfName = self.getGameProfile().getName();
        var conn = mc.getConnection();
        if (conn == null) {
            return;
        }
        int rowH = compactRowH(screenW);
        int w = Math.round(plateW(screenW) * 0.78f);
        float s = w / 360f;
        int yy = y;
        for (String name : com.sao.saomenu.party.SAOClientPartyState.teamMembers()) {
            if (name.equals(selfName)) {
                continue;
            }
            var info = conn.getPlayerInfo(name);
            if (info == null) {
                continue;
            }
            // 血量:优先真实玩家实体;实体不在渲染区时退化为满血(在线即满条观感)
            float hp = 20f;
            float maxHp = 20f;
            var ent = mc.level != null ? mc.level.getPlayerByUUID(info.getProfile().getId()) : null;
            if (ent != null) {
                hp = ent.getHealth();
                maxHp = ent.getMaxHealth();
            }
            float frac = maxHp <= 0f ? 0f : Mth.clamp(hp / maxHp, 0f, 1f);
            renderCompactBar(g, x, yy, w, rowH, s, name, frac);
            yy += rowH + 2;
        }
    }

    /** 单行队友血条:hp_bar 贴图 + 名字 + 绿条(粗段/细段台阶同主血条板几何)。 */
    private static void renderCompactBar(GuiGraphics g, int x, int y, int w, int h,
                                         float s, String name, float frac) {
        // 底板:裁掉贴图右侧标签格与透明留白,只画 名字行+凹槽 区域并压扁高度
        RenderSystem.enableBlend();
        g.blit(TEX_HP_BAR, x, y, w, h, PLATE_PAD_L, 30.0F, 315, 22, 360, 83);
        // 名字(凹槽上方小字区)
        Font font = Minecraft.getInstance().font;
        String label = font.width(name) > Math.round(200 * s)
                ? font.plainSubstrByWidth(name, Math.round(190 * s)) + "…" : name;
        g.drawString(font, label, x + Math.round(4 * s),
                y + Math.round((39f - 30f) * s / 2f - 4) + 2, mulAlpha(TEXT_WHITE, 1f), false);
        // 绿条:与主血条板同一凹槽几何(粗段/细段),比例裁剪
        if (frac > 0f) {
            float barScale = s;
            int barX = x + Math.round((GROOVE_X0 - PLATE_PAD_L) * barScale);
            int barY = y + Math.round((GROOVE_TOP - 30f) * barScale);
            int barH = Math.max(2, Math.round((GROOVE_THICK_BOT - GROOVE_TOP) * barScale));
            float fillEnd = GROOVE_X0 + (GROOVE_X1 - GROOVE_X0) * frac;
            float thickEnd = Math.min(fillEnd, GROOVE_STEP);
            int thickW = Math.round((thickEnd - GROOVE_X0) * barScale);
            if (thickW > 0) {
                int uW = Math.max(1, Math.round(GREEN_STEP_U
                        * (thickEnd - GROOVE_X0) / (GROOVE_STEP - GROOVE_X0)));
                g.blit(TEX_HP_GREEN, barX, barY, thickW, barH, 0f, 0f, uW, 25, 258, 25);
            }
            if (fillEnd > GROOVE_STEP) {
                int thinX = x + Math.round((GROOVE_STEP - PLATE_PAD_L) * barScale);
                int thinW = Math.round((fillEnd - GROOVE_STEP) * barScale);
                int thinH = Math.max(1, Math.round((GROOVE_THIN_BOT - GROOVE_TOP + 1.5f) * barScale));
                int uW = Math.max(1, Math.round((258 - GREEN_STEP_U)
                        * (fillEnd - GROOVE_STEP) / (GROOVE_X1 - GROOVE_STEP)));
                if (thinW > 0) {
                    g.blit(TEX_HP_GREEN, thinX, barY, thinW, thinH,
                            GREEN_STEP_U, 0f, uW, 17, 258, 25);
                }
            }
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * SAO 血条板(SAO Utils 官方贴图复刻,几何逐像素对齐 hp_bar.png 360x83):
     * 左端 [+] 徽章块(x12..34)+ 名字/头像位(x36..86)+ 血条凹槽
     * (粗段 x87..211 y39..51,台阶细段 x211..328 y39..43)+
     * 右下双格标签(x220..348 y64..79,分隔线 x305:左格血量、右格等级)。
     * 名称位用第一人称头像;受击闪红与低血量红色脉冲/变色效果保留。
     */
    public static void renderPlate(GuiGraphics g, int x, int y, int w, int h,
                                   String name, Player p, float alpha) {
        float hp = p != null ? p.getHealth() : 20f;
        float maxHp = p != null ? p.getMaxHealth() : 20f;
        int level = p != null ? p.experienceLevel : 1;
        float frac = maxHp <= 0 ? 0f : Mth.clamp(hp / maxHp, 0f, 1f);
        // 所有像素坐标以贴图 360x83 为基准等比例缩放
        float s = w / 360f;
        // 底板整体左上平移掉贴图自带的透明留白,可见主体精确从 (x,y) 起笔。
        // 板内元件(凹槽/徽章/头像/标签)与底板共用同一原点,平移后相对位置不变
        x = Math.round(x - PLATE_PAD_L * s);
        y = Math.round(y - PLATE_PAD_T * s);

        // 1) 底板(贴图自带半透明 alpha)。必须用独立拉伸的 10 参重载:
        //    8 参重载会把目标宽高当作源采样宽高,只画出贴图左上角一块并裁掉右侧标签格
        //    混合必须显式开启:菜单 Screen 的渲染路径里,前面的 fill() 已把混合关掉,
        //    贴图边缘的低 alpha 柔和阴影会被画成实心黑框
        RenderSystem.enableBlend();
        shaderAlpha(alpha);
        g.blit(TEX_HP_BAR, x, y, w, h, 0f, 0f, 360, 83, 360, 83);

        // 2) 绿条:凹槽分粗段与台阶细段两截,hp_green 同样带台阶(uv 分界 x=134),
        //    按血量比例从左裁剪;血量低时着色器乘色把绿转红(保留颜色改变效果)
        int barX = x + Math.round(GROOVE_X0 * s);
        int barY = y + Math.round(GROOVE_TOP * s);
        int barH = Math.max(2, Math.round((GROOVE_THICK_BOT - GROOVE_TOP) * s));
        if (frac > 0f) {
            float warm = Mth.clamp((0.6f - frac) / 0.45f, 0f, 1f); // 60% 起转红,15% 全红
            RenderSystem.setShaderColor(1f, 1f - warm * 0.72f, 1f - warm * 0.68f, alpha);
            float fillEnd = GROOVE_X0 + (GROOVE_X1 - GROOVE_X0) * frac;
            // 粗段:贴图 uv x 0..134(hp_green 台阶前),高度取满 25 → 凹槽粗段高
            float thickEnd = Math.min(fillEnd, GROOVE_STEP);
            int thickW = Math.round((thickEnd - GROOVE_X0) * s);
            if (thickW > 0) {
                int uW = Math.max(1, Math.round(GREEN_STEP_U
                        * (thickEnd - GROOVE_X0) / (GROOVE_STEP - GROOVE_X0)));
                g.blit(TEX_HP_GREEN, barX, barY, thickW, barH, 0f, 0f, uW, 25, 258, 25);
            }
            // 细段:贴图 uv x 134..258,压到凹槽台阶后的薄高度
            if (fillEnd > GROOVE_STEP) {
                int thinX = x + Math.round(GROOVE_STEP * s);
                int thinW = Math.round((fillEnd - GROOVE_STEP) * s);
                int thinH = Math.max(1, Math.round((GROOVE_THIN_BOT - GROOVE_TOP + 1.5f) * s));
                int uW = Math.max(1, Math.round((258 - GREEN_STEP_U)
                        * (fillEnd - GROOVE_STEP) / (GROOVE_X1 - GROOVE_STEP)));
                if (thinW > 0) {
                    g.blit(TEX_HP_GREEN, thinX, barY, thinW, thinH,
                            GREEN_STEP_U, 0f, uW, 17, 258, 25);
                }
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        }

        // 3) 左端 [+] 徽章(承托块 x12..34、y25..66 内居中)
        int iconW = Math.max(8, Math.round(18f * s));
        int iconX = x + Math.round(23f * s) - iconW / 2;
        int iconY = y + Math.round(45.5f * s) - iconW / 2;
        RenderSystem.enableBlend();
        shaderAlpha(alpha);
        g.blit(TEX_HP_ICON, iconX, iconY, iconW, iconW, 0f, 0f, 22, 22, 22, 22);

        // 4) 名字位(x36..86)-> 第一人称皮肤头像(关闭头像时回退名字文本)
        Font font = Minecraft.getInstance().font;
        int avS = Math.max(8, Math.round(26f * s));
        int avX = x + Math.round(61f * s) - avS / 2;
        int avY = y + Math.round(45.5f * s) - avS / 2;
        if (SAOConfig.showAvatar() && p != null) {
            renderAvatar(g, Minecraft.getInstance(), p, avX, avY, avS, alpha);
        } else {
            int maxW = Math.round(48f * s);
            String nameText = font.width(name) <= maxW ? name
                    : font.plainSubstrByWidth(name, maxW - font.width("…")) + "…";
            g.drawString(font, nameText, x + Math.round(38f * s),
                    y + Math.round(45.5f * s) - 4, mulAlpha(TEXT_DARK, alpha), false);
        }

        // 5) 右下双格标签:左格"血量 / 上限"、右格"Lv:等级"(分隔线 x=305)
        int labelY = y + Math.round(71.5f * s) - 4;
        String hpText = trimHp(hp) + " / " + trimHp(maxHp);
        String lvText = "Lv:" + level;
        g.drawString(font, hpText,
                x + Math.round(262.5f * s) - font.width(hpText) / 2, labelY,
                mulAlpha(TEXT_DARK, alpha), false);
        g.drawString(font, lvText,
                x + Math.round(326.5f * s) - font.width(lvText) / 2, labelY,
                mulAlpha(TEXT_DARK, alpha), false);

        // 6) 受击闪红(整个板叠一层快速衰减的红)
        long flashAge = net.minecraft.Util.getMillis() - flashAt;
        if (flashAge >= 0 && flashAge < FLASH_MS) {
            float fa = (1f - flashAge / (float) FLASH_MS) * alpha;
            g.fill(x, y, x + w, y + h, mulAlpha(0x59FF3030, fa));
        }
        // 7) 低血量脉冲:仅在已填充的血条段上叠呼吸式红光(不覆盖空槽)
        if (frac > 0f && frac < 0.2f) {
            float pulse = 0.35f + 0.45f * Mth.sin(net.minecraft.Util.getMillis() / 130f);
            int pulseW = Math.round((GROOVE_X1 - GROOVE_X0) * frac * s);
            if (pulseW > 0) {
                g.fill(barX, barY, barX + pulseW, barY + barH, mulAlpha(0x66FF2020, pulse * alpha));
            }
        }
    }

    /**
     * 皮肤平面头像:取 64x64 皮肤纹理的正面脸部区域(UV 8,8 → 16,16)
     * 放大绘制,外层 1px 主题色描边。皮肤尚未加载完成时显示默认皮肤。
     */
    private static void renderAvatar(GuiGraphics g, Minecraft mc, Player p,
                                     int x, int y, int size, float alpha) {
        net.minecraft.resources.ResourceLocation skin =
                mc.getSkinManager().getInsecureSkinLocation(p.getGameProfile());
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, mulAlpha(SAOConfig.accent(), alpha));
        RenderSystem.enableBlend();
        shaderAlpha(alpha);
        g.blit(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        shaderAlpha(1f);
    }

    private static String trimHp(float v) {
        float r = Math.round(v * 10f) / 10f;
        return (r == Math.rint(r)) ? String.valueOf((int) r) : String.valueOf(r);
    }

    private static void shaderAlpha(float a) {
        RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(a, 0f, 1f));
    }

    /** 按主题色 ARGB 染色(把白色贴图染成主题色)。 */
    private static void setTint(int argb, float alpha) {
        RenderSystem.setShaderColor(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                Mth.clamp(alpha, 0f, 1f));
    }

    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        return (Math.round(a * Mth.clamp(factor, 0f, 1f)) << 24) | rgb;
    }

    /** 贴图路径小工具,避免 SAOHud 与菜单 Screen 重复拼接。 */
    static final class ResourceLocationHelper {
        static ResourceLocation hud(String name) {
            return new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/" + name);
        }

        private ResourceLocationHelper() {
        }
    }
}
