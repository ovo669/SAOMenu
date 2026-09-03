package com.sao.saomenu.party;

import com.sao.saomenu.SAOMenu;
import dev.architectury.networking.simple.MessageType;
import dev.architectury.networking.simple.SimpleNetworkManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * SAO 组队网络层:4 条消息构成邀请握手与队伍同步。
 *
 * <p>组队模型直接用 MC 原生 scoreboard team(服务端权威,原版 /team 命令
 * 创建的队伍同样可被识别),模组只补上「SAO 式邀请确认」流程:</p>
 * <ol>
 *   <li>C2S {@code INVITE}:发起者把被邀请人名字发给服务端,服务端建队/
 *       校验后向被邀请人发 S2C {@code INVITE_REQUEST}(客户端弹 SAO 邀请窗)</li>
 *   <li>C2S {@code INVITE_RESPONSE}:被邀请人接受/拒绝;接受时服务端把
 *       其加入队伍,并广播 S2C {@code TEAM_SYNC} 给全队刷新 HUD</li>
 *   <li>C2S {@code LEAVE}:离开当前队伍,同样广播 TEAM_SYNC</li>
 * </ol>
 *
 * <p>注册必须在双端初始化阶段各调一次 {@link #init()}(见平台实现类),
 * S2C 解码器只在客户端环境注册,C2S 只在服务端环境注册。</p>
 */
public final class SAONetwork {

    public static final SimpleNetworkManager CHANNEL = SimpleNetworkManager.create(SAOMenu.MOD_ID);

    public static MessageType INVITE;
    public static MessageType INVITE_REQUEST;
    public static MessageType INVITE_RESPONSE;
    public static MessageType LEAVE;
    public static MessageType TEAM_SYNC;
    public static MessageType EQUIP_ITEM;
    public static MessageType DROP_ITEM;

    private static boolean initialized;

    private SAONetwork() {
    }

    /** 双端主入口调用;幂等。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        INVITE = CHANNEL.registerC2S("invite", InviteC2S::new);
        INVITE_RESPONSE = CHANNEL.registerC2S("invite_response", InviteResponseC2S::new);
        LEAVE = CHANNEL.registerC2S("leave", LeaveC2S::new);
        INVITE_REQUEST = CHANNEL.registerS2C("invite_request", InviteRequestS2C::new);
        TEAM_SYNC = CHANNEL.registerS2C("team_sync", TeamSyncS2C::new);
        EQUIP_ITEM = CHANNEL.registerC2S("equip_item", EquipItemC2S::new);
        DROP_ITEM = CHANNEL.registerC2S("drop_item", DropItemC2S::new);
    }
}
