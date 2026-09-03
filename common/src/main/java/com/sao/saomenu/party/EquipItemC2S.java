package com.sao.saomenu.party;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.simple.BaseC2SMessage;
import dev.architectury.networking.simple.MessageType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S 装备物品:把背包槽位物品穿到护甲位(护甲)或与手持互换(武器/工具等)。
 */
public final class EquipItemC2S extends BaseC2SMessage {

    private int slot;

    public EquipItemC2S() {
    }

    /** 解码器入口:从缓冲区还原字段。 */
    public EquipItemC2S(FriendlyByteBuf buf) {
        this.slot = buf.readVarInt();
    }

    public EquipItemC2S(int slot) {
        this.slot = slot;
    }

    @Override
    public MessageType getType() {
        return SAONetwork.EQUIP_ITEM;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
    }

    @Override
    public void handle(NetworkManager.PacketContext ctx) {
        int s = this.slot;
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                MinecraftServer server = sp.getServer();
                if (server != null) {
                    SAOItemActions.handleEquip(server, sp, s);
                }
            }
        });
    }
}
