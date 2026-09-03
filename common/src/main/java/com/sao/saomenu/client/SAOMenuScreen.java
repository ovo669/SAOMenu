package com.sao.saomenu.client;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.SAOMenuPlatform;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * SAO Utils 风格圆形菜单主界面。
 *
 * <p>对照 SAO-World 参考截图 1:1 还原:世界保持全亮(无遮罩),
 * 菜单以活动按钮为锚点位于屏幕左上区域、整体缓缓上下浮动;
 * 打开时从按钮处缩放弹出,卡片与菜单项带级联滑入动画,
 * 关闭时缩回消失。不含原视频右侧任务栏。</p>
 */
public class SAOMenuScreen extends Screen {

    // ---------------------------------------------------------------- 配色(参考截图实测)
    private static final int CARD_LINE = 0xFFA09FA0;      // 名字下划线
    private static final int SHADOW = 0x3A303030;         // box-shadow 3px 3px 2px #888
    private static final int TEXT_DARK = 0xFF3C3C3D;
    private static final int TEXT_ON_ORANGE = 0xFFF9F9F9;

    private static final ResourceLocation TEX_BTN = tex("btn_circle.png");
    private static final ResourceLocation TEX_BTN_NORMAL = tex("btn_normal.png");
    private static final ResourceLocation TEX_BTN_HOVER = tex("btn_hover.png");
    private static final ResourceLocation TEX_LIST_NORMAL = tex("list_normal.png");
    private static final ResourceLocation TEX_LIST_HOVER = tex("list_hover.png");
    private static final ResourceLocation TEX_INDICATOR = tex("indicator.png");
    private static final ResourceLocation TEX_PANEL = tex("panel.png");
    private static final ResourceLocation TEX_ALERT = tex("alert.png");
    private static final ResourceLocation TEX_BTN_OK = tex("btn_ok.png");
    private static final ResourceLocation TEX_BTN_OK_HOVER = tex("btn_ok_hover.png");
    private static final ResourceLocation TEX_BTN_CANCEL = tex("btn_cancel.png");
    private static final ResourceLocation TEX_BTN_CANCEL_HOVER = tex("btn_cancel_hover.png");
    private static final ResourceLocation TEX_BTN_PRESS = tex("btn_press.png");
    private static final ResourceLocation TEX_LIST_PRESS = tex("list_press.png");
    private static final ResourceLocation TEX_ITEM_MAP = tex("item_map.png");
    private static final ResourceLocation TEX_ACT_EQUIP = tex("item_run.png");
    private static final ResourceLocation TEX_ACT_EQUIP_H = tex("item_run_hover.png");
    private static final ResourceLocation TEX_ACT_INFO = tex("item_help.png");
    private static final ResourceLocation TEX_ACT_INFO_H = tex("item_help_hover.png");
    private static final ResourceLocation TEX_ACT_DROP = tex("item_remove.png");
    private static final ResourceLocation TEX_ACT_DROP_H = tex("item_remove_hover.png");
    private static final ResourceLocation TEX_ARROW_RIGHT = tex("arrow_right.png");
    private static final ResourceLocation TEX_RING = tex("ring.png");
    private static final ResourceLocation TEX_SILHOUETTE = tex("card_silhouette.png");

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/" + name);
    }

    /**
     * 开混合后贴 GUI 贴图。
     *
     * <p>{@code GuiGraphics.fill()} 收尾会把混合关掉,而 {@code blit()} 不管理混合状态;
     * 「fill 阴影 → blit 面板」这种顺序会让贴图边缘的低 alpha 像素(柔和阴影)
     * 以实心纯黑画出,表现为面板四周一圈黑框(与 SAOWelcome 面板同款问题)。</p>
     */
    private static void blitBlended(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        g.blit(tex, x, y, 0, 0, w, h, w, h);
    }

    // ---------------------------------------------------------------- 菜单模型

    private enum Action {
        NONE, OPEN_OPTIONS, OPEN_CONFIG, OPEN_STATS, OPEN_ADVANCEMENTS, CLOSE,
        SWITCH_FRIENDS, SWITCH_PARTY, SHOW_EQUIP, SHOW_ITEMS, INVITE_PLAYER, LEAVE_TEAM, TOGGLE_MAP, SKILL
    }

    /** 装备条目分类(第三列展示哪一栏装备)。 */
    private enum EquipKind { WEAPON, ARMOR, TRINKET }

    private record MainButton(String icon, Kind kind) {
        enum Kind {PROFILE, PARTY, FRIENDS, SETTINGS}
    }

    /**
     * 菜单项。stack 非空时:label 即物品显示名、图标渲染为 3D 物品、
     * invSlot 为背包槽位(装备/丢弃操作回传服务端用)。
     */
    private record MenuItem(String label, String icon, Action action, MenuItem[] children,
                            ItemStack stack, int invSlot) {
        MenuItem(String label, String icon, Action action, MenuItem[] children) {
            this(label, icon, action, children, null, -1);
        }

        MenuItem(String label, String icon, Action action) {
            this(label, icon, action, null, null, -1);
        }
    }

    private static final MainButton[] MAIN_BUTTONS = {
            new MainButton("info", MainButton.Kind.PROFILE),
            new MainButton("party", MainButton.Kind.PARTY),
            new MainButton("msg", MainButton.Kind.FRIENDS),
            new MainButton("setting", MainButton.Kind.SETTINGS),
    };

    private static final MenuItem[] PROFILE_ITEMS = {
            // 技能:装饰性剑技列表(本游戏暂无技能系统,点击提示暂未开放)
            new MenuItem("saomenu.menu.skill", "item_status", Action.NONE, new MenuItem[]{
                    new MenuItem("saomenu.skill.horizontal", "item_weapon", Action.SKILL),
                    new MenuItem("saomenu.skill.slant", "item_weapon", Action.SKILL),
                    new MenuItem("saomenu.skill.vertical", "item_weapon", Action.SKILL),
                    new MenuItem("saomenu.skill.linear", "item_weapon", Action.SKILL),
                    new MenuItem("saomenu.skill.sonic_leap", "item_run", Action.SKILL),
                    new MenuItem("saomenu.skill.starburst", "item_weapon", Action.SKILL),
            }),
            new MenuItem("saomenu.menu.equip", "item_weapon", Action.NONE, new MenuItem[]{
                    new MenuItem("saomenu.menu.weapon", "item_weapon", Action.SHOW_EQUIP),
                    new MenuItem("saomenu.menu.armor", "item_armor", Action.SHOW_EQUIP),
                    new MenuItem("saomenu.menu.trinket", "item_ring", Action.SHOW_EQUIP),
            }),
            new MenuItem("saomenu.menu.items", "item_bag", Action.SHOW_ITEMS),
            new MenuItem("saomenu.menu.map", "item_map", Action.TOGGLE_MAP),
    };

    /** 二级子项对应的装备分类:与 PROFILE_ITEMS 里「装备」的 children 下标一一对应。 */
    private static final EquipKind[] EQUIP_KINDS = {EquipKind.WEAPON, EquipKind.ARMOR, EquipKind.TRINKET};

    /** 装备条目:一行 = 一个已装备的物品。stack 为空表示「暂无装备」占位行。 */
    private record EquipEntry(ItemStack stack, boolean empty) {
    }

    /** 队伍面板的动态菜单项:在队伍中 = 离开队伍;不在 = 提示邀请入口。 */
    private static final MenuItem[] PARTY_ITEMS = {
            new MenuItem("saomenu.menu.invite", "item_status", Action.NONE),
            new MenuItem("saomenu.menu.leave_team", "item_bag", Action.LEAVE_TEAM),
    };

    /** 邀请玩家二级列:每个在线玩家一项(label 运行时替换)。children 语义复用。 */
    private MenuItem[] inviteItems() {
        List<MenuItem> list = new ArrayList<>();
        if (mc().getConnection() != null) {
            for (PlayerInfo info : mc().getConnection().getOnlinePlayers()) {
                String name = info.getProfile().getName();
                if (name.equals(playerName())) {
                    continue;
                }
                list.add(new MenuItem(name, "item_status", Action.INVITE_PLAYER));
            }
        }
        if (list.isEmpty()) {
            list.add(new MenuItem("saomenu.panel.no_players", "item_status", Action.NONE));
        }
        return list.toArray(new MenuItem[0]);
    }

    private static final MenuItem[] FRIENDS_ITEMS = {
            new MenuItem("saomenu.menu.advancements", "item_status", Action.OPEN_ADVANCEMENTS),
            new MenuItem("saomenu.menu.refresh", "item_bag", Action.SWITCH_FRIENDS),
    };

    private static final MenuItem[] SETTINGS_ITEMS = {
            new MenuItem("saomenu.menu.config", "item_config", Action.OPEN_CONFIG),
            new MenuItem("saomenu.menu.options", "item_status", Action.OPEN_OPTIONS),
            new MenuItem("saomenu.menu.close", "item_logout", Action.CLOSE),
    };

    // ---------------------------------------------------------------- 动画状态

    private static final long OPEN_MS = 260;
    private static final long PANEL_MS = 200;
    private static final long ITEM_STAGGER_MS = 45;
    private static final long ITEM_MS = 180;
    private static final long CLOSE_MS = 170;
    private static final long BOB_PERIOD_MS = 2800;

    private long openedAt;
    /** 开启动画只在新实例首次 init 时计时(resize 触发的 init 不重置)。 */
    private boolean openAnimArmed = true;
    private long panelAt = Long.MIN_VALUE;
    private int panelOwner = -1;
    private long childAt;
    private int childOwner = -1;
    private boolean closing;
    private long closedAt;

    // 装备第三列:哪个二级子项(武器/护甲/首饰)正在展示已装备物品
    private int equipOwner = -1;
    private long equipAt;
    /** 当前正在渲染装备列的子项(含悬停临时切换),变化时重置列动画。 */
    private int equipShownOwner = -1;

    private int selectedMain = -1;
    /** 是否已点击/滚轮选择过主按钮:未选择前列内全亮,选择后其余按钮压暗。 */
    private boolean mainTouched = false;
    private int hoverMain = -1;
    private int hoverItem = -1;
    private int hoverChild = -1;
    private int hoverEquip = -1;
    private int expandedItem = -1;

    // Logout 确认弹窗(参照 SAO_Utils Alert 窗)
    private boolean confirmClose;
    private long confirmAt;

    // 上一帧菜单组的变换参数(命中判定需做逆变换,与渲染保持一致)
    private float menuScale = 1f;
    private int menuAnchorX;
    private int menuAnchorY;

    // 打开时的首按钮锚点:跟随鼠标模式由光标位置钳制(参照 SAO_Utils),否则用配置锚点
    private int baseAnchorX;
    private int baseAnchorY;

    public SAOMenuScreen() {
        super(Component.translatable("saomenu.title"));
    }

    @Override
    protected void init() {
        // 从设置等子界面返回:菜单此前 beginClose 后被挂起(closing 未走完),
        // 直接显示会因 closeP>=1 第一帧即置空,这里复位关闭状态并重播开启动画
        if (closing) {
            closing = false;
            confirmClose = false;
            openAnimArmed = true;
        }
        // 只有新开菜单才重播开启动画;resize 重新 init 不重置(否则窗口变化时闪烁)
        if (openAnimArmed) {
            openedAt = now();
            openAnimArmed = false;
        }
        mainTouched = false;
        // 菜单位置固定:锚点 = 屏幕中线左侧(SAO-World 参照),不跟随鼠标
        baseAnchorX = MenuLayout.firstButtonCenterX(this.width);
        baseAnchorY = MenuLayout.firstButtonCenterY(this.height);
        menuAnchorX = baseAnchorX;
        menuAnchorY = baseAnchorY;
        playLauncher();
        SAOMenu.LOGGER.info("[SAOMenu] gui size {}x{} anchor {}x{} (fixed)",
                this.width, this.height, baseAnchorX, baseAnchorY);
    }

    /** 第 index 个主按钮圆心 Y(基于打开时锚点)。 */
    private int buttonY(int index) {
        return MenuLayout.buttonCenterYAt(this.height, baseAnchorY, index);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static long now() {
        return Util.getMillis();
    }

    private Minecraft mc() {
        return Minecraft.getInstance();
    }

    /**
     * 当前生效的主按钮(面板跟随点击,不跟随悬停——参照 SAO-World)。
     * 未点击任何按钮时返回 -1:只显示主按钮列。
     */
    private int activeMain() {
        return selectedMain;
    }

    /**
     * 世界空间菜单板({@link SAOMenu3DPanel})的显示强度:菜单打开/关闭动画
     * 期间从 0 渐变到 1 再回落,与 HUD 菜单的开合节奏同源。
     */
    public float worldMenuAlpha() {
        long t = now();
        if (closing) {
            return Mth.clamp(1f - (t - closedAt) / (float) CLOSE_MS, 0f, 1f);
        }
        return Mth.clamp((t - openedAt) / 150f, 0f, 1f);
    }

    /** 世界空间菜单板当前应高亮的主按钮(0..3);菜单收起或未选择时 -1。 */
    public int worldMenuMain() {
        return closing ? -1 : selectedMain;
    }

    private boolean isActive(int index) {
        return hoverMain == index || selectedMain == index;
    }

    private MenuItem[] activeItems(int main) {
        MenuItem[] items = switch (MAIN_BUTTONS[main].kind()) {
            case PROFILE -> PROFILE_ITEMS;
            case PARTY -> PARTY_ITEMS;
            case FRIENDS -> FRIENDS_ITEMS;
            case SETTINGS -> SETTINGS_ITEMS;
        };
        // 队伍面板:「邀请玩家」展开在线玩家列(动态 children)
        if (MAIN_BUTTONS[main].kind() == MainButton.Kind.PARTY && items == PARTY_ITEMS) {
            if (expandedItem == 0) {
                return partyItemsWithInviteChildren();
            }
            // 未展开时还原静态定义(清掉上一帧缓存的 children)
            PARTY_ITEMS[0] = new MenuItem("saomenu.menu.invite", "item_status", Action.NONE);
        }
        // 个人面板:「物品」展开背包条目列(动态 children,SAO 菜单条目样式)
        if (MAIN_BUTTONS[main].kind() == MainButton.Kind.PROFILE && items == PROFILE_ITEMS) {
            if (expandedItem == 2) {
                return profileItemsWithInvChildren();
            }
            // 未展开时还原静态定义;action 必须保持 SHOW_ITEMS,
            // 否则点击分支认不出该项可展开(表现为点了没反应)
            PROFILE_ITEMS[2] = new MenuItem("saomenu.menu.items", "item_bag", Action.SHOW_ITEMS);
        }
        return items;
    }

    /** 把「物品」项挂上动态背包 children(缓存 1 秒刷新,条目 = 每个非空物品一格)。 */
    private MenuItem[] profileItemsWithInvChildren() {
        if (invChildrenCacheAt == 0 || now() - invChildrenCacheAt > 1000) {
            invChildrenCache = invChildItems();
            invChildrenCacheAt = now();
        }
        PROFILE_ITEMS[2] = new MenuItem("saomenu.menu.items", "item_bag", Action.NONE, invChildrenCache);
        return PROFILE_ITEMS;
    }

    /** 背包 → 菜单条目:快捷栏 + 主背包的非空物品;全空给占位行。 */
    private MenuItem[] invChildItems() {
        List<MenuItem> list = new ArrayList<>();
        Player p = mc().player;
        if (p != null) {
            for (int i = 0; i < 36; i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    list.add(new MenuItem(s.getHoverName().getString(), "", Action.NONE, null,
                            s, i));
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new MenuItem("saomenu.inv.empty", "item_bag", Action.NONE));
        }
        return list.toArray(new MenuItem[0]);
    }

    /** 物品条目 children 缓存(1 秒刷新背包变化)。 */
    private MenuItem[] invChildrenCache;
    private long invChildrenCacheAt;

    /** 把「邀请玩家」项挂上动态在线玩家 children(缓存避免每帧重建数组)。 */
    private MenuItem[] partyItemsWithInviteChildren() {
        if (inviteChildrenCacheAt == 0 || now() - inviteChildrenCacheAt > 1000) {
            inviteChildrenCache = inviteItems();
            inviteChildrenCacheAt = now();
        }
        PARTY_ITEMS[0] = new MenuItem("saomenu.menu.invite", "item_status", Action.NONE, inviteChildrenCache);
        return PARTY_ITEMS;
    }

    /** 邀请列缓存(1 秒刷新在线名单)。 */
    private MenuItem[] inviteChildrenCache;
    private long inviteChildrenCacheAt;

    /** 本次点击命中的菜单项原始标签(INVITE_PLAYER 时是被邀请人名)。 */
    private String lastClickedLabel;

    // 物品操作按钮(参照动画:选中行右侧弹出三圆钮)
    private boolean actionMenuOpen;
    private int actionRow = -1;      // 二级列窗口内行下标
    private long actionAt;
    private int hoverAction = -1;
    private static final String[] ACT_KEYS = {
            "saomenu.act.equip", "saomenu.act.info", "saomenu.act.drop"};

    // 物品信息弹窗(白卡属性行 + 官方圆钮)
    private boolean infoOpen;
    private long infoAt;
    private ItemStack infoStack;

    // 伪3D:整组菜单随鼠标轻微倾斜(平滑后的倾斜量)
    private float swayXs;
    private float swayYs;

    // 二级展开时整组左移(参照 SAO-World:子菜单落在一级列原位),平滑跟随
    private float shiftXs;

    // 整组随鼠标轻微漂移(悬浮感),平滑后的屏幕像素偏移
    private float followXs;
    private float followYs;
    private float followPxX;
    private float followPxY;

    // 移动穿透:GLFW 键值缓存(首次遍历键值空间解析各移动键的键码)
    private static final int[] MOVE_KEY_CODES = new int[7];
    private static boolean moveKeyCodesResolved;

    /**
     * 每帧直接轮询 GLFW 按键状态同步移动键(事件路径不可靠时的保底,
     * 直接驱动 KeyMapping.isDown,而 LocalPlayer.aiStep 每帧无条件读取它)。
     */
    private void pollMoveKeys() {
        if (!moveKeyCodesResolved) {
            KeyMapping[] kms = moveKeys();
            for (int code = 32; code <= 348; code++) {
                for (int i = 0; i < kms.length; i++) {
                    if (MOVE_KEY_CODES[i] == 0 && kms[i].matches(code, 0)) {
                        MOVE_KEY_CODES[i] = code;
                    }
                }
            }
            moveKeyCodesResolved = true;
        }
        long win = mc().getWindow().getWindow();
        KeyMapping[] kms = moveKeys();
        for (int i = 0; i < kms.length; i++) {
            kms[i].setDown(MOVE_KEY_CODES[i] != 0
                    && com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, MOVE_KEY_CODES[i]));
        }
    }

    /** 松开全部移动键(关菜单/弹窗时防卡键)。 */
    private void releaseMoveKeys() {
        for (KeyMapping km : moveKeys()) {
            km.setDown(false);
        }
    }

    // 按压态:点击瞬间高亮对应图元(模拟视频里手指点触反馈),120ms 后回弹
    private static final long PRESS_MS = 120;
    private long mainPressAt = Long.MIN_VALUE;
    private int mainPressIndex = -1;
    private long itemPressAt = Long.MIN_VALUE;
    private int itemPressIndex = -1;
    private int itemPressColumn = -1; // 0=一级列,1=二级列

    // 二级列滚动(物品条目 30+ 行放不下一屏):childScroll = 窗口起点
    private int childScroll;
    /** 二级列可见行数(按屏高算)。 */
    private int childVisibleRows() {
        int step = MenuLayout.itemH(this.height) + MenuLayout.itemGap(this.height);
        return Math.max(3, (this.height - 24) / step);
    }

    /**
     * 窗口化二级 children:物品条目超过一屏时只取 [childScroll, childScroll+rows)。
     * 非物品列原样返回。窗口数组带缓存(同源同滚动直接复用,避免每帧新建)。
     */
    private MenuItem[] windowedChildren(MenuItem[] children) {
        int rows = childVisibleRows();
        if (children.length <= rows) {
            // 不满一屏:整个列表直接显示,滚动归零
            // (注意不能走下面的 clamp——length-rows 为负时 clamp 会返回负数导致越界崩溃)
            childScroll = 0;
            windowCacheSrc = null;
            return children;
        }
        if (windowCacheSrc == children && windowCacheScroll == childScroll && windowCacheOut != null) {
            return windowCacheOut;
        }
        childScroll = Mth.clamp(childScroll, 0, children.length - rows);
        MenuItem[] win = new MenuItem[rows];
        for (int v = 0; v < rows; v++) {
            win[v] = children[childScroll + v];
        }
        windowCacheSrc = children;
        windowCacheScroll = childScroll;
        windowCacheOut = win;
        return win;
    }

    /** 窗口缓存键:源数组引用 + 滚动偏移。 */
    private MenuItem[] windowCacheSrc;
    private int windowCacheScroll = -1;
    private MenuItem[] windowCacheOut;

    private boolean mainPressing(int index) {
        return mainPressIndex == index
                && now() - mainPressAt < PRESS_MS;
    }

    private boolean itemPressing(int column, int index) {
        return itemPressColumn == column && itemPressIndex == index
                && now() - itemPressAt < PRESS_MS;
    }

    // ---------------------------------------------------------------- 物品条目(二级 children)

    private void switchPanelIfChanged(int main) {
        if (panelOwner != main) {
            panelOwner = main;
            panelAt = now();
            expandedItem = -1;
            childOwner = -1;
            equipOwner = -1;
            equipShownOwner = -1;
            actionMenuOpen = false;
            childScroll = 0;
            infoOpen = false;
        }
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = now();

        // 世界已卸载(退网/回主菜单):立即关闭,避免后续渲染读到 null 玩家数据
        if (mc().player == null || mc().level == null) {
            super.onClose();
            return;
        }

        // 移动穿透:每帧轮询 GLFW 按键;弹窗/关闭时全部松开防卡键
        if (!closing && !confirmClose && !infoOpen) {
            pollMoveKeys();
        } else {
            releaseMoveKeys();
        }

        float openP = closing ? 1f : clamp01((now - openedAt) / (float) OPEN_MS);
        float closeP = closing ? clamp01((now - closedAt) / (float) CLOSE_MS) : 0f;
        if (closing && closeP >= 1f) {
            super.onClose();
            return;
        }
        float globalAlpha = closing ? 1f - closeP : clamp01((now - openedAt) / 150f);

        if (!closing) {
            updateHovers(mouseX, mouseY);
        }

        int main = activeMain();
        if (main >= 0 && !closing) {
            switchPanelIfChanged(main);
        }

        // 底部圆点 = 物品栏,不参与浮动/缩放(第 1 个为副手,与主栏隔开一档;圆点内渲染真实物品图标)
        Player pp = mc().player;
        SAOHud.renderHotbarDots(g, this.width, this.height, pp, globalAlpha);

        // 菜单组:整体上下浮动 + 打开/关闭缩放,锚点 = 活动按钮圆心
        float bobY = Mth.sin(now / (float) BOB_PERIOD_MS * Mth.TWO_PI) * this.height * 0.006f * SAOConfig.bobAmp();
        float scale = closing
                ? 1f - 0.15f * easeOutCubic(closeP)
                : (0.55f + 0.45f * easeOutBack(openP)) * SAOConfig.menuScale();
        int ax = baseAnchorX;
        int ay = buttonY(Math.max(0, main)) + Math.round(bobY);
        menuScale = scale;
        menuAnchorX = ax;
        menuAnchorY = ay;

        // 二级列可见时整组左移一个列宽(子菜单正好落在一级列原位),平滑跟随
        float shiftTarget = 0f;
        if (main >= 0) {
            MenuItem[] its = activeItems(main);
            if (visibleChildrenItem(its) >= 0) {
                shiftTarget = MenuLayout.childColumnXAt(baseAnchorX, this.height)
                        - MenuLayout.itemColumnXAt(baseAnchorX, this.height);
            }
        }
        shiftXs += (shiftTarget - shiftXs) * 0.18f;

        // 整组随鼠标轻微漂移(以屏幕中心为原点,幅度 ~1.8% 屏宽)
        float fx = Mth.clamp((mouseX - this.width / 2f) / (float) this.width, -0.5f, 0.5f);
        float fy = Mth.clamp((mouseY - this.height / 2f) / (float) this.height, -0.5f, 0.5f);
        followXs += (fx - followXs) * 0.08f;
        followYs += (fy - followYs) * 0.08f;
        followPxX = followXs * this.width * 0.035f;
        followPxY = followYs * this.height * 0.035f;

        var pose = g.pose();
        pose.pushPose();
        pose.translate(ax, ay, 0);
        // 伪3D(2D 仿射安全版):Z 轴微旋转 + 错切模拟透视倾斜,全程 z=0。
        // 之前用 X/Y 轴真 3D 旋转会把顶点推出 GUI 深度安全范围,
        // 在 ImmediatelyFast/Oculus 合批渲染下表现为整组菜单闪烁重影
        float tx = Mth.clamp((mouseX - ax) / (float) Math.max(1, this.width), -0.6f, 0.6f);
        float ty = Mth.clamp((mouseY - ay) / (float) Math.max(1, this.height), -0.6f, 0.6f);
        swayXs += (tx - swayXs) * 0.14f;
        swayYs += (ty - swayYs) * 0.14f;
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(swayXs * 0.03f));
        org.joml.Matrix4f sway = new org.joml.Matrix4f(
                1f, swayYs * 0.05f, 0f, 0f,
                swayXs * 0.06f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f);
        pose.last().pose().mul(sway);
        pose.scale(scale, scale, 1f);
        pose.translate(-ax, -ay, 0);
        pose.translate(-shiftXs, 0.0F, 0.0F);
        pose.translate(followPxX, followPxY, 0.0F);

        if (main >= 0) {
            renderPanelFor(g, main, mouseX, mouseY, globalAlpha, now);
            renderMenuItems(g, main, mouseX, mouseY, globalAlpha, now);
        }
        renderMainButtons(g, globalAlpha);

        pose.popPose();

        // 悬停物品条目(二级列)的原版 tooltip:名称/附魔/耐久,压在所有图元上
        // hoverChild 是窗口内下标,真实条目 = childScroll + hoverChild
        // 弹窗/操作按钮打开时不画(tooltip 会盖在它们上面)
        if (!infoOpen && !confirmClose && !actionMenuOpen) {
            int mainTip = activeMain();
            MenuItem[] itemsTip = mainTip >= 0 ? activeItems(mainTip) : null;
            if (itemsTip != null && hoverChild >= 0) {
                int shownTip = visibleChildrenItem(itemsTip);
                if (shownTip >= 0 && itemsTip[shownTip].children() != null) {
                    MenuItem[] all = itemsTip[shownTip].children();
                    int real = childScroll + hoverChild;
                    if (real >= 0 && real < all.length) {
                        ItemStack st = all[real].stack();
                        if (st != null && !st.isEmpty()) {
                            g.renderTooltip(this.font, st, mouseX, mouseY);
                        }
                    }
                }
            }
        }

        // 地图面板:浮在菜单组之上,独立于浮动/缩放变换(自带滑入动画)
        SAOMapPanel.render(g, mc(), this.width, this.height, globalAlpha);

        // 菜单打开期间常驻 HUD 由本 Screen 接管(平台 HUD 钩子此时跳过):
        // 血条板保持全透明度无缝衔接,圆点(上方已绘制)随开关动画淡入淡出
        SAOHud.renderPlate(g, 0, 0, SAOHud.plateW(this.width), SAOHud.plateH(this.width),
                playerName(), pp, 1f);
        if (pp != null) {
            SAOHud.renderTeamBars(g, mc(), 0, SAOHud.plateH(this.width) + 2, this.width, pp);
            SAOHud.renderEffects(g, 0, SAOHud.plateH(this.width) + 2
                    + SAOHud.teamRows(mc()) * (SAOHud.compactRowH(this.width) + 2), pp, 1f);
        }
        SAOClockPanel.render(g, mc(), this.width, this.height, globalAlpha);
        if (pp != null && pp.getMaxHealth() > 0f) {
            SAOHud.renderLowHpVignette(g, this.width, this.height, pp.getHealth() / pp.getMaxHealth());
        }
        if (confirmClose) {
            renderConfirmDialog(g, mouseX, mouseY, globalAlpha, now);
        }
        if (infoOpen) {
            renderInfoDialog(g, mouseX, mouseY, globalAlpha, now);
        }
    }



    /** Logout 确认弹窗(SAO Utils 官方 alert 窗素材,官方蓝◎/粉✕圆钮,翻转入场)。 */
    private void renderConfirmDialog(GuiGraphics g, int mouseX, int mouseY, float alpha, long now) {
        MenuLayout.Rect at = dialogRect();
        float p = clamp01((now - confirmAt) / 160f);
        float s = 0.1f + 0.9f * easeOutBack(p);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(this.width / 2f, this.height / 2f, 0);
        pose.scale(1f, s, 1f);
        pose.translate(-this.width / 2f, -this.height / 2f, 0);

        // 物品图标延迟合批先刷掉;垫近实心底防菜单内容透出(同信息弹窗)
        g.flush();
        RenderSystem.disableDepthTest();
        g.fill(at.x() + 3, at.y() + 3, at.x() + at.w() + 3, at.y() + at.h() + 3, mulAlpha(0x6E303030, alpha));
        int insX = Math.max(2, Math.round(at.w() * 0.02f));
        int insTop = Math.max(2, Math.round(at.h() * 0.045f));
        int insBot = Math.max(2, Math.round(at.h() * 0.02f));
        g.fill(at.x() + insX, at.y() + insTop, at.x() + at.w() - insX, at.y() + at.h() - insBot,
                mulAlpha(0xE6FFFFFF, alpha));
        shaderAlpha(alpha);
        blitBlended(g, TEX_ALERT, at.x(), at.y(), at.w(), at.h());
        shaderAlpha(1f);

        Font f = this.font;
        String title = tr("saomenu.logout.title");
        g.drawString(f, title, at.centerX() - f.width(title) / 2, at.y() + 10,
                mulAlpha(TEXT_DARK, alpha), false);
        String msg = tr("saomenu.logout.msg");
        g.drawString(f, msg, at.centerX() - f.width(msg) / 2,
                at.y() + Math.round(at.h() * 0.44f),
                mulAlpha(TEXT_DARK, alpha), false);

        // 官方圆钮:蓝◎确认 / 粉✕取消(悬停换亮版贴图)
        int d = 26;
        int by = at.y() + Math.round(at.h() * 0.80f) - d / 2;
        int b1x = at.x() + at.w() / 4 - d / 2;
        int b2x = at.x() + at.w() * 3 / 4 - d / 2;
        boolean h1 = inCircle(b1x + d / 2, by + d / 2, d / 2, mouseX, mouseY);
        boolean h2 = inCircle(b2x + d / 2, by + d / 2, d / 2, mouseX, mouseY);
        RenderSystem.enableBlend();
        g.blit(h1 ? TEX_BTN_OK_HOVER : TEX_BTN_OK, b1x, by, 0, 0, d, d, d, d);
        g.blit(h2 ? TEX_BTN_CANCEL_HOVER : TEX_BTN_CANCEL, b2x, by, 0, 0, d, d, d, d);
        shaderAlpha(1f);

        pose.popPose();
    }

    private boolean inCircle(int cx, int cy, int r, int x, int y) {
        return MenuLayout.inCircle(cx, cy, r, x, y);
    }

    /** 弹窗矩形(alert.png 350x253 比例,居中)。 */
    private MenuLayout.Rect dialogRect() {
        int w = Math.min(280, this.width - 20);
        int h = Math.round(w * 253f / 350f);
        return new MenuLayout.Rect((this.width - w) / 2, (this.height - h) / 2, w, h);
    }

    // ------------------------------------------------------------ 物品操作按钮

    /** 第 b 个操作按钮的矩形:缩小后水平居中排布在选中行上(参照动画)。 */
    private MenuLayout.Rect actionButtonRect(MenuLayout.Rect row, int b) {
        int d = Math.max(8, Math.round(row.h() * 0.85f));
        int cx = row.centerX() + (b - 1) * Math.round(d * 1.18f);
        int cy = row.centerY();
        return new MenuLayout.Rect(cx - d / 2, cy - d / 2, d, d);
    }

    /** 执行物品操作:0=装备(服务端换位) 1=信息弹窗 2=丢弃(服务端掉落)。 */
    private void executeItemAction(int b, MenuItem target) {
        actionMenuOpen = false;
        if (b == 0) {
            new com.sao.saomenu.party.EquipItemC2S(target.invSlot()).sendToServer();
            playClick();
        } else if (b == 1) {
            infoStack = target.stack().copy();
            infoOpen = true;
            infoAt = now();
            playAlert();
        } else {
            new com.sao.saomenu.party.DropItemC2S(target.invSlot(), true).sendToServer();
            playClick();
        }
    }

    // ------------------------------------------------------------ 物品信息弹窗

    /** 物品信息弹窗(参照动画图三:alert 白卡 + 属性行 + 官方蓝◎/粉✕圆钮)。 */
    private void renderInfoDialog(GuiGraphics g, int mouseX, int mouseY, float alpha, long now) {
        ItemStack st = infoStack;
        if (st == null || st.isEmpty()) {
            infoOpen = false;
            return;
        }
        MenuLayout.Rect at = dialogRect();
        float p = clamp01((now - infoAt) / 160f);
        float s = 0.1f + 0.9f * easeOutBack(p);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(this.width / 2f, this.height / 2f, 0);
        pose.scale(1f, s, 1f);
        pose.translate(-this.width / 2f, -this.height / 2f, 0);

        // 物品图标延迟合批,先刷掉;alert 面板原设计 77% 玻璃感,
        // 底下垫一层近实心白,菜单内容才不会透出弹窗
        g.flush();
        RenderSystem.disableDepthTest();
        g.fill(at.x() + 3, at.y() + 3, at.x() + at.w() + 3, at.y() + at.h() + 3, mulAlpha(0x6E303030, alpha));
        int insX = Math.max(2, Math.round(at.w() * 0.02f));
        int insTop = Math.max(2, Math.round(at.h() * 0.045f));
        int insBot = Math.max(2, Math.round(at.h() * 0.02f));
        g.fill(at.x() + insX, at.y() + insTop, at.x() + at.w() - insX, at.y() + at.h() - insBot,
                mulAlpha(0xE6FFFFFF, alpha));
        shaderAlpha(alpha);
        blitBlended(g, TEX_ALERT, at.x(), at.y(), at.w(), at.h());
        shaderAlpha(1f);

        Font f = this.font;
        // 标题 = 物品名(稀有度颜色)
        String title = st.getHoverName().getString();
        g.drawString(f, title, at.centerX() - f.width(title) / 2, at.y() + 8,
                mulAlpha(st.getRarity().color.getColor(), alpha), false);

        // 属性行(灰色带内居中排列)
        List<String> lines = buildInfoLines(st);
        int ly = at.y() + Math.round(at.h() * 0.22f);
        int maxLines = 7;
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            g.drawString(f, lines.get(i), at.centerX() - f.width(lines.get(i)) / 2,
                    ly + i * 11, mulAlpha(TEXT_DARK, alpha), false);
        }

        // 单个确认圆钮:底部中央,点击任意处只关闭弹窗(菜单保持打开)
        int d = 26;
        int bx = at.centerX() - d / 2;
        int by = at.y() + Math.round(at.h() * 0.82f) - d / 2;
        boolean hv = MenuLayout.inCircle(at.centerX(), by + d / 2, d / 2, mouseX, mouseY);
        RenderSystem.enableBlend();
        g.blit(hv ? TEX_BTN_OK_HOVER : TEX_BTN_OK, bx, by, 0, 0, d, d, d, d);

        pose.popPose();
    }

    /** 物品属性行:类型/数量/耐久/附魔/ID。 */
    private List<String> buildInfoLines(ItemStack st) {
        List<String> lines = new ArrayList<>();
        net.minecraft.world.item.Item item = st.getItem();
        String type;
        if (item instanceof net.minecraft.world.item.ArmorItem) {
            type = tr("saomenu.info.armor");
        } else if (item instanceof net.minecraft.world.item.SwordItem
                || item instanceof net.minecraft.world.item.ProjectileWeaponItem) {
            type = tr("saomenu.info.weapon");
        } else if (item instanceof net.minecraft.world.item.TieredItem
                || item instanceof net.minecraft.world.item.DiggerItem) {
            type = tr("saomenu.info.tool");
        } else {
            type = tr("saomenu.info.item");
        }
        lines.add(tr("saomenu.info.type") + ": " + type);
        lines.add(tr("saomenu.info.count") + ": " + st.getCount());
        if (st.isDamageableItem()) {
            lines.add(tr("saomenu.info.durability") + ": "
                    + (st.getMaxDamage() - st.getDamageValue()) + " / " + st.getMaxDamage());
        }
        var ench = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(st);
        int shown = 0;
        for (var e : ench.entrySet()) {
            if (shown >= 4) {
                lines.add("… +" + (ench.size() - shown));
                break;
            }
            lines.add(tr("saomenu.info.enchant") + ": " + e.getKey().getFullname(e.getValue()).getString());
            shown++;
        }
        lines.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
        return lines;
    }

    private void updateHovers(int mouseX, int mouseY) {
        if (confirmClose) {
            return;
        }
        // 菜单组有浮动/缩放变换,先逆变换回菜单本地坐标再做命中
        int lx = localX(mouseX);
        int ly = localY(mouseY);
        hoverMain = MenuLayout.hoveredMainButtonAt(this.width, this.height, baseAnchorX, baseAnchorY, lx, ly);
        hoverItem = -1;
        hoverChild = -1;
        hoverEquip = -1;
        hoverAction = -1;
        int main = activeMain();
        if (main < 0) {
            return;
        }
        MenuItem[] items = activeItems(main);
        int anchorY = buttonY(main);
        // 操作按钮打开时:独占命中(只悬停三圆钮)
        if (actionMenuOpen) {
            MenuItem[] winA = shownChildren(items);
            if (winA != null && actionRow >= 0 && actionRow < winA.length) {
                int shownA = visibleChildrenItem(items);
                int anchorA = shownA >= 0 ? MenuLayout.menuItemRectAt(this.width, this.height,
                        items.length, baseAnchorX, anchorY, shownA).centerY() : anchorY;
                MenuLayout.Rect rowA = MenuLayout.childItemRectAt(this.width, this.height,
                        winA.length, baseAnchorX, anchorA, actionRow);
                for (int b = 0; b < 3; b++) {
                    if (actionButtonRect(rowA, b).contains(lx, ly)) {
                        hoverAction = b;
                        break;
                    }
                }
            }
            return;
        }
        for (int i = 0; i < items.length; i++) {
            if (MenuLayout.menuItemRectAt(this.width, this.height, items.length, baseAnchorX, anchorY, i).contains(lx, ly)) {
                hoverItem = i;
            }
        }
        int shown = visibleChildrenItem(items);
        if (shown < 0 || items[shown].children() == null) {
            return;
        }
        MenuItem[] children = windowedChildren(items[shown].children());
        int childAnchor = MenuLayout.menuItemRectAt(this.width, this.height, items.length, baseAnchorX, anchorY, shown).centerY();
        for (int i = 0; i < children.length; i++) {
            if (MenuLayout.childItemRectAt(this.width, this.height, children.length, baseAnchorX, childAnchor, i)
                    .contains(lx, ly)) {
                hoverChild = i;
            }
        }
        int target = equipTargetIndex(items, shown);
        if (target < 0) {
            return;
        }
        List<EquipEntry> entries = equipEntries(EQUIP_KINDS[target]);
        int equipAnchor = equipAnchorY(items, shown, target);
        for (int i = 0; i < entries.size(); i++) {
            if (MenuLayout.equipItemRectAt(this.width, this.height, entries.size(), baseAnchorX, equipAnchor, i)
                    .contains(lx, ly)) {
                hoverEquip = i;
            }
        }
    }

    /**
     * 一级项里当前应展开子项列的那个:只看点击展开项(expandedItem)。
     * 悬停不再自动展开——参考 SAO-World,子菜单必须点一下才打开。
     */
    private int visibleChildrenItem(MenuItem[] items) {
        if (expandedItem >= 0 && expandedItem < items.length && items[expandedItem].children() != null) {
            return expandedItem;
        }
        return -1;
    }

    /** 当前展开二级列的窗口化 children;未展开返回 null(渲染与命中共用同一几何)。 */
    private MenuItem[] shownChildren(MenuItem[] items) {
        int shown = visibleChildrenItem(items);
        if (shown < 0 || shown >= items.length || items[shown].children() == null) {
            return null;
        }
        return windowedChildren(items[shown].children());
    }

    /** 当前应展示装备列的二级子项下标;不展示返回 -1。物品条目列不触发装备列。 */
    private int equipTargetIndex(MenuItem[] items, int shown) {
        if (shown < 0 || shown >= items.length || items[shown].children() == null) {
            return -1;
        }
        MenuItem[] children = items[shown].children();
        // 物品条目列(条目带 stack)不关联装备展示
        if (children.length > 0 && children[0].stack() != null) {
            return -1;
        }
        // 只看点击选中的子项:悬停不再自动展开(参照 SAO-World,点一下才打开)
        int target = equipOwner;
        if (target < 0 || target >= children.length) {
            return -1;
        }
        return children[target].action() == Action.SHOW_EQUIP ? target : -1;
    }

    /** 装备列锚点 Y:对齐目标二级子项行的纵向中心。 */
    private int equipAnchorY(MenuItem[] items, int shown, int target) {
        int childAnchor = MenuLayout.menuItemRectAt(this.width, this.height, items.length,
                baseAnchorX, buttonY(shown), shown).centerY();
        return MenuLayout.childItemRectAt(this.width, this.height, items[shown].children().length,
                baseAnchorX, childAnchor, target).centerY();
    }

    /** 收集某分类下已装备的物品条目;全空时返回一条「暂无装备」占位。 */
    private List<EquipEntry> equipEntries(EquipKind kind) {
        List<EquipEntry> list = new ArrayList<>();
        Player p = mc().player;
        if (p != null) {
            switch (kind) {
                case WEAPON -> {
                    if (!p.getMainHandItem().isEmpty()) {
                        list.add(new EquipEntry(p.getMainHandItem(), false));
                    }
                }
                case ARMOR -> {
                    // 头 → 胸 → 腿 → 脚
                    for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                        if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) {
                            continue;
                        }
                        ItemStack s = p.getItemBySlot(slot);
                        if (!s.isEmpty()) {
                            list.add(new EquipEntry(s, false));
                        }
                    }
                }
                case TRINKET -> {
                    if (!p.getOffhandItem().isEmpty()) {
                        list.add(new EquipEntry(p.getOffhandItem(), false));
                    }
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new EquipEntry(ItemStack.EMPTY, true));
        }
        return list;
    }

    /** 屏幕坐标 -> 菜单本地坐标(逆用渲染时的浮动/缩放/左移/漂移变换)。 */
    private int localX(int mx) {
        return Math.round(menuAnchorX + shiftXs + (mx - menuAnchorX - followPxX) / menuScale);
    }

    /** 菜单本地坐标 -> 屏幕视觉 X(变换正变换;剪裁框等屏幕图元用)。 */
    private int visualX(int localXPos) {
        return Math.round(menuAnchorX + (localXPos - menuAnchorX - shiftXs) * menuScale + followPxX);
    }

    /** 菜单本地坐标 -> 屏幕视觉 Y。 */
    private int visualY(int localYPos) {
        return Math.round(menuAnchorY + (localYPos - menuAnchorY) * menuScale + followPxY);
    }

    private int localY(int my) {
        return Math.round(menuAnchorY + (my - menuAnchorY) / menuScale);
    }

    private void shaderAlpha(float a) {
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

    // ---------------------------------------------------------------- 主按钮

    /** 主按钮堆叠展开:单按钮时长(ms)。 */
    private static final long UNFOLD_MS = 240;
    /** 相邻按钮的展开错峰间隔(ms)。 */
    private static final long UNFOLD_STAGGER_MS = 45;

    private void renderMainButtons(GuiGraphics g, float globalAlpha) {
        // 堆叠向下展开(参照 SAO-World):打开时所有按钮叠在首按钮位,
        // 随后逐个错峰向下滑到自己的位置,带 easeOutBack 回弹
        int stackY = buttonY(0);
        long unfoldNow = now();
        for (int i = 0; i < MenuLayout.BTN_COUNT; i++) {
            boolean active = isActive(i);
            int d = MenuLayout.btnSize(this.height);
            int cx = baseAnchorX;
            float p = closing ? 1f
                    : clamp01((unfoldNow - openedAt - i * UNFOLD_STAGGER_MS) / (float) UNFOLD_MS);
            float eased = easeOutBack(p);
            int cy = Math.round(stackY + (buttonY(i) - stackY) * eased);
            // SAO Utils 官方按钮素材:常态白圆,悬停/选中橙圆,按压用 press 帧反馈点触
            // 未点击过任何主按钮前列内全部全亮;点击后其余按钮压到 45% 隐约可见
            boolean dim = mainTouched && !active;
            float a = globalAlpha * (dim ? 0.45f : 1f);
            ResourceLocation btnTex = mainPressing(i) ? TEX_BTN_PRESS
                    : active ? TEX_BTN_HOVER : TEX_BTN_NORMAL;
            shaderAlpha(a);
            RenderSystem.enableBlend();
            g.blit(btnTex, cx - d / 2, cy - d / 2, 0, 0, d, d, d, d);
            shaderAlpha(1f);
            // SAO Utils 官方符号图标(46x46):常态深色版,悬停/选中反白版
            ResourceLocation glyph = tex("symbol_" + MAIN_BUTTONS[i].icon()
                    + (active ? "_hover" : "_normal") + ".png");
            int pad = Math.max(2, Math.round(d * 0.22f));
            int isz = d - pad * 2;
            shaderAlpha(a);
            RenderSystem.enableBlend();
            g.blit(glyph, cx - d / 2 + pad, cy - d / 2 + pad, 0, 0, isz, isz, isz, isz);
            shaderAlpha(1f);
        }
    }

    // ---------------------------------------------------------------- 面板(卡片)

    private void renderPanelFor(GuiGraphics g, int main, int mouseX, int mouseY,
                                float globalAlpha, long now) {
        int anchorY = buttonY(main);
        float p = panelAt == Long.MIN_VALUE ? 1f
                : clamp01((now - panelAt) / (float) PANEL_MS);
        float eased = easeOutCubic(p);
        float alpha = globalAlpha * p;

        switch (MAIN_BUTTONS[main].kind()) {
            case PROFILE -> renderPlayerCard(g, anchorY, mouseX, mouseY, eased, alpha);
            case PARTY -> renderTeamCard(g, anchorY, eased, alpha);
            case FRIENDS -> renderFriendsCard(g, anchorY, eased, alpha);
            case SETTINGS -> { /* 设置无左侧卡 */ }
        }
    }

    private void renderPlayerCard(GuiGraphics g, int anchorY, int mouseX, int mouseY, float eased, float alpha) {
        MenuLayout.Rect rect = MenuLayout.cardRectAt(this.width, this.height, baseAnchorX, anchorY);
        int slide = Math.round((1f - eased) * rect.w() * 0.35f);
        MenuLayout.Rect at = new MenuLayout.Rect(rect.x() + slide, rect.y(), rect.w(), rect.h());

        // 主体:属性区按文字行数预留(大卡 8 行含饥饿/护甲,有手持物品时 9 行),剩余全部给 3D 头像
        ItemStack held = mc().player != null ? mc().player.getInventory().getSelected() : ItemStack.EMPTY;
        boolean hasHeld = !held.isEmpty();
        int statLines = at.h() >= 140 ? 8 + (hasHeld ? 1 : 0) : 6;
        int split = Math.max(Math.round(at.h() * 0.40f), at.h() - (statLines * 10 + 6));
        // SAO Utils 官方玩家卡面板贴图(上半白、下半浅灰属性区)
        shaderAlpha(alpha);
        blitBlended(g, TEX_PANEL, at.x(), at.y(), at.w(), at.h());
        shaderAlpha(1f);

        // 名字 + 下划线(头部区尽量紧凑,把空间让给剪影)
        Font f = this.font;
        String name = playerName();
        int nameY = at.y() + 3;
        g.drawString(f, name, at.centerX() - f.width(name) / 2, nameY,
                mulAlpha(TEXT_DARK, alpha), false);
        int lineY = nameY + 10;
        g.fill(at.x() + at.w() / 10, lineY, at.x() + at.w() - at.w() / 10, lineY + 1, mulAlpha(CARD_LINE, alpha));

        // 手持物品图标(卡片左上角,SAO 槽位样式)
        if (hasHeld) {
            int isz = 14;
            int ix = at.x() + 8;
            int iy = at.y() + 5;
            g.fill(ix, iy, ix + isz, iy + isz, mulAlpha(0x52F9F9F9, alpha));
            g.fill(ix, iy, ix + isz, iy + 1, mulAlpha(SAOConfig.accent(), alpha));
            g.fill(ix, iy + isz - 1, ix + isz, iy + isz, mulAlpha(SAOConfig.accent(), alpha));
            g.fill(ix, iy, ix + 1, iy + isz, mulAlpha(SAOConfig.accent(), alpha));
            g.fill(ix + isz - 1, iy, ix + isz, iy + isz, mulAlpha(SAOConfig.accent(), alpha));
            g.pose().pushPose();
            g.pose().translate(ix + isz / 2f, iy + isz / 2f, 120f);
            g.pose().scale(isz / 16f, isz / 16f, 1f);
            g.renderItem(held, -8, -8);
            g.pose().popPose();
        }

        // 头像区:占满头部线与属性区之间(split 为卡片内相对高度)
        int areaTop = lineY + 2;
        int areaH = Math.max(8, split - (areaTop - at.y()) - 2);
        if (mc().player != null) {
            // 3D 玩家(与原版背包同源渲染),朝向跟随鼠标转动。
            // 原版语义:(x,y)=脚部锚点,size=缩放系数,体高≈1.9*size;裁剪由调用者负责。
            int k = Math.max(6, Math.round(areaH / 1.95f));
            int anchorX = at.centerX();
            int feetY = areaTop + areaH - 2;
            int halfW = Math.round(k * 0.8f);
            // 原版 FollowsMouse 传的是"锚点 - 光标"增量,内部 atan(delta/40)*20° 转向
            float dx = anchorX - mouseX;
            float dy = feetY - mouseY;
            // 剪裁框必须跟随视觉变换(缩放/左移/浮动):菜单整组左移后,
            // 3D 人物画在新位置,旧坐标的剪裁框会把人物整个裁掉
            g.enableScissor(visualX(anchorX - halfW), visualY(areaTop),
                    visualX(anchorX + halfW), visualY(areaTop + areaH));
            shaderAlpha(alpha);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, anchorX, feetY, k, dx, dy, mc().player);
            shaderAlpha(1f);
            g.disableScissor();
        } else {
            // 无玩家(标题界面等)回退全身剪影
            int sh = areaH;
            int sw = Math.max(6, Math.round(sh * (64f / 96f)));
            shaderAlpha(alpha);
            RenderSystem.enableBlend();
            g.blit(TEX_SILHOUETTE, at.centerX() - sw / 2, areaTop, 0, 0, sw, sh, sw, sh);
            shaderAlpha(1f);
        }

        // 属性
        Player p = mc().player;
        if (p != null) {
            int statsTop = at.y() + split;
            int lineStep = 10;
            List<String> stats = new ArrayList<>();
            if (hasHeld) {
                stats.add(tr("saomenu.stat.held", held.getHoverName().getString()));
            }
            stats.add(tr("saomenu.stat.level", p.experienceLevel));
            stats.add(tr("saomenu.stat.experience", Math.round(p.experienceProgress * 100.0f)));
            stats.add(tr("saomenu.stat.health", trim(p.getHealth()), trim(p.getMaxHealth())));
            if (statLines >= 8) {
                stats.add(tr("saomenu.stat.hunger", p.getFoodData().getFoodLevel()));
                stats.add(tr("saomenu.stat.armor", p.getArmorValue()));
            }
            stats.add(tr("saomenu.stat.strength", trim((float) p.getAttributeValue(Attributes.ATTACK_DAMAGE))));
            stats.add(tr("saomenu.stat.agility", trim((float) p.getAttributeValue(Attributes.MOVEMENT_SPEED))));
            stats.add(tr("saomenu.stat.resistance", trim((float) p.getAttributeValue(Attributes.ARMOR))));
            for (int i = 0; i < stats.size(); i++) {
                g.drawString(f, stats.get(i), at.x() + 8, statsTop + 4 + i * lineStep,
                        mulAlpha(TEXT_DARK, alpha), false);
            }
        }

        // ▶ 指向按钮
        renderArrowRight(g, at, anchorY, alpha);
    }

    /** 队伍面板:scoreboard 队伍名 + 成员列表;无队伍时显示提示。 */
    private void renderTeamCard(GuiGraphics g, int anchorY, float eased, float alpha) {
        List<String> rows = new ArrayList<>();
        String title = tr("saomenu.party");
        String subtitle = null;
        String footer = tr("saomenu.panel.team_members", 0);
        Player p = mc().player;
        if (p != null && mc().level != null) {
            PlayerTeam team = mc().level.getScoreboard().getPlayersTeam(p.getGameProfile().getName());
            if (team != null) {
                title = team.getDisplayName().getString();
                List<String> members = new ArrayList<>(team.getPlayers());
                members.sort(String::compareToIgnoreCase);
                rows.addAll(members);
                footer = tr("saomenu.panel.team_members", members.size());
            } else {
                subtitle = tr("saomenu.panel.no_team");
            }
        }
        renderListCard(g, title, subtitle, rows, footer, anchorY, eased, alpha);
    }

    /** 好友面板:Tab 在线玩家列表。 */
    private void renderFriendsCard(GuiGraphics g, int anchorY, float eased, float alpha) {
        List<String> rows = new ArrayList<>();
        int online = 0;
        if (mc().getConnection() != null) {
            List<String> names = new ArrayList<>();
            for (PlayerInfo info : mc().getConnection().getOnlinePlayers()) {
                names.add(info.getProfile().getName());
            }
            names.sort(String::compareToIgnoreCase);
            online = names.size();
            rows.addAll(names);
        }
        renderListCard(g, tr("saomenu.friends"), null, rows,
                tr("saomenu.panel.online", online), anchorY, eased, alpha);
    }

    /** 通用列表卡:标题/副标题 + 最多 maxRows 行 + 底部统计。 */
    private void renderListCard(GuiGraphics g, String title, String subtitle,
                                List<String> rows, String footer, int anchorY, float eased, float alpha) {
        MenuLayout.Rect rect = MenuLayout.cardRectAt(this.width, this.height, baseAnchorX, anchorY);
        int slide = Math.round((1f - eased) * rect.w() * 0.35f);
        MenuLayout.Rect at = new MenuLayout.Rect(rect.x() + slide, rect.y(), rect.w(), rect.h());

        shaderAlpha(alpha);
        blitBlended(g, TEX_PANEL, at.x(), at.y(), at.w(), at.h());
        shaderAlpha(1f);

        Font f = this.font;
        g.drawString(f, title, at.centerX() - f.width(title) / 2,
                at.y() + 5, mulAlpha(TEXT_DARK, alpha), false);
        int lineY;
        if (subtitle != null && !subtitle.isEmpty()) {
            g.drawString(f, subtitle, at.centerX() - f.width(subtitle) / 2,
                    at.y() + 17, mulAlpha(TEXT_DARK, alpha), false);
            lineY = at.y() + 30;
        } else {
            lineY = at.y() + 18;
        }
        g.fill(at.x() + at.w() / 10, lineY, at.x() + at.w() - at.w() / 10, lineY + 1, mulAlpha(CARD_LINE, alpha));

        // 行数按卡片实际高度自适应,超出部分折叠为 "+N 更多"
        int maxRows = Mth.clamp((at.h() - 56) / 12, 1, 8);
        if (rows.size() > maxRows) {
            int extra = rows.size() - maxRows + 1;
            List<String> shown = new ArrayList<>(rows.subList(0, Math.max(0, maxRows - 1)));
            shown.add(tr("saomenu.panel.more", extra));
            rows = shown;
        }
        int rowY = lineY + 6;
        for (int i = 0; i < rows.size(); i++) {
            g.drawString(f, rows.get(i), at.x() + 12, rowY + i * 12,
                    mulAlpha(TEXT_DARK, alpha), false);
        }
        if (rows.isEmpty() && (subtitle == null || subtitle.isEmpty())) {
            g.drawString(f, tr("saomenu.panel.no_players"), at.x() + 12, rowY,
                    mulAlpha(TEXT_DARK, alpha), false);
        }

        g.drawString(f, footer, at.x() + at.w() - 12 - f.width(footer),
                at.y() + at.h() - 13, mulAlpha(TEXT_DARK, alpha), false);
        renderArrowRight(g, at, anchorY, alpha);
    }

    /** 卡片右侧指向按钮的 ▶,横跨两者之间的间隙。 */
    private void renderArrowRight(GuiGraphics g, MenuLayout.Rect card, int anchorY, float alpha) {
        int btnLeft = baseAnchorX - MenuLayout.btnSize(this.height) / 2;
        int x0 = card.x() + card.w() + 1;
        int w = btnLeft - x0 - 1;
        if (w < 3) {
            return;
        }
        int h = Math.max(6, Math.round(w * (19f / 24f)));
        shaderAlpha(alpha);
        RenderSystem.enableBlend();
        g.blit(TEX_ARROW_RIGHT, x0, anchorY - h / 2, 0, 0, w, h, w, h);
        shaderAlpha(1f);
    }

    // ---------------------------------------------------------------- 菜单项

    private void renderMenuItems(GuiGraphics g, int main, int mouseX, int mouseY,
                                 float globalAlpha, long now) {
        MenuItem[] items = activeItems(main);
        int anchorY = buttonY(main);
        long base = panelAt == Long.MIN_VALUE ? now - PANEL_MS : panelAt;

        // SAO Utils 官方指示器:列左缘长箭头,中段菱形对准活动行
        renderIndicator(g, items.length, anchorY, baseAnchorX, globalAlpha, true);

        for (int i = 0; i < items.length; i++) {
            float p = clamp01((now - base - i * ITEM_STAGGER_MS) / (float) ITEM_MS);
            if (p <= 0f) {
                continue;
            }
            float eased = easeOutCubic(p);
            MenuLayout.Rect rect = MenuLayout.menuItemRectAt(this.width, this.height, items.length, baseAnchorX, anchorY, i);
            int slide = Math.round((1f - eased) * rect.w() * 0.45f);
            MenuLayout.Rect at = new MenuLayout.Rect(rect.x() - slide, rect.y(), rect.w(), rect.h());
            // 与主按钮同一规则:本列已点击过(expandedItem 生效)后,非当前项压暗
            boolean dim = expandedItem != -1 && expandedItem != i;
            renderMenuItem(g, at, items[i].label(), items[i].icon(),
                    hoverItem == i, false, globalAlpha * eased * (dim ? 0.45f : 1f),
                    itemPressing(0, i), items[i].stack());
        }

        int shown = visibleChildrenItem(items);
        if (shown >= 0 && items[shown].children() != null) {
            if (childOwner != shown) {
                childOwner = shown;
                childAt = now;
            }
            MenuItem[] children = windowedChildren(items[shown].children());
            int childAnchor = MenuLayout.menuItemRectAt(this.width, this.height, items.length, baseAnchorX, anchorY, shown).centerY();
            // 二级列不再画指示器(其定位公式落在一级列位置,与原指示器重叠成双线);
            // 只保留主按钮旁那条原始指示器
            // 二级列同样:点选某个子项(equipOwner)后,其余子项压暗
            boolean childDim = equipOwner != -1;
            for (int i = 0; i < children.length; i++) {
                float p = clamp01((now - childAt - i * ITEM_STAGGER_MS) / (float) ITEM_MS);
                if (p <= 0f) {
                    continue;
                }
                float eased = easeOutCubic(p);
                MenuLayout.Rect rect = MenuLayout.childItemRectAt(this.width, this.height, children.length, baseAnchorX, childAnchor, i);
                int slide = Math.round((1f - eased) * rect.w() * 0.45f);
                MenuLayout.Rect at = new MenuLayout.Rect(rect.x() - slide, rect.y(), rect.w(), rect.h());
                boolean dim = childDim && equipOwner != i;
                renderMenuItem(g, at, children[i].label(), children[i].icon(),
                        hoverChild == i || (actionMenuOpen && actionRow == i), true,
                        globalAlpha * eased * (dim ? 0.45f : 1f),
                        itemPressing(1, i), children[i].stack());
            }
            // 物品操作按钮:缩小后水平排布在选中行上(级联弹出 + 悬停标签)
            if (actionMenuOpen && actionRow >= 0 && actionRow < children.length
                    && children[actionRow].stack() != null) {
                MenuLayout.Rect rowA = MenuLayout.childItemRectAt(this.width, this.height,
                        children.length, baseAnchorX, childAnchor, actionRow);
                // 物品图标是延迟合批的,先刷掉;物品渲染还会往深度缓冲写 z=150 的深度,
                // 之后 blit 继承"深度测试开启"状态会被图标深度挡住(表现为图标盖在按钮上),
                // 所以 flush 后必须关掉深度测试
                g.flush();
                RenderSystem.disableDepthTest();
                long age = now - actionAt;
                for (int b = 0; b < 3; b++) {
                    float bp = clamp01((age - b * 45) / 150f);
                    float bs = 0.2f + 0.8f * easeOutBack(bp);
                    MenuLayout.Rect full = actionButtonRect(rowA, b);
                    int ds = Math.max(2, Math.round(full.w() * bs));
                    boolean hv = hoverAction == b;
                    ResourceLocation t = b == 0 ? (hv ? TEX_ACT_EQUIP_H : TEX_ACT_EQUIP)
                            : b == 1 ? (hv ? TEX_ACT_INFO_H : TEX_ACT_INFO)
                            : (hv ? TEX_ACT_DROP_H : TEX_ACT_DROP);
                    shaderAlpha(globalAlpha);
                    RenderSystem.enableBlend();
                    g.blit(t, full.centerX() - ds / 2, full.centerY() - ds / 2, 0, 0, ds, ds, ds, ds);
                    shaderAlpha(1f);
                    if (hv) {
                        String lbl = tr(ACT_KEYS[b]);
                        g.drawString(this.font, lbl, full.centerX() - this.font.width(lbl) / 2,
                                full.y() - 11, mulAlpha(0xFFFFFFFF, globalAlpha), true);
                    }
                }
            }
            // 装备第三列:参考 SAO-World,武器/护甲/首饰展开后右侧直接列出已装备物品
            int equipTarget = equipTargetIndex(items, shown);
            if (equipTarget >= 0) {
                if (equipTarget != equipShownOwner) {
                    equipShownOwner = equipTarget;
                    equipAt = now;
                }
                renderEquipColumn(g, EQUIP_KINDS[equipTarget],
                        equipAnchorY(items, shown, equipTarget), globalAlpha, now);
            } else {
                equipShownOwner = -1;
            }
        } else {
            if (childOwner != -1) {
                childOwner = -1;
            }
            equipShownOwner = -1;
        }
    }

    /** 装备条目列(第三列):每个已装备物品一行,白底条目 + 物品图标 + 名称。 */
    private void renderEquipColumn(GuiGraphics g, EquipKind kind, int anchorY, float globalAlpha, long now) {
        List<EquipEntry> entries = equipEntries(kind);
        int count = entries.size();
        for (int i = 0; i < count; i++) {
            float p = clamp01((now - equipAt - i * ITEM_STAGGER_MS) / (float) ITEM_MS);
            if (p <= 0f) {
                continue;
            }
            float eased = easeOutCubic(p);
            MenuLayout.Rect rect = MenuLayout.equipItemRectAt(this.width, this.height, count, baseAnchorX, anchorY, i);
            int slide = Math.round((1f - eased) * rect.w() * 0.45f);
            MenuLayout.Rect at = new MenuLayout.Rect(rect.x() + slide, rect.y(), rect.w(), rect.h());
            renderEquipItem(g, at, entries.get(i), hoverEquip == i, globalAlpha * eased);
        }
    }

    /** 单个装备条目:SAO Utils 条目贴图 + 物品图标(3D)+ 名称;空条目为灰色占位。 */
    private void renderEquipItem(GuiGraphics g, MenuLayout.Rect at, EquipEntry e, boolean hovered, float alpha) {
        fillRounded(g, at.x() + 2, at.y() + 2, at.w(), at.h(),
                Math.max(2, Math.round(at.h() * 0.12f)), mulAlpha(SHADOW, alpha));
        if (hovered) {
            setTint(SAOConfig.accent(), alpha);
            blitBlended(g, TEX_LIST_HOVER, at.x(), at.y(), at.w(), at.h());
        } else {
            RenderSystem.enableBlend();
            shaderAlpha(alpha * 0.92f);
            g.blit(TEX_LIST_NORMAL, at.x(), at.y(), 0, 0, at.w(), at.h(), at.w(), at.h());
        }
        shaderAlpha(1f);

        int iconSize = Math.round(at.h() * 0.78f);
        int iconX = at.x() + Math.round(at.h() * 0.18f);
        int iconY = at.y() + (at.h() - iconSize) / 2;
        if (!e.empty()) {
            g.pose().pushPose();
            g.pose().translate(iconX + iconSize / 2f, iconY + iconSize / 2f, 120f);
            g.pose().scale(iconSize / 16f, iconSize / 16f, 1f);
            g.renderItem(e.stack(), -8, -8);
            g.pose().popPose();
        }

        Font f = this.font;
        String label = e.empty() ? tr("saomenu.equip.empty") : e.stack().getHoverName().getString();
        int textX = iconX + iconSize + Math.round(at.h() * 0.18f);
        int maxW = at.x() + at.w() - textX - 6;
        if (f.width(label) > maxW) {
            label = f.plainSubstrByWidth(label, Math.max(0, maxW - f.width("…"))) + "…";
        }
        int textY = at.y() + (at.h() - f.lineHeight) / 2;
        g.drawString(f, label, textX, textY,
                e.empty() ? mulAlpha(0xFF9A9DA0, alpha)
                        : hovered ? mulAlpha(TEXT_ON_ORANGE, alpha) : mulAlpha(TEXT_DARK, alpha),
                false);
    }

    /**
     * 菜单列指示器(SAO Utils 官方素材):列左缘双头长箭头,中段菱形对准活动行;
     * 一级列在按钮边缘保留接头小环。
     */
    private void renderIndicator(GuiGraphics g, int count, int anchorY, int anchorX, float alpha, boolean mainColumn) {
        int itemH = MenuLayout.itemH(this.height);
        int step = itemH + MenuLayout.itemGap(this.height);
        int totalH = (count - 1) * step + itemH;
        int top = MenuLayout.clampedAnchorY(this.height, count, anchorY) - totalH / 2;
        int colX = MenuLayout.itemColumnXAt(anchorX, this.height);
        int indH = totalH + Math.max(8, itemH * 2);
        int indW = Math.max(6, Math.round(indH * 28f / 230f));
        int x = colX - MenuLayout.arrowGap(this.height) / 2 - indW / 2 - 1;
        int y = top - (indH - totalH) / 2;
        shaderAlpha(alpha);
        RenderSystem.enableBlend();
        g.blit(TEX_INDICATOR, x, y, 0, 0, indW, indH, indW, indH);
        shaderAlpha(1f);
        if (mainColumn) {
            int btnRight = anchorX + MenuLayout.btnSize(this.height) / 2;
            int ringD = Math.max(4, Math.round(itemH * 0.30f));
            shaderAlpha(alpha);
            RenderSystem.enableBlend();
            g.blit(TEX_RING, btnRight + 1, anchorY - ringD / 2, 0, 0, ringD, ringD, ringD, ringD);
            shaderAlpha(1f);
        }
    }

    /** 单个菜单项:SAO Utils 官方条目素材(白/橙圆角条)+ 阴影 + 图标 + 文字;press 为按压帧。 */
    private void renderMenuItem(GuiGraphics g, MenuLayout.Rect at, String labelKey, String icon,
                                boolean hovered, boolean child, float alpha, boolean pressed, ItemStack stack) {
        int r = Math.max(2, Math.round(at.h() * 0.12f));
        fillRounded(g, at.x() + 2, at.y() + 2, at.w(), at.h(), r, mulAlpha(SHADOW, alpha));
        if (pressed) {
            blitBlended(g, TEX_LIST_PRESS, at.x(), at.y(), at.w(), at.h());
        } else if (hovered) {
            setTint(SAOConfig.accent(), alpha);
            blitBlended(g, TEX_LIST_HOVER, at.x(), at.y(), at.w(), at.h());
        } else {
            RenderSystem.enableBlend();
            shaderAlpha(alpha * 0.9f);
            g.blit(TEX_LIST_NORMAL, at.x(), at.y(), 0, 0, at.w(), at.h(), at.w(), at.h());
        }
        shaderAlpha(1f);

        // 图标:物品条目画 3D 物品,普通条目画贴图符号
        // 弹窗打开时跳过图标渲染——图标是延迟合批的,某些优化 mod 会推迟到帧末刷新,
        // 弹窗盖不住它们;而弹窗本来就遮住这些行,图标不渲染也无视觉损失
        int iconSize = Math.round(at.h() * 0.72f);
        int iconY = at.y() + (at.h() - iconSize) / 2;
        int iconX = at.x() + Math.round(at.h() * 0.18f);
        if (stack != null && !stack.isEmpty()) {
            if (!infoOpen && !confirmClose) {
                g.pose().pushPose();
                g.pose().translate(iconX + iconSize / 2f, iconY + iconSize / 2f, 120f);
                g.pose().scale(iconSize / 16f, iconSize / 16f, 1f);
                g.renderItem(stack, -8, -8);
                g.pose().popPose();
            }
        } else {
            shaderAlpha(alpha);
            RenderSystem.enableBlend();
            g.blit(tex(icon + ".png"), iconX, iconY,
                    0, 0, iconSize, iconSize, iconSize, iconSize);
            shaderAlpha(1f);
        }

        // 文字(SAOUI 字体;动态 label(在线玩家名/语言键)二选一)
        Font f = this.font;
        String label = resolveLabel(labelKey);
        int textX = at.x() + Math.round(at.h() * 0.18f) + iconSize + Math.round(at.h() * 0.22f);
        int textY = at.y() + (at.h() - f.lineHeight) / 2;
        g.drawString(f, label, textX, textY,
                hovered ? mulAlpha(TEXT_ON_ORANGE, alpha) : mulAlpha(TEXT_DARK, alpha), false);
    }

    /** 圆角矩形填充:两条直条相交,四角各留 r×r 缺口(背景透出即圆角)。 */
    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    // ---------------------------------------------------------------- 输入

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // 悬停命中由 render() 每帧统一处理(事件与渲染双路径会造成状态抖动);
        // 这里只保留地图/时钟拖动的平滑跟随
        if (!closing) {
            if (SAOMapPanel.isShown()) {
                SAOMapPanel.dragTo(this.width, this.height, (int) mouseX, (int) mouseY);
            }
            SAOClockPanel.dragTo(this.width, this.height, (int) mouseX, (int) mouseY);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        SAOMapPanel.endDragAndSave();
        SAOClockPanel.endDragAndSave();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) {
            return false;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // 时钟面板拖动(优先于地图面板,体积更小)
        if (SAOClockPanel.hitCard(this.width, this.height, mx, my)) {
            SAOClockPanel.beginDrag(this.width, this.height, mx, my);
            return true;
        }

        // 地图面板优先:图钉 → 面板内(开始拖动并吞掉点击);拖动结束才轮到菜单
        if (SAOMapPanel.isShown()) {
            if (SAOMapPanel.hitPin(this.width, this.height, mx, my)) {
                SAOMapPanel.togglePin();
                playClick();
                return true;
            }
            if (SAOMapPanel.hitCard(this.width, this.height, mx, my)) {
                SAOMapPanel.beginDrag(this.width, this.height, mx, my);
                return true;
            }
        }

        // 确认弹窗打开时:蓝钮=确认关闭,红钮/弹窗外=取消
        if (confirmClose) {
            MenuLayout.Rect at = dialogRect();
            int d = 26;
            int by = at.y() + Math.round(at.h() * 0.80f) - d / 2;
            int b1x = at.x() + at.w() / 4 - d / 2;
            int b2x = at.x() + at.w() * 3 / 4 - d / 2;
            if (MenuLayout.inCircle(b1x + d / 2, by + d / 2, d / 2 + 2, mx, my)) {
                playClick();
                beginClose();
                return true;
            }
            if (MenuLayout.inCircle(b2x + d / 2, by + d / 2, d / 2 + 2, mx, my)) {
                playPanel();
                confirmClose = false;
                return true;
            }
            if (mx < at.x() || mx >= at.x() + at.w() || my < at.y() || my >= at.y() + at.h()) {
                playPanel();
                confirmClose = false;
                return true;
            }
            return true;
        }

        // 信息弹窗打开:任意点击(圆钮/面板/外部)都只关闭弹窗,菜单保持打开
        if (infoOpen) {
            infoOpen = false;
            playPanel();
            return true;
        }

        int lx = localX(mx);
        int ly = localY(my);

        // 物品操作按钮(装备/信息/丢弃):优先于其他命中。
        // 几何必须与渲染完全一致:行矩形按窗口化 children 的长度布局
        if (actionMenuOpen) {
            int mainA = activeMain();
            MenuItem[] itemsA = mainA >= 0 ? activeItems(mainA) : null;
            int shownA = itemsA != null ? visibleChildrenItem(itemsA) : -1;
            if (itemsA != null && shownA >= 0 && itemsA[shownA].children() != null) {
                MenuItem[] winA = windowedChildren(itemsA[shownA].children());
                int anchorA = MenuLayout.menuItemRectAt(this.width, this.height, itemsA.length,
                        baseAnchorX, buttonY(mainA), shownA).centerY();
                if (actionRow >= 0 && actionRow < winA.length && winA[actionRow].stack() != null) {
                    MenuLayout.Rect rowA = MenuLayout.childItemRectAt(this.width, this.height,
                            winA.length, baseAnchorX, anchorA, actionRow);
                    for (int b = 0; b < 3; b++) {
                        MenuLayout.Rect br = actionButtonRect(rowA, b);
                        if (MenuLayout.inCircle(br.centerX(), br.centerY(), br.w() / 2 + 2, lx, ly)) {
                            MenuItem[] all = itemsA[shownA].children();
                            int real = childScroll + actionRow;
                            if (real < all.length && all[real].stack() != null) {
                                executeItemAction(b, all[real]);
                            } else {
                                actionMenuOpen = false;
                            }
                            return true;
                        }
                    }
                }
            }
        }

        int hitMain = MenuLayout.hoveredMainButtonAt(this.width, this.height, baseAnchorX, baseAnchorY, lx, ly);
        if (hitMain != -1) {
            mainTouched = true;
            if (selectedMain == hitMain) {
                // 再点已选中的按钮:收起面板回到初始按钮列(参照 SAO-World)
                selectedMain = -1;
                panelOwner = -1;
                expandedItem = -1;
                equipOwner = -1;
                actionMenuOpen = false;
                infoOpen = false;
                childScroll = 0;
                playPanel();
            } else {
                selectedMain = hitMain;
                expandedItem = -1;
                equipOwner = -1;
                actionMenuOpen = false;
                infoOpen = false;
                childScroll = 0;
                playClick();
            }
            mainPressIndex = hitMain;
            mainPressAt = now();
            return true;
        }

        // 圆点 = 物品栏槽位(第 1 个为副手指示,仅吞掉点击;其后 9 个切换选中槽位)
        // 仅当原版快捷栏被隐藏、圆点可见时响应
        if (SAOConfig.hideHotbar()) {
            for (int i = 0; i < MenuLayout.DOT_COUNT; i++) {
                if (MenuLayout.inDot(this.width, this.height, i, mx, my)) {
                    Player p = mc().player;
                    if (p != null && i > 0 && p.getInventory().selected != i - 1) {
                        p.getInventory().selected = i - 1;
                        playClick();
                    }
                    return true;
                }
            }
        }

        int main = activeMain();
        if (main >= 0) {
            MenuItem[] items = activeItems(main);
            int anchorY = buttonY(main);
            for (int i = 0; i < items.length; i++) {
                if (MenuLayout.menuItemRectAt(this.width, this.height, items.length, baseAnchorX, anchorY, i).contains(lx, ly)) {
                    itemPressColumn = 0;
                    itemPressIndex = i;
                    itemPressAt = now();
                    if (items[i].children() != null || items[i].action() == Action.SHOW_ITEMS) {
                        // 装备/物品:展开二级列表(动态物品 children 在 activeItems 挂上)
                        int newExpanded = expandedItem == i ? -1 : i;
                        if (newExpanded != expandedItem) {
                            // 切换展开项必须清掉上一列的选中状态:残留的 equipOwner
                            // 会让新列表按旧下标压暗(表现为只有一行亮、其余全透明)
                            equipOwner = -1;
                            childScroll = 0;
                            actionMenuOpen = false;
                        }
                        expandedItem = newExpanded;
                        playPanel();
                    } else {
                        lastClickedLabel = items[i].label();
                        runAction(items[i].action());
                    }
                    return true;
                }
            }
            int shown = visibleChildrenItem(items);
            if (shown >= 0 && items[shown].children() != null) {
                MenuItem[] children = windowedChildren(items[shown].children());
                int childAnchor = MenuLayout.menuItemRectAt(this.width, this.height, items.length, baseAnchorX, anchorY, shown).centerY();
                // 装备条目列(第三列):只读展示,点击不落穿关闭菜单
                int equipTarget = equipTargetIndex(items, shown);
                if (equipTarget >= 0) {
                    int equipAnchor = equipAnchorY(items, shown, equipTarget);
                    List<EquipEntry> entries = equipEntries(EQUIP_KINDS[equipTarget]);
                    for (int i = 0; i < entries.size(); i++) {
                        if (MenuLayout.equipItemRectAt(this.width, this.height, entries.size(), baseAnchorX, equipAnchor, i)
                                .contains(lx, ly)) {
                            playClick();
                            return true;
                        }
                    }
                }
                for (int i = 0; i < children.length; i++) {
                    if (MenuLayout.childItemRectAt(this.width, this.height, children.length, baseAnchorX, childAnchor, i).contains(lx, ly)) {
                        itemPressColumn = 1;
                        itemPressIndex = i;
                        itemPressAt = now();
                        if (children[i].action() == Action.SHOW_EQUIP) {
                            // 武器/护甲/首饰:展开/切换第三列已装备列表,不再打开物品栏
                            actionMenuOpen = false;
                            if (equipOwner != i) {
                                equipOwner = i;
                                equipAt = now();
                                playPanel();
                            }
                        } else if (children[i].stack() != null && !children[i].stack().isEmpty()) {
                            // 物品条目:行右侧弹出 装备/信息/丢弃 三按钮(再点同行收起)
                            if (actionMenuOpen && actionRow == i) {
                                actionMenuOpen = false;
                                playPanel();
                            } else {
                                actionMenuOpen = true;
                                actionRow = i;
                                actionAt = now();
                                playPanel();
                            }
                        } else {
                            lastClickedLabel = children[i].label();
                            runAction(children[i].action());
                        }
                        return true;
                    }
                }
            }
        }

        if (actionMenuOpen) {
            // 操作按钮打开时空点:只收按钮,不关菜单
            actionMenuOpen = false;
            playPanel();
            return true;
        }
        beginClose();
        return true;
    }

    private void runAction(Action action) {
        // INVITE_PLAYER 需要 label(玩家名),由点击处先记下再进来
        switch (action) {
            case SKILL -> {
                // 装饰性技能:本游戏暂无技能系统
                playClick();
                SAONotification.push(tr("saomenu.coming_soon"), "");
            }
            case SHOW_ITEMS -> playClick(); // 实际展开由 children 分支处理,此处兜底
            case OPEN_OPTIONS -> {
                playClick();
                mc().setScreen(new OptionsScreen(this, mc().options));
            }
            case OPEN_CONFIG -> {
                playClick();
                mc().setScreen(new SAOSettingsScreen(this));
            }
            case OPEN_STATS -> {
                playClick();
                Player p = mc().player;
                if (p != null) {
                    mc().setScreen(new SAOStatsScreen(this, p));
                }
            }
            case OPEN_ADVANCEMENTS -> {
                playClick();
                mc().setScreen(new SAOAdvancementsScreen(this));
            }
            case SWITCH_FRIENDS -> {
                selectedMain = 2;
                mainTouched = true;
                expandedItem = -1;
                playPanel();
            }
            case SWITCH_PARTY -> {
                selectedMain = 1;
                mainTouched = true;
                expandedItem = -1;
                playPanel();
            }
            case INVITE_PLAYER -> {
                // label 即被邀请人名(由菜单模型保证)
                String target = lastClickedLabel;
                if (target != null && !target.isEmpty()) {
                    new com.sao.saomenu.party.InviteC2S(target).sendToServer();
                    SAONotification.push(tr("saomenu.party.notify.sent.title"),
                            tr("saomenu.party.notify.sent.msg", target));
                }
                playClick();
            }
            case LEAVE_TEAM -> {
                new com.sao.saomenu.party.LeaveC2S().sendToServer();
                playClick();
            }
            case TOGGLE_MAP -> {
                playPanel();
                SAOMapPanel.toggle();
            }
            case CLOSE -> openConfirm();
            case NONE -> playClick();
        }
    }

    /** 打开登出确认弹窗(参照 SAO_Utils:点击 Logout 弹 Alert)。 */
    private void openConfirm() {
        confirmClose = true;
        confirmAt = now();
        releaseMoveKeys();
        playAlert();
    }

    @Override
    public void onClose() {
        beginClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 信息弹窗开着:任意关闭键先收弹窗
        if (infoOpen && (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_O)) {
            infoOpen = false;
            return true;
        }
        // 移动键穿透:SAO 式非阻塞菜单,开着也能走/跳/潜行/疾跑
        // (确认弹窗/信息弹窗开着时不穿透,防止误操作)
        if (!closing && !confirmClose && !infoOpen) {
            for (KeyMapping km : moveKeys()) {
                if (km.matches(keyCode, scanCode)) {
                    km.setDown(true);
                    return true;
                }
            }
        }
        // F5 切换视角(原版机制:Screen 打开时 keybind 不生效,需在此自行处理),
        // 便于切第三人称查看角色面前的世界空间菜单板
        if (keyCode == GLFW.GLFW_KEY_F5 && mc().player != null) {
            var opt = mc().options;
            switch (opt.getCameraType()) {
                case FIRST_PERSON -> opt.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                case THIRD_PERSON_BACK -> opt.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                default -> opt.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
            return true;
        }
        // 原版机制:屏幕打开时 KeyMapping 不会触发 click,必须在这里直接处理 O 键
        if (keyCode == GLFW.GLFW_KEY_O) {
            if (confirmClose) {
                confirmClose = false;
                return true;
            }
            beginClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // 移动键松开同步(菜单开着走动时松 W/空格等要停)
        for (KeyMapping km : moveKeys()) {
            if (km.matches(keyCode, scanCode)) {
                km.setDown(false);
                return true;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /** 允许在菜单打开时使用的移动键(前后左右/跳跃/潜行/疾跑)。供 Mixin 输入接管读取。 */
    public KeyMapping[] moveKeys() {
        var o = mc().options;
        return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight,
                o.keyJump, o.keyShift, o.keySprint};
    }

    /** 移动是否被暂时封锁(确认弹窗/信息弹窗/关闭动画)。供 Mixin 输入接管读取。 */
    public boolean isMovementBlocked() {
        return closing || confirmClose || infoOpen;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 滚轮不再切换主按钮(一级菜单固定);悬停在二级物品条目列上时滚动窗口
        if (!closing && !confirmClose && delta != 0) {
            int main = activeMain();
            MenuItem[] items = main >= 0 ? activeItems(main) : null;
            int shown = -1;
            if (items != null) {
                shown = visibleChildrenItem(items);
            }
            if (items != null && shown >= 0 && items[shown].children() != null
                    && items[shown].children().length > 0
                    && items[shown].children()[0].stack() != null) {
                // 物品条目列:命中任一可见行(或其附近)即滚动
                MenuItem[] children = items[shown].children();
                int rows = childVisibleRows();
                int lx = localX((int) mouseX);
                int ly = localY((int) mouseY);
                int anchorY = MenuLayout.menuItemRectAt(this.width, this.height, items.length, baseAnchorX, buttonY(main), shown).centerY();
                boolean over = false;
                for (int v = 0; v < Math.min(rows, children.length); v++) {
                    if (MenuLayout.childItemRectAt(this.width, this.height, rows, baseAnchorX, anchorY, v).contains(lx, ly)) {
                        over = true;
                        break;
                    }
                }
                if (over) {
                    // clamp 上界必须 >= 0:条目不足一屏时 max 为负,
                    // 直接钳会得到负 childScroll,后续下标运算越界崩溃
                    int max = Math.max(0, children.length - rows);
                    int before = childScroll;
                    childScroll = Mth.clamp(childScroll - (int) Math.signum(delta), 0, max);
                    if (childScroll != before) {
                        actionMenuOpen = false;
                        playClick();
                    }
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void beginClose() {
        if (closing) {
            return;
        }
        closing = true;
        closedAt = now();
        actionMenuOpen = false;
        infoOpen = false;
        releaseMoveKeys();
        // 地图未固定时随菜单一起收回;固定(图钉)则保留为 HUD 常显
        if (!SAOMapPanel.isPinned()) {
            if (SAOMapPanel.isShown()) {
                SAOMapPanel.toggle();
            }
        }
        playAlert();
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 菜单项标签:语言键 → 翻译;未知键(在线玩家名)→ 原样显示。
     * 原版对缺失翻译返回键本身,以此区分。
     */
    private static String resolveLabel(String key) {
        if (!key.startsWith("saomenu.") && !key.contains(".")) {
            return key;
        }
        return Component.translatable(key).getString();
    }

    private String playerName() {
        Player p = mc().player;
        return p != null ? p.getGameProfile().getName() : "Player";
    }

    private String tr(String key, Object... args) {
        // 不依赖 translatable 的 MessageFormat 替换(Forge/Fabric 行为不一致),
        // 手动替换 {n} 占位符,双平台结果一致
        String s = Component.translatable(key).getString();
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    private static String trim(float v) {
        float r = Math.round(v * 10f) / 10f;
        return (r == Math.rint(r)) ? String.valueOf((int) r) : String.valueOf(r);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeOutBack(float t) {
        float u = t - 1f;
        return 1f + 2.70158f * u * u * u + 1.70158f * u * u;
    }

    /** 把基础色(含 alpha)整体乘一个透明度系数。 */
    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        int na = Math.round(a * Mth.clamp(factor, 0f, 1f));
        return (na << 24) | rgb;
    }

    private void playLauncher() {
        if (!SAOConfig.sounds()) {
            return;
        }
        mc().getSoundManager().play(SimpleSoundInstance.forUI(SAOMenuPlatform.launcherSound(), 1.0F));
    }

    private void playClick() {
        if (!SAOConfig.sounds()) {
            return;
        }
        mc().getSoundManager().play(SimpleSoundInstance.forUI(SAOMenuPlatform.clickSound(), 1.0F));
    }

    private void playPanel() {
        if (!SAOConfig.sounds()) {
            return;
        }
        mc().getSoundManager().play(SimpleSoundInstance.forUI(SAOMenuPlatform.panelSound(), 1.0F));
    }

    private void playAlert() {
        if (!SAOConfig.sounds()) {
            return;
        }
        mc().getSoundManager().play(SimpleSoundInstance.forUI(SAOMenuPlatform.alertSound(), 1.0F));
    }
}
