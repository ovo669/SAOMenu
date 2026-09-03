package com.sao.saomenu.party;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.simple.BaseC2SMessage;
import dev.architectury.networking.simple.MessageType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S 邀请:发起者请求邀请目标玩家入队。
 *
 * <p>服务端在主线程执行;目标必须在线、未在别的模组队伍、队伍未满。</p>
 */
public final class InviteC2S extends BaseC2SMessage {

    private String targetName;

    public InviteC2S() {
    }

    /** 解码器入口:从缓冲区还原字段。 */
    public InviteC2S(FriendlyByteBuf buf) {
        this.targetName = buf.readUtf(64);
    }

    public InviteC2S(String targetName) {
        this.targetName = targetName;
    }

    @Override
    public MessageType getType() {
        return SAONetwork.INVITE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(targetName, 64);
    }

    @Override
    public void handle(NetworkManager.PacketContext ctx) {
        String name = this.targetName;
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                MinecraftServer server = sp.getServer();
                if (server != null) {
                    SAOTeamManager.handleInvite(server, sp, name);
                }
            }
        });
    }
}
