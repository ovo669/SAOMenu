package com.sao.saomenu.party;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.simple.BaseS2CMessage;
import dev.architectury.networking.simple.MessageType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 队伍同步:服务端→队员,刷新客户端缓存的成员表(供 HUD 血条/绿三角用)。
 * 空标题表示清空(离队)。
 */
public final class TeamSyncS2C extends BaseS2CMessage {

    private String title;
    private List<String> members;

    public TeamSyncS2C() {
    }

    /** 解码器入口:从缓冲区还原字段。 */
    public TeamSyncS2C(FriendlyByteBuf buf) {
        this.title = buf.readUtf(64);
        int n = buf.readVarInt();
        this.members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            this.members.add(buf.readUtf(64));
        }
    }

    public TeamSyncS2C(String title, List<String> members) {
        this.title = title;
        this.members = members;
    }

    @Override
    public MessageType getType() {
        return SAONetwork.TEAM_SYNC;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(title, 64);
        buf.writeVarInt(members.size());
        for (String m : members) {
            buf.writeUtf(m, 64);
        }
    }

    @Override
    public void handle(NetworkManager.PacketContext ctx) {
        String t = this.title;
        List<String> ms = this.members;
        ctx.queue(() -> SAOClientPartyState.onTeamSync(t, ms));
    }
}
