package com.sao.saomenu.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * SAO 战斗 HUD:准星目标血条、伤害数字、击杀/获得经验通知、升级金色光环。
 *
 * <p>全部基于客户端每帧检测(血量差/经验差),不依赖平台事件钩子,
 * Forge/Fabric 双平台零差异。</p>
 */
public final class SAOCombatHud {

    private static final long NUMBER_MS = 900;
    private static final long RING_MS = 700;
    private static final double TRACK_RANGE = 40; // 血量差检测范围(方块)

    private static final Map<Integer, Float> LAST_HP = new HashMap<>();
    /** 每个实体最近一次掉血的时刻,驱动目标血条的受击闪白。 */
    private static final Map<Integer, Long> HURT_AT = new HashMap<>();
    private static final List<DamageNumber> NUMBERS = new ArrayList<>();

    private static int lastXp = -1;
    private static int lastLevel = -1;
    private static long ringAt = Long.MIN_VALUE;

    private SAOCombatHud() {
    }

    /** 每帧入口(SAOHud.render 调用):先检测事件,再绘制伤害数字。 */
    public static void render(GuiGraphics g, Minecraft mc, int w, int h, float alpha) {
        if (mc.player == null) {
            return;
        }
        detect(mc);
        renderNumbers(g, mc, w, h, alpha);
    }

    /** 供 SAOTargetBar3D 读取某实体最近一次掉血的时刻(0 表示从未受击)。 */
    public static long hurtAt(int entityId) {
        return HURT_AT.getOrDefault(entityId, 0L);
    }

    // ---------------------------------------------------------------- 检测

    /** 血量差/击杀/经验/升级检测 + 过期数字清理 + 升级光环粒子。 */
    private static void detect(Minecraft mc) {
        long now = Util.getMillis();
        if (mc.level == null) {
            LAST_HP.clear();
            HURT_AT.clear();
            NUMBERS.clear();
            return;
        }
        Player p = mc.player;
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Map<Integer, Float> seen = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || le == p) {
                continue;
            }
            if (le.distanceToSqr(camPos) > TRACK_RANGE * TRACK_RANGE) {
                continue;
            }
            int id = le.getId();
            seen.put(id, le.getHealth());
            Float prev = LAST_HP.get(id);
            if (prev != null) {
                float dmg = prev - le.getHealth();
                if (dmg >= 0.5f) {
                    // 玩家打出的伤害橙色,其余(环境/他人)红色
                    boolean byPlayer = le.getLastHurtByMob() == p;
                    int color = byPlayer ? 0xFFFF8C0A : 0xFFFF4040;
                    NUMBERS.add(new DamageNumber(id, "-" + trim(dmg), color, now,
                            (float) (Math.random() * 12 - 6)));
                    HURT_AT.put(id, now);
                }
            }
        }
        // 击杀检测:上一帧还有血、这一帧从跟踪集消失且是被玩家打死的
        for (Iterator<Map.Entry<Integer, Float>> it = LAST_HP.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Float> en = it.next();
            if (seen.containsKey(en.getKey())) {
                continue;
            }
            Entity e = mc.level.getEntity(en.getKey());
            if (e instanceof LivingEntity le && !le.isAlive() && en.getValue() > 0f
                    && le.getLastHurtByMob() == p && SAOConfig.saoToasts()) {
                SAONotification.push(SAOHud.tr("saomenu.notify.kill", le.getDisplayName().getString()), "");
            }
            it.remove();
        }
        LAST_HP.clear();
        LAST_HP.putAll(seen);
        // 闪白记录跟着跟踪集走,实体离开视野即清理
        HURT_AT.keySet().retainAll(seen.keySet());

        // 经验与升级
        int xp = p.totalExperience;
        if (lastXp >= 0 && xp > lastXp && SAOConfig.saoToasts()) {
            SAONotification.push(SAOHud.tr("saomenu.notify.exp", xp - lastXp), "");
        }
        lastXp = xp;
        if (lastLevel >= 0 && p.experienceLevel > lastLevel) {
            ringAt = now;
        }
        lastLevel = p.experienceLevel;

        // 过期数字
        NUMBERS.removeIf(n -> now - n.at > NUMBER_MS);

        // 升级金色光环:绕玩家一圈上升的光点
        if (ringAt != Long.MIN_VALUE && now - ringAt < RING_MS && mc.level != null) {
            float t = (now - ringAt) / (float) RING_MS;
            double radius = 0.7 + t * 0.9;
            for (int i = 0; i < 12; i++) {
                double ang = i * Math.PI * 2 / 12 + t * 1.6;
                mc.particleEngine.createParticle(ParticleTypes.GLOW,
                        p.getX() + Math.cos(ang) * radius,
                        p.getY() + 0.35 + t * 1.7,
                        p.getZ() + Math.sin(ang) * radius,
                        0, 0.12, 0);
            }
        }
    }

    // ---------------------------------------------------------------- 伤害数字

    /** 伤害数字:世界坐标上浮 + 淡出,玩家打出的橙色、其余红色。 */
    private static void renderNumbers(GuiGraphics g, Minecraft mc, int w, int h, float alpha) {
        if (!SAOConfig.showDamageNumbers() || NUMBERS.isEmpty() || mc.level == null) {
            return;
        }
        long now = Util.getMillis();
        for (Iterator<DamageNumber> it = NUMBERS.iterator(); it.hasNext(); ) {
            DamageNumber n = it.next();
            Entity e = mc.level.getEntity(n.entityId);
            if (e == null) {
                continue;
            }
            float p = Mth.clamp((now - n.at) / (float) NUMBER_MS, 0f, 1f);
            float[] sp = project(mc, e.getPosition(1f)
                    .add(n.offset * 0.05, 0.9 + p * 0.6, 0), w, h);
            if (sp == null) {
                continue;
            }
            g.drawString(mc.font, n.text,
                    Math.round(sp[0] - mc.font.width(n.text) / 2f),
                    Math.round(sp[1]),
                    mulAlpha(n.color, alpha * (1f - p * p)), false);
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 世界坐标 -> GUI 屏幕坐标(透视投影);屏幕外/身后返回 null。
     *
     * <p>视图旋转必须与 MC 世界渲染一致:GameRenderer 渲染世界时用
     * {@code poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()))} 再
     * {@code mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180f))} 级联构建。
     * 注意 Axis.rotation 接收的是弧度,必须显式把角度换算成弧度
     * (MC 内部走 rotationDegrees,直接传度数会放大 57 倍导致血条乱飞)。</p>
     */
    private static float[] project(Minecraft mc, Vec3 pos, int w, int h) {
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = pos.subtract(cam.getPosition());
        float deg2rad = (float) (Math.PI / 180.0);
        Matrix4f view = new Matrix4f()
                .rotate(com.mojang.math.Axis.XP.rotation(cam.getXRot() * deg2rad))
                .rotate(com.mojang.math.Axis.YP.rotation((cam.getYRot() + 180.0f) * deg2rad));
        Matrix4f m = new Matrix4f(mc.gameRenderer.getProjectionMatrix(mc.options.fov().get()))
                .mul(view);
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f).mul(m);
        if (v.w <= 0.001f) {
            return null;
        }
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        return new float[]{(nx * 0.5f + 0.5f) * w, (0.5f - ny * 0.5f) * h};
    }

    private static String trim(float v) {
        float r = Math.round(v * 10f) / 10f;
        return (r == Math.rint(r)) ? String.valueOf((int) r) : String.valueOf(r);
    }

    private static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        return (Math.round(a * Mth.clamp(factor, 0f, 1f)) << 24) | rgb;
    }

    private record DamageNumber(int entityId, String text, int color, long at, float offset) {
    }
}
