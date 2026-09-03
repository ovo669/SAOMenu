package com.sao.saomenu.party;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.simple.BaseC2SMessage;
import dev.architectury.networking.simple.MessageType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S 离开队伍:从当前模组队伍移除自己并广播同步。
 */
public final class LeaveC2S extends BaseC2SMessage {

    public LeaveC2S() {
    }

    /** 解码器入口:无字段,仅对齐 MessageDecoder 签名。 */
    public LeaveC2S(FriendlyByteBuf buf) {
    }

    @Override
    public MessageType getType() {
        return SAONetwork.LEAVE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public void handle(NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                MinecraftServer server = sp.getServer();
                if (server != null) {
                    SAOTeamManager.handleLeave(server, sp);
                }
            }
        });
    }
}
