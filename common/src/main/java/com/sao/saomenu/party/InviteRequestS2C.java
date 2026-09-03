package com.sao.saomenu.party;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.simple.BaseS2CMessage;
import dev.architectury.networking.simple.MessageType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S2C 邀请请求:服务端→被邀请人,客户端弹出 SAO 邀请确认窗。
 */
public final class InviteRequestS2C extends BaseS2CMessage {

    private String inviterName;

    public InviteRequestS2C() {
    }

    /** 解码器入口:从缓冲区还原字段。 */
    public InviteRequestS2C(FriendlyByteBuf buf) {
        this.inviterName = buf.readUtf(64);
    }

    public InviteRequestS2C(String inviterName) {
        this.inviterName = inviterName;
    }

    @Override
    public MessageType getType() {
        return SAONetwork.INVITE_REQUEST;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(inviterName, 64);
    }

    @Override
    public void handle(NetworkManager.PacketContext ctx) {
        String name = this.inviterName;
        ctx.queue(() -> SAOClientPartyState.onInviteReceived(name));
    }
}
