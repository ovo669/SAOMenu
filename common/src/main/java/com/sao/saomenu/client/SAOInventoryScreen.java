package com.sao.saomenu.client;

import com.sao.saomenu.SAOMenuPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * SAO 风格物品栏:替代菜单"物品/武器/护甲"弹出的原版背包。
 *
 * <p>布局:标题 + 3x9 主物品区 + 快捷栏行 + 护甲列 + 副手槽 + "完成"按钮。
 * 交互:左键 拿起/放下/堆叠/交换,右键 拆分一半/放单个;点击面板外丢弃光标物品;
 * 关闭时剩余光标物品自动放回背包,放不下则丢到地面。</p>
 */
public class SAOInventoryScreen extends Screen {

    private static final int PANEL_BG = 0xE6232729;
    private static final int SHADOW = 0x3A000000;
    private static final int SLOT_BG = 0x52F9F9F9;
    private static final int SLOT_BORDER = 0x80FFFFFF;
    private static final int TEXT_WHITE = 0xFFF9F9F9;
    private static final int TEXT_ON_ACCENT = 0xFF232323;

    private static final int SLOT = 18;
    private static final int STEP = 20;
    private static final int GRID_W = 9 * STEP - 2;
    private static final int PANEL_W = GRID_W + 20 + SLOT + 16;
    private static final int PANEL_H = 34 + 4 * STEP - 2 + 8 + 28 + 12;
    private static final int TAB_W = (PANEL_W - 40) / 4;
    private static final int TAB_H = 12;

    // ---- 布局坐标(harness 复用,避免硬编码) ----
    static int gridX(int w) {
        return (w - PANEL_W) / 2 + 10;
    }

    static int gridY(int h) {
        return Math.max(10, (h - PANEL_H) / 2) + 34;
    }

    static int hotY(int h) {
        return gridY(h) + 3 * STEP + 8;
    }

    static int slotCenterX(int w, int col) {
        return gridX(w) + col * STEP + SLOT / 2;
    }

    static int slotCenterY(int h) {
        return hotY(h) + SLOT / 2;
    }

    static int mainSlotCenterY(int h) {
        return gridY(h) + SLOT / 2;
    }

    static int tabCenterX(int w, int i) {
        return (w - PANEL_W) / 2 + 20 + i * TAB_W + TAB_W / 2;
    }

    static int tabCenterY(int h) {
        return Math.max(10, (h - PANEL_H) / 2) + 20 + TAB_H / 2;
    }

    private final Player player;
    private ItemStack cursorStack = ItemStack.EMPTY;
    private final List<Slot> slots = new ArrayList<>();
    private NonNullList<ItemStack> items;
    private final Slot[] hotbarSlots = new Slot[9];
    private final Slot[] armorSlots = new Slot[4];
    private Slot hovered;
    private Slot offhandSlot;
    private int filter; // 0=全部 1=武器 2=护甲 3=材料
    private boolean dragging;
    private int dragButton;
    private final java.util.LinkedHashSet<Slot> dragSlots = new java.util.LinkedHashSet<>();
    private Slot lastSlot;
    private long lastSlotTime;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int doneX;
    private int doneY;

    /** 一个可交互槽位。 */
    private record Slot(int x, int y, NonNullList<ItemStack> list, int index) {
        ItemStack get() {
            return list.get(index);
        }

        void set(ItemStack s) {
            list.set(index, s);
        }

        boolean contains(int mx, int my) {
            return mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
        }
    }

    public SAOInventoryScreen(Player player) {
        super(Component.translatable("saomenu.menu.items"));
        this.player = player;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        buildSlots();
    }

    private void buildSlots() {
        slots.clear();
        // 1.20.1:items 为扁平 36 格(0-8 快捷栏 + 9-35 主物品)
        items = player.getInventory().items;
        NonNullList<ItemStack> armor = player.getInventory().armor;
        NonNullList<ItemStack> offhand = player.getInventory().offhand;

        panelW = PANEL_W;
        panelH = PANEL_H;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(10, (this.height - panelH) / 2);

        int gx = gridX(this.width);
        int gy = gridY(this.height);
        // 主物品区 3 行(9..35)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slots.add(new Slot(gx + c * STEP, gy + r * STEP, items, 9 + r * 9 + c));
            }
        }
        // 快捷栏行(0..8)
        int hotY = hotY(this.height);
        for (int c = 0; c < 9; c++) {
            Slot s = new Slot(gx + c * STEP, hotY, items, c);
            slots.add(s);
            hotbarSlots[c] = s;
        }
        // 护甲列 + 副手
        int ax = gx + GRID_W + 8;
        for (int i = 0; i < 4; i++) {
            Slot s = new Slot(ax, gy + i * STEP, armor, i);
            slots.add(s);
            armorSlots[i] = s;
        }
        offhandSlot = new Slot(ax, hotY, offhand, 0);
        slots.add(offhandSlot);

        doneX = panelX + panelW - 60 - 8;
        doneY = panelY + panelH - 28;
    }

    // ------------------------------------------------------------ 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        g.fill(panelX + 3, panelY + 3, panelX + panelW + 3, panelY + panelH + 3, SHADOW);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);

        String title = Component.translatable("saomenu.menu.items").getString();
        g.drawString(this.font, title, panelX + panelW / 2 - this.font.width(title) / 2,
                panelY + 6, TEXT_WHITE, false);

        renderTabs(g);

        hovered = hoveredSlot(mouseX, mouseY);
        for (Slot s : slots) {
            boolean hover = s == hovered;
            int accent = SAOConfig.accent();
            g.fill(s.x(), s.y(), s.x() + SLOT, s.y() + SLOT, SLOT_BG);
            g.fill(s.x(), s.y(), s.x() + SLOT, s.y() + 1, hover ? accent : SLOT_BORDER);
            g.fill(s.x(), s.y(), s.x() + 1, s.y() + SLOT, hover ? accent : SLOT_BORDER);
            g.fill(s.x(), s.y() + SLOT - 1, s.x() + SLOT, s.y() + SLOT, hover ? accent : SLOT_BORDER);
            g.fill(s.x() + SLOT - 1, s.y(), s.x() + SLOT, s.y() + SLOT, hover ? accent : SLOT_BORDER);
            ItemStack stack = s.get();
            if (!stack.isEmpty()) {
                g.renderItem(stack, s.x() + 1, s.y() + 1);
                g.renderItemDecorations(this.font, stack, s.x() + 1, s.y() + 1);
            }
            // 分类过滤:不匹配的槽位(含空槽)整体压暗。
            // 物品渲染在提升的 z 高度,压暗填充必须画在更高处才能盖住图标
            if (!matchesFilter(stack)) {
                g.pose().pushPose();
                g.pose().translate(0f, 0f, 200f);
                g.fill(s.x(), s.y(), s.x() + SLOT, s.y() + SLOT, 0xB3000000);
                g.pose().popPose();
            }
        }

        // 底部快捷键提示
        String hints = Component.translatable("saomenu.inventory.hints").getString();
        g.drawString(this.font, hints, panelX + 12, panelY + panelH - 12, 0xFF9A9B9D, false);

        // 完成按钮
        boolean hoverDone = mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 20;
        g.fill(doneX, doneY, doneX + 60, doneY + 20, hoverDone ? lighten(SAOConfig.accent()) : SAOConfig.accent());
        String done = Component.translatable("saomenu.inventory.done").getString();
        g.drawString(this.font, done, doneX + (60 - this.font.width(done)) / 2,
                doneY + 6, TEXT_ON_ACCENT, false);

        // 悬停提示
        if (hovered != null && !hovered.get().isEmpty()) {
            g.renderTooltip(this.font, hovered.get(), mouseX, mouseY);
        }

        // 光标上的物品
        if (!cursorStack.isEmpty()) {
            g.renderItem(cursorStack, mouseX - 8, mouseY - 8);
            g.renderItemDecorations(this.font, cursorStack, mouseX - 8, mouseY - 8);
        }
    }

    /** 顶部分类标签:全部 / 武器 / 护甲 / 材料。 */
    private void renderTabs(GuiGraphics g) {
        String[] keys = {"saomenu.inventory.tab_all", "saomenu.inventory.tab_weapon",
                "saomenu.inventory.tab_armor", "saomenu.inventory.tab_material"};
        for (int i = 0; i < 4; i++) {
            int tx = panelX + 20 + i * TAB_W;
            int ty = panelY + 20;
            String label = Component.translatable(keys[i]).getString();
            g.drawString(this.font, label, tx + (TAB_W - this.font.width(label)) / 2,
                    ty + 2, i == filter ? SAOConfig.accent() : 0xFF9A9B9D, false);
            if (i == filter) {
                g.fill(tx + 8, ty + TAB_H - 1, tx + TAB_W - 8, ty + TAB_H, SAOConfig.accent());
            }
        }
    }

    private boolean matchesFilter(ItemStack stack) {
        return filter == 0 || categoryOf(stack) == filter;
    }

    /** 分类:1=武器 2=护甲 3=材料。 */
    private static int categoryOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        var item = stack.getItem();
        if (item instanceof net.minecraft.world.item.ArmorItem) {
            return 2;
        }
        if (item instanceof net.minecraft.world.item.SwordItem
                || item instanceof net.minecraft.world.item.BowItem
                || item instanceof net.minecraft.world.item.CrossbowItem
                || item instanceof net.minecraft.world.item.TridentItem) {
            return 1;
        }
        return 3;
    }

    private Slot hoveredSlot(int mouseX, int mouseY) {
        for (Slot s : slots) {
            if (s.contains(mouseX, mouseY)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        // 分类标签点击
        if (button == 0) {
            for (int i = 0; i < 4; i++) {
                int tx = panelX + 20 + i * TAB_W;
                int ty = panelY + 20;
                if (mx >= tx && mx < tx + TAB_W && my >= ty && my < ty + TAB_H) {
                    filter = i;
                    playClick();
                    return true;
                }
            }
        }
        if (button == 0 && mx >= doneX && mx < doneX + 60 && my >= doneY && my < doneY + 20) {
            onClose();
            return true;
        }
        for (Slot s : slots) {
            if (s.contains(mx, my)) {
                if (button == 0 && hasShiftDown()) {
                    shiftClick(s);
                } else if (button == 0) {
                    leftClick(s);
                } else if (button == 1) {
                    rightClick(s);
                }
                return true;
            }
        }
        // 点击面板外:丢弃光标物品
        if (!cursorStack.isEmpty()) {
            player.drop(cursorStack, true);
            cursorStack = ItemStack.EMPTY;
            playClick();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 左键:拿起 / 放下 / 堆叠 / 交换;双击同槽位且光标有物品时聚合同种。 */
    private void leftClick(Slot s) {
        long t = System.currentTimeMillis();
        boolean dbl = s == lastSlot && t - lastSlotTime < 500 && !cursorStack.isEmpty();
        if (dbl) {
            gatherSameItems();
        } else {
            handleLeftClick(s);
        }
        lastSlot = s;
        lastSlotTime = t;
    }

    private void handleLeftClick(Slot s) {
        ItemStack cur = s.get();
        if (cursorStack.isEmpty()) {
            if (!cur.isEmpty()) {
                cursorStack = cur.copy();
                s.set(ItemStack.EMPTY);
                playClick();
            }
        } else if (cur.isEmpty()) {
            s.set(cursorStack.copy());
            cursorStack = ItemStack.EMPTY;
            playClick();
        } else if (ItemStack.isSameItemSameTags(cur, cursorStack)) {
            int move = Math.min(cursorStack.getCount(), cur.getMaxStackSize() - cur.getCount());
            if (move > 0) {
                cur.grow(move);
                cursorStack.shrink(move);
                playClick();
            } else {
                swap(s);
            }
        } else {
            swap(s);
        }
    }

    /** 双击聚合:把背包里所有同种物品吸到光标上(不超过最大堆叠)。 */
    private void gatherSameItems() {
        boolean changed = false;
        for (Slot t : slots) {
            ItemStack other = t.get();
            if (other.isEmpty() || !ItemStack.isSameItemSameTags(other, cursorStack)) {
                continue;
            }
            int move = Math.min(other.getCount(), cursorStack.getMaxStackSize() - cursorStack.getCount());
            if (move > 0) {
                cursorStack.grow(move);
                other.shrink(move);
                changed = true;
            }
            if (cursorStack.getCount() >= cursorStack.getMaxStackSize()) {
                break;
            }
        }
        if (changed) {
            playClick();
        }
    }

    /** 右键:空手拆一半;持物放单个。 */
    private void rightClick(Slot s) {
        ItemStack cur = s.get();
        if (cursorStack.isEmpty()) {
            if (!cur.isEmpty()) {
                // 拆分一半(向上取整);数量 1 时整堆拿起,与原版一致
                cursorStack = cur.split((cur.getCount() + 1) / 2);
                playClick();
            }
        } else if (cur.isEmpty()) {
            s.set(cursorStack.split(1));
            playClick();
        } else if (ItemStack.isSameItemSameTags(cur, cursorStack) && cur.getCount() < cur.getMaxStackSize()) {
            cur.grow(1);
            cursorStack.shrink(1);
            playClick();
        }
    }

    private void swap(Slot s) {
        ItemStack t = s.get().copy();
        s.set(cursorStack.copy());
        cursorStack = t;
        playClick();
    }

    // ------------------------------------------------------------ Shift 一键转移

    /** Shift+左键:快捷栏↔主物品区,护甲/副手→主物品区,主区的护甲优先入护甲槽。 */
    private void shiftClick(Slot s) {
        ItemStack cur = s.get();
        if (cur.isEmpty()) {
            return;
        }
        if (s.list() != items) {
            // 护甲 / 副手 → 主物品区
            moveInto(items, 9, 36, s);
            return;
        }
        if (s.index() < 9) {
            // 快捷栏 → 主物品区
            moveInto(items, 9, 36, s);
            return;
        }
        // 主物品区:护甲优先入对应护甲槽
        if (cur.getItem() instanceof ArmorItem armorItem) {
            int ai = armorIndex(armorItem);
            if (ai >= 0) {
                Slot t = armorSlots[ai];
                if (t.get().isEmpty()) {
                    t.set(cur.copy());
                    s.set(ItemStack.EMPTY);
                    playClick();
                    return;
                }
                if (tryMerge(s, t)) {
                    return;
                }
            }
        }
        moveInto(items, 0, 9, s);
    }

    private static int armorIndex(ArmorItem item) {
        // EquipmentSlot:FEET=2..HEAD=5 → 护甲 NonNullList 下标 0..3
        return item.getEquipmentSlot().getIndex() - EquipmentSlot.FEET.getIndex();
    }

    /** 把 src 的物品合并/搬入 target 的 [from, to) 区间。 */
    private void moveInto(NonNullList<ItemStack> target, int from, int to, Slot src) {
        for (Slot t : slots) {
            if (t.list() == target && t.index() >= from && t.index() < to && tryMerge(src, t)) {
                return;
            }
        }
        for (Slot t : slots) {
            if (t.list() == target && t.index() >= from && t.index() < to && t.get().isEmpty()) {
                t.set(src.get().copy());
                src.set(ItemStack.EMPTY);
                playClick();
                return;
            }
        }
    }

    private boolean tryMerge(Slot from, Slot to) {
        ItemStack a = from.get();
        ItemStack b = to.get();
        if (!b.isEmpty() && ItemStack.isSameItemSameTags(a, b)) {
            int move = Math.min(a.getCount(), b.getMaxStackSize() - b.getCount());
            if (move > 0) {
                b.grow(move);
                a.shrink(move);
                playClick();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 拖拽

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!cursorStack.isEmpty()) {
            if (!dragging) {
                dragging = true;
                dragButton = button;
                dragSlots.clear();
            }
            Slot s = hoveredSlot((int) mouseX, (int) mouseY);
            if (s != null) {
                dragSlots.add(s);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            if (dragButton == 1) {
                distributeRightDrag();
            } else {
                distributeLeftDrag();
            }
            dragSlots.clear();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 右键拖放:拖过的槽位各放 1 个。 */
    private void distributeRightDrag() {
        boolean changed = false;
        for (Slot s : dragSlots) {
            if (cursorStack.isEmpty()) {
                break;
            }
            if (placeOne(s, false)) {
                changed = true;
            }
        }
        if (changed) {
            playClick();
        }
    }

    /** 左键拖放:光标物品在拖过的槽位间均分,余数给靠前的槽位。 */
    private void distributeLeftDrag() {
        if (dragSlots.isEmpty()) {
            return;
        }
        int total = cursorStack.getCount();
        int n = dragSlots.size();
        boolean changed = false;
        if (total < n) {
            // 槽位多于物品:逐个放 1,放完为止
            for (Slot s : dragSlots) {
                if (cursorStack.isEmpty()) {
                    break;
                }
                if (placeOne(s, false)) {
                    changed = true;
                }
            }
        } else {
            int per = total / n;
            int rem = total % n;
            for (Slot s : dragSlots) {
                if (cursorStack.isEmpty()) {
                    break;
                }
                int give = per + (rem-- > 0 ? 1 : 0);
                if (placeUpTo(s, give, false)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            playClick();
        }
    }

    /** 放 1 个到槽位(空槽或同种可堆叠)。 */
    private boolean placeOne(Slot s, boolean sound) {
        ItemStack cur = s.get();
        if (cur.isEmpty()) {
            s.set(cursorStack.split(1));
            if (sound) {
                playClick();
            }
            return true;
        }
        if (ItemStack.isSameItemSameTags(cur, cursorStack) && cur.getCount() < cur.getMaxStackSize()) {
            cur.grow(1);
            cursorStack.shrink(1);
            if (sound) {
                playClick();
            }
            return true;
        }
        return false;
    }

    /** 放至多 give 个到槽位。 */
    private boolean placeUpTo(Slot s, int give, boolean sound) {
        ItemStack cur = s.get();
        if (cur.isEmpty()) {
            s.set(cursorStack.split(give));
            if (sound) {
                playClick();
            }
            return true;
        }
        if (ItemStack.isSameItemSameTags(cur, cursorStack)) {
            int move = Math.min(give, cur.getMaxStackSize() - cur.getCount());
            if (move > 0) {
                cur.grow(move);
                cursorStack.shrink(move);
                if (sound) {
                    playClick();
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 键盘

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        hovered = hoveredSlot((int) mouseX, (int) mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 滚轮切换快捷栏选中槽位(原版物品栏行为)
        if (player != null) {
            int sel = player.getInventory().selected + (delta > 0 ? -1 : 1);
            player.getInventory().selected = Math.floorMod(sel, 9);
            playClick();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            numberKey(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Q) {
            dropHovered(hasControlDown());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && hovered != null && hovered != offhandSlot) {
            swapTwo(hovered, offhandSlot);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_O) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 数字键 1-9:悬停槽与对应快捷栏槽交换/搬运。 */
    private void numberKey(int n) {
        if (n < 0 || n >= 9) {
            return;
        }
        Slot hot = hotbarSlots[n];
        if (cursorStack.isEmpty()) {
            if (hovered != null && hovered != hot) {
                swapTwo(hovered, hot);
            }
            return;
        }
        if (hot.get().isEmpty()) {
            hot.set(cursorStack.copy());
            cursorStack = ItemStack.EMPTY;
            playClick();
        } else if (ItemStack.isSameItemSameTags(hot.get(), cursorStack)
                && hot.get().getCount() < hot.get().getMaxStackSize()) {
            int move = Math.min(cursorStack.getCount(), hot.get().getMaxStackSize() - hot.get().getCount());
            hot.get().grow(move);
            cursorStack.shrink(move);
            playClick();
        } else {
            ItemStack t = hot.get().copy();
            hot.set(cursorStack.copy());
            cursorStack = t;
            playClick();
        }
    }

    /** Q 丢弃悬停槽物品;按住 Ctrl 丢弃整堆。 */
    private void dropHovered(boolean whole) {
        if (hovered == null || hovered.get().isEmpty()) {
            return;
        }
        ItemStack stack = whole ? hovered.get().copy() : hovered.get().split(1);
        if (whole) {
            hovered.set(ItemStack.EMPTY);
        }
        player.drop(stack, true);
        playClick();
    }

    private void swapTwo(Slot a, Slot b) {
        ItemStack t = a.get().copy();
        a.set(b.get().copy());
        b.set(t);
        playClick();
    }

    /** 预览自检钩子:绕过按键修饰,直接执行指定下标槽位的 Shift 转移。 */
    void previewShiftClick(int index) {
        for (Slot s : slots) {
            if (s.list() == items && s.index() == index) {
                shiftClick(s);
                return;
            }
        }
    }

    private static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 30);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 30);
        int b = Math.min(255, (argb & 0xFF) + 30);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void playClick() {
        if (SAOConfig.sounds()) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SAOMenuPlatform.clickSound(), 1.0F));
        }
    }

    @Override
    public void onClose() {
        // 剩余光标物品放回背包,放不下则丢到地面
        if (!cursorStack.isEmpty() && player != null) {
            if (!player.getInventory().add(cursorStack)) {
                player.drop(cursorStack, true);
            }
            cursorStack = ItemStack.EMPTY;
        }
        super.onClose();
    }

}
