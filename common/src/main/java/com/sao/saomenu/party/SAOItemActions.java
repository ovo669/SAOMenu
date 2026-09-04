package com.sao.saomenu.party;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

/**
 * 服务端物品操作(SAO 菜单三按钮的执行端):
 * 装备 = 护甲换位(原位物品退回背包/掉落)或与手持互换;
 * 丢弃 = 整组/单个丢到世界(带玩家朝向速度)。
 */
public final class SAOItemActions {

    private SAOItemActions() {
    }

    /** 装备背包槽位(0-35)的物品。 */
    public static void handleEquip(MinecraftServer server, ServerPlayer player, int slot) {
        if (slot < 0 || slot >= 36) {
            return;
        }
        var inv = player.getInventory();
        ItemStack stack = inv.getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        // 护甲:按装备位换入,原位物品退回背包(放不下掉地上)
        Equipable equipable = Equipable.get(copy);
        if (equipable != null) {
            EquipmentSlot es = equipable.getEquipmentSlot();
            if (es != null && es.getType() == EquipmentSlot.Type.ARMOR) {
                int idx = switch (es) {
                    case HEAD -> 3;
                    case CHEST -> 2;
                    case LEGS -> 1;
                    case FEET -> 0;
                    default -> -1;
                };
                if (idx >= 0) {
                    ItemStack old = inv.armor.get(idx);
                    inv.armor.set(idx, copy);
                    inv.setItem(slot, ItemStack.EMPTY);
                    if (!old.isEmpty() && !inv.add(old)) {
                        player.drop(old, false);
                    }
                    playEquip(player, 1f);
                    return;
                }
            }
        }
        // 非护甲:与当前手持槽互换
        int sel = inv.selected;
        if (slot == sel) {
            return;
        }
        ItemStack displaced = inv.getItem(sel);
        inv.setItem(sel, copy);
        inv.setItem(slot, displaced);
        playEquip(player, 1.15f);
    }

    /** 丢弃背包槽位(0-35)的物品。 */
    public static void handleDrop(MinecraftServer server, ServerPlayer player, int slot, boolean all) {
        if (slot < 0 || slot >= 36) {
            return;
        }
        var inv = player.getInventory();
        ItemStack stack = inv.getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        int n = all ? stack.getCount() : 1;
        ItemStack dropped = inv.removeItem(slot, n);
        if (!dropped.isEmpty()) {
            player.drop(dropped, false);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.65f);
        }
    }

    /**
     * 二刀流:把两个背包槽位的剑放到主手与副手。
     *
     * <p>主手用「切换选中槽」而非搬运物品——剑本来就在背包里,
     * 直接把快捷栏选中位指过去最省事;剑不在快捷栏时才与选中槽互换。
     * 副手原有物品退回背包(放不下掉地上)。</p>
     */
    public static void handleDualWield(ServerPlayer player, int mainSlot, int offSlot) {
        final int OFFHAND = -2;
        boolean mainAlready = mainSlot == OFFHAND;
        boolean offAlready = offSlot == OFFHAND;
        if ((!mainAlready && (mainSlot < 0 || mainSlot >= 36))
                || (!offAlready && (offSlot < 0 || offSlot >= 36))
                || (!mainAlready && !offAlready && mainSlot == offSlot)) {
            return;
        }
        var inv = player.getInventory();

        // 1) 副手:需要从背包搬一把过去时,原有副手物品退回背包
        if (!offAlready) {
            ItemStack off = inv.getItem(offSlot);
            if (off.isEmpty()) {
                return;
            }
            ItemStack oldOff = inv.offhand.get(0);
            inv.offhand.set(0, off.copy());
            inv.setItem(offSlot, ItemStack.EMPTY);
            if (!oldOff.isEmpty() && !inv.add(oldOff)) {
                player.drop(oldOff, false);
            }
        }

        // 2) 主手:哨兵表示已握剑;否则把那把剑弄到「当前选中的快捷栏槽」上。
        //    只改 inv.selected 是不够的——客户端 selected 由客户端自己维护,
        //    服务端改它不会同步回去,表现为「切了模式但主手还是空手/旧物品」。
        //    所以统一搬到玩家当前 selected 槽,主手立刻拿到剑,无需依赖同步。
        if (!mainAlready) {
            ItemStack main = inv.getItem(mainSlot);
            if (main.isEmpty()) {
                return;
            }
            int sel = inv.selected;
            if (mainSlot != sel) {
                ItemStack displaced = inv.getItem(sel);
                inv.setItem(sel, main.copy());
                inv.setItem(mainSlot, displaced);
            }
        }
        playEquip(player, 1.2f);
    }

    private static void playEquip(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.8f, pitch);
    }
}
