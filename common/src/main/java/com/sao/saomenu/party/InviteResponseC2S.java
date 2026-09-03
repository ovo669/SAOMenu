package com.sao.saomenu.party;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.simple.BaseC2SMessage;
import dev.architectury.networking.simple.MessageType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S 邀请应答:被邀请人接受(true)/拒绝(false)当前待处理邀请。
 */
public final class InviteResponseC2S extends BaseC2SMessage {

    private boolean accept;

    public InviteResponseC2S() {
    }

    /** 解码器入口:从缓冲区还原字段。 */
    public InviteResponseC2S(FriendlyByteBuf buf) {
        this.accept = buf.readBoolean();
    }

    public InviteResponseC2S(boolean accept) {
        this.accept = accept;
    }

    @Override
    public MessageType getType() {
        return SAONetwork.INVITE_RESPONSE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(accept);
    }

    @Override
    public void handle(NetworkManager.PacketContext ctx) {
        boolean ok = this.accept;
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                MinecraftServer server = sp.getServer();
                if (server != null) {
                    SAOTeamManager.handleInviteResponse(server, sp, ok);
                }
            }
        });
    }
}
