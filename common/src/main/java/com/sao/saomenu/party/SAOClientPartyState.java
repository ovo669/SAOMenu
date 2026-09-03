package com.sao.saomenu.party;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端组队状态缓存:待处理邀请 + 当前队伍成员表。
 *
 * <p>只存数据不渲染——邀请窗在 {@link com.sao.saomenu.client.SAOInviteScreen},
 * 队友血条在 SAOHud,绿三角在 SAOTargetBar3D,都从这里读。</p>
 */
public final class SAOClientPartyState {

    /** 待处理邀请(邀请人名字),null 表示无。 */
    private static volatile String pendingInviter;
    private static volatile long inviteAt;

    /** 当前队伍:标题(队长名)+ 成员名列表;title 为空表示无队伍。 */
    private static volatile String teamTitle = "";
    private static volatile List<String> teamMembers = List.of();

    private SAOClientPartyState() {
    }

    // ------------------------------------------------------------ S2C 入口

    /** 收到邀请(网络线程 → 主线程)。 */
    public static void onInviteReceived(String inviterName) {
        pendingInviter = inviterName;
        inviteAt = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof com.sao.saomenu.client.SAOMenuScreen) {
            mc.setScreen(new com.sao.saomenu.client.SAOInviteScreen(inviterName));
        }
    }

    /** 收到队伍同步(网络线程 → 主线程)。 */
    public static void onTeamSync(String title, List<String> members) {
        teamTitle = title == null ? "" : title;
        teamMembers = new ArrayList<>(members);
        // 自己离队时顺手清掉陈旧邀请
        if (teamTitle.isEmpty()) {
            pendingInviter = null;
        }
    }

    // ------------------------------------------------------------ 渲染层读取

    /** 当前待处理邀请的邀请人名;无邀请返回 null。超过有效期自动视为无。 */
    public static String pendingInviter() {
        String inv = pendingInviter;
        if (inv != null && System.currentTimeMillis() - inviteAt > SAOTeamManager.INVITE_TIMEOUT_MS) {
            pendingInviter = null;
            return null;
        }
        return inv;
    }

    /** 关闭/应答邀请窗后清引用。 */
    public static void clearInvite() {
        pendingInviter = null;
    }

    /** 队伍标题(队长名);无队伍为空串。 */
    public static String teamTitle() {
        return teamTitle;
    }

    /** 队伍成员名(已排序,不含重复);无队伍为空表。 */
    public static List<String> teamMembers() {
        return teamMembers;
    }

    /** 是否在模组队伍中。 */
    public static boolean inParty() {
        return !teamTitle.isEmpty() && !teamMembers.isEmpty();
    }

    /** 世界/服务器切换时清空全部状态。 */
    public static void reset() {
        pendingInviter = null;
        teamTitle = "";
        teamMembers = List.of();
    }
}
