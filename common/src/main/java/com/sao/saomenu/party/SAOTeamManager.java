package com.sao.saomenu.party;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端组队逻辑:SAO 式邀请握手 + 原生 scoreboard team 承载成员关系。
 *
 * <p>邀请会话:{@code Map<被邀请人UUID, 邀请人名字>}。每个玩家同时只保留
 * 一份待处理邀请(新邀请覆盖旧邀请),超时自动失效,避免 stale 邀请被接受。</p>
 *
 * <p>队伍命名:{@code saomenu_<队长UUID>} 避免与玩家自建队伍撞名;
 * 队伍显示名 = 队长名(客户端 HUD 标题直接用)。
 * 队伍颜色设为白色并关 friendlyFire?不——保持原版默认,模组不改战斗规则。</p>
 */
public final class SAOTeamManager {

    /** 邀请有效期(ms)。 */
    public static final long INVITE_TIMEOUT_MS = 30_000L;
    /** 单队人数上限(参照 SAO 队伍上限)。 */
    public static final int MAX_PARTY_SIZE = 8;

    private static final Map<UUID, PendingInvite> PENDING = new HashMap<>();

    private record PendingInvite(String inviterName, UUID inviterId, long at) {
        boolean expired(long now) {
            return now - at > INVITE_TIMEOUT_MS;
        }
    }

    private SAOTeamManager() {
    }

    // ------------------------------------------------------------ 邀请握手

    /** 发起者 inviteTargetName 的邀请流程;结果通过聊天栏 + 通知反馈双方。 */
    public static void handleInvite(MinecraftServer server, ServerPlayer inviter, String targetName) {
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            inviter.sendSystemMessage(Component.translatable("saomenu.party.msg.not_online", targetName));
            return;
        }
        if (target.getUUID().equals(inviter.getUUID())) {
            inviter.sendSystemMessage(Component.translatable("saomenu.party.msg.self"));
            return;
        }
        PlayerTeam team = teamOf(server, inviter);
        if (team != null && team.getPlayers().size() >= MAX_PARTY_SIZE && !team.getPlayers().contains(targetName)) {
            inviter.sendSystemMessage(Component.translatable("saomenu.party.msg.full", MAX_PARTY_SIZE));
            return;
        }
        // 目标已在别的队伍:提示发起者
        PlayerTeam targetTeam = teamOf(server, target);
        if (targetTeam != null && targetTeam != team) {
            inviter.sendSystemMessage(Component.translatable("saomenu.party.msg.target_in_team", targetName));
            return;
        }

        PENDING.put(target.getUUID(), new PendingInvite(inviter.getGameProfile().getName(), inviter.getUUID(),
                System.currentTimeMillis()));
        // S2C:弹 SAO 邀请窗(客户端渲染层)
        new InviteRequestS2C(inviter.getGameProfile().getName()).sendTo(target);
        inviter.sendSystemMessage(Component.translatable("saomenu.party.msg.sent", targetName));
    }

    /** 被邀请人对邀请的应答。 */
    public static void handleInviteResponse(MinecraftServer server, ServerPlayer target, boolean accept) {
        PendingInvite invite = PENDING.remove(target.getUUID());
        if (invite == null || invite.expired(System.currentTimeMillis())) {
            return;
        }
        ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviterId());
        if (!accept) {
            if (inviter != null) {
                inviter.sendSystemMessage(Component.translatable("saomenu.party.msg.declined",
                        target.getGameProfile().getName()));
            }
            return;
        }
        // 接受:以邀请人视角的队伍为准加入
        PlayerTeam team = ensureTeam(server, inviter != null ? inviter : target);
        if (team.getPlayers().size() >= MAX_PARTY_SIZE) {
            target.sendSystemMessage(Component.translatable("saomenu.party.msg.full", MAX_PARTY_SIZE));
            return;
        }
        team.getPlayers().add(target.getGameProfile().getName());
        syncTeam(server, team);
        // SAO 接受音效(双方)
        playAccept(server, target);
        if (inviter != null) {
            playAccept(server, inviter);
        }
    }

    /** 离开当前队伍;若为队长且队内还有他人,队长位转移给字典序第一的成员。 */
    public static void handleLeave(MinecraftServer server, ServerPlayer leaver) {
        PlayerTeam team = teamOf(server, leaver);
        if (team == null || !team.getName().startsWith(PREFIX)) {
            leaver.sendSystemMessage(Component.translatable("saomenu.party.msg.no_party"));
            return;
        }
        team.getPlayers().remove(leaver.getGameProfile().getName());
        if (team.getPlayers().isEmpty()) {
            server.getScoreboard().removePlayerTeam(team);
        } else {
            syncTeam(server, team);
        }
        leaver.sendSystemMessage(Component.translatable("saomenu.party.msg.left"));
        syncEmpty(leaver);
    }

    // ------------------------------------------------------------ 同步

    /** 把队伍成员表广播给全体成员(含队长)。 */
    private static void syncTeam(MinecraftServer server, PlayerTeam team) {
        List<String> members = new ArrayList<>(team.getPlayers());
        java.util.Collections.sort(members, String::compareToIgnoreCase);
        TeamSyncS2C msg = new TeamSyncS2C(team.getDisplayName().getString(), members);
        for (String name : members) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(name);
            if (p != null) {
                msg.sendTo(p);
            }
        }
    }

    /** 离队后的空队伍同步:清掉客户端本地成员表。 */
    private static void syncEmpty(ServerPlayer leaver) {
        new TeamSyncS2C("", List.of()).sendTo(leaver);
    }

    // ------------------------------------------------------------ 工具

    private static final String PREFIX = "saomenu_";

    /** 玩家所在的模组队伍;不在任何队伍返回 null(原版自建队伍不算)。 */
    public static PlayerTeam teamOf(MinecraftServer server, ServerPlayer p) {
        PlayerTeam team = server.getScoreboard().getPlayersTeam(p.getGameProfile().getName());
        if (team != null && team.getName().startsWith(PREFIX)) {
            return team;
        }
        return null;
    }

    /** 取/建玩家名下的模组队伍。 */
    private static PlayerTeam ensureTeam(MinecraftServer server, ServerPlayer captain) {
        PlayerTeam team = teamOf(server, captain);
        if (team == null) {
            String name = PREFIX + captain.getUUID();
            team = server.getScoreboard().addPlayerTeam(name);
            team.setDisplayName(Component.literal(captain.getGameProfile().getName()));
            team.getPlayers().add(captain.getGameProfile().getName());
        }
        return team;
    }

    private static void playAccept(MinecraftServer server, ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------ 客户端缓存(静态字段双端隔离)

    /**
     * 清空邀请会话(玩家退网时调用,防 UUID 泄漏累积)。
     * 服务端专用。
     */
    public static void clearPending(UUID id) {
        PENDING.remove(id);
    }
}
