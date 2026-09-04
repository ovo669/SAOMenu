package com.sao.saomenu.party;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.simple.BaseC2SMessage;
import dev.architectury.networking.simple.MessageType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S 二刀流装备:把两个背包槽位的剑分别放到主手与副手。
 *
 * <p>背包写入必须由服务端执行(客户端改自己的 Inventory 会被下一次同步覆盖),
 * 所以菜单只发槽位号,实际换装在 {@link SAOItemActions#handleDualWield} 里做。</p>
 */
public final class DualWieldC2S extends BaseC2SMessage {

    private int mainSlot;
    private int offSlot;

    public DualWieldC2S() {
    }

    /** 解码器入口:从缓冲区还原字段。 */
    public DualWieldC2S(FriendlyByteBuf buf) {
        this.mainSlot = buf.readVarInt();
        this.offSlot = buf.readVarInt();
    }

    public DualWieldC2S(int mainSlot, int offSlot) {
        this.mainSlot = mainSlot;
        this.offSlot = offSlot;
    }

    @Override
    public MessageType getType() {
        return SAONetwork.DUAL_WIELD;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(mainSlot);
        buf.writeVarInt(offSlot);
    }

    @Override
    public void handle(NetworkManager.PacketContext ctx) {
        int m = this.mainSlot;
        int o = this.offSlot;
        ctx.queue(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                SAOItemActions.handleDualWield(sp, m, o);
            }
        });
    }
}
