package com.sao.saomenu.party;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.simple.BaseC2SMessage;
import dev.architectury.networking.simple.MessageType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S 丢弃物品:把背包槽位物品丢到世界(all=true 整组,false 单个)。
 */
public final class DropItemC2S extends BaseC2SMessage {

    private int slot;
    private boolean all;

    public DropItemC2S() {
    }

    /** 解码器入口:从缓冲区还原字段。 */
    public DropItemC2S(FriendlyByteBuf buf) {
        this.slot = buf.readVarInt();
        this.all = buf.readBoolean();
    }

    public DropItemC2S(int slot, boolean all) {
        this.slot = slot;
        this.all = all;
    }

    @Override
    public MessageType getType() {
        return SAONetwork.DROP_ITEM;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeBoolean(all);
    }

    @Override
    public void handle(NetworkManager.PacketContext ctx) {
        int s = this.slot;
        boolean a = this.all;
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                MinecraftServer server = sp.getServer();
                if (server != null) {
                    SAOItemActions.handleDrop(server, sp, s, a);
                }
            }
        });
    }
}
