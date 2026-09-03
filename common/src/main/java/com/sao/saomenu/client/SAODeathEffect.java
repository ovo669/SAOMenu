package com.sao.saomenu.client;

import com.sao.saomenu.SAOMenuPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SAO 死亡碎裂特效:生物死亡瞬间整体炸成蓝色多边形碎片。
 *
 * <p>编排分两级——死亡当帧在包围盒体内爆散一批碎片 + 中心闪光,
 * 随后几 tick 继续补少量残碎,得到动漫里「先炸开、再零星飘落」的层次。</p>
 *
 * <p>{@link #shardCount}、{@link #burstVelocity} 等是不依赖 Minecraft 的纯函数,
 * 可单元测试;{@link #clientTick} 负责检测死亡并生成粒子。</p>
 */
public final class SAODeathEffect {

    /** 每个死亡生物的碎片总量下限/上限(体型越大越多)。 */
    public static final int MIN_SHARDS = 26;
    public static final int MAX_SHARDS = 190;
    /** 爆散后继续补碎片的 tick 数。 */
    public static final int TRAIL_TICKS = 4;
    /** 检测范围(方块):超出这个距离的死亡不生成粒子,避免远处浪费。 */
    private static final double RANGE = 48;

    /** 已经放过特效的实体 id,防止死亡动画期间重复触发。 */
    private static final Set<Integer> DONE = new HashSet<>();
    /** 正在补残碎的实体:id → 剩余 tick。 */
    private static final Map<Integer, Trail> TRAILS = new HashMap<>();

    private SAODeathEffect() {
    }

    /** 补碎阶段的记录:位置与体型在实体移除后仍需可用,故拷贝一份。 */
    private record Trail(double x, double y, double z, float width, float height,
                         int shards, int ticksLeft) {
    }

    // ------------------------------------------------------------ 纯函数(可测)

    /**
     * 碎片数量:按包围盒体积开方缩放,再钳制到 [MIN_SHARDS, MAX_SHARDS]。
     *
     * <p>用体积的平方根而非体积本身,是为了让末影龙这种巨物不会一次生成上千粒子。</p>
     */
    public static int shardCount(float width, float height, float density) {
        float volume = Math.max(0.02f, width * width * height);
        int n = Math.round(46f * (float) Math.sqrt(volume) * Mth.clamp(density, 0.1f, 3f));
        return Mth.clamp(n, MIN_SHARDS, MAX_SHARDS);
    }

    /**
     * 爆散初速度:水平朝外扩散、竖直略微上抛。
     *
     * @param horizontalAngle 该碎片的水平角(弧度)
     * @param radialFrac      碎片距中轴的归一化距离 0..1(越外圈飞得越快)
     * @param speed           基础速度
     * @return 长度 3 的数组 {vx, vy, vz}
     */
    public static double[] burstVelocity(double horizontalAngle, float radialFrac, float speed) {
        float r = Mth.clamp(radialFrac, 0f, 1f);
        double h = speed * (0.35 + 0.65 * r);
        return new double[]{
                Math.cos(horizontalAngle) * h,
                speed * (0.42 + 0.30 * (1f - r)),
                Math.sin(horizontalAngle) * h,
        };
    }

    /** 闪光数量:体型越大闪光越多,始终 1..5 个。 */
    public static int glowCount(float width, float height) {
        return Mth.clamp(1 + Math.round((width + height) * 0.55f), 1, 5);
    }

    /** 补碎阶段每 tick 的碎片数:总量的 1/6,至少 2 个。 */
    public static int trailShardsPerTick(int totalShards) {
        return Math.max(2, totalShards / 6);
    }

    // ------------------------------------------------------------ 检测与生成

    /**
     * 每客户端 tick 调用:扫描附近生物,在 {@code deathTime} 刚起跳时触发碎裂。
     *
     * <p>用 deathTime==1 而不是 isAlive()==false,是因为原版死亡动画持续 20 tick,
     * 实体在这期间仍在 level 里;deathTime 从 1 开始递增,正好是「刚死」的那一帧。</p>
     */
    public static void clientTick(Minecraft mc) {
        if (mc.level == null) {
            DONE.clear();
            TRAILS.clear();
            return;
        }
        if (!SAOConfig.deathShatter()) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Set<Integer> alive = new HashSet<>();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            alive.add(le.getId());
            if (le.distanceToSqr(cam) > RANGE * RANGE) {
                continue;
            }
            // 玩家自己死亡不炸(第一人称视角里只会糊满屏幕)
            if (le instanceof Player) {
                continue;
            }
            if (le.deathTime <= 0 || DONE.contains(le.getId())) {
                continue;
            }
            DONE.add(le.getId());
            burst(mc, le);
        }

        // 补残碎
        for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Trail> en = it.next();
            Trail t = en.getValue();
            spawnShards(mc, t.x(), t.y(), t.z(), t.width(), t.height(),
                    trailShardsPerTick(t.shards()), 0.22f);
            if (t.ticksLeft() <= 1) {
                it.remove();
            } else {
                en.setValue(new Trail(t.x(), t.y(), t.z(), t.width(), t.height(),
                        t.shards(), t.ticksLeft() - 1));
            }
        }

        // 实体已彻底移除后清理去重集合,避免长时间游戏累积
        DONE.retainAll(alive);
    }

    /** 死亡当帧:整体爆散 + 中心闪光 + 音效,并登记补碎。 */
    private static void burst(Minecraft mc, LivingEntity le) {
        float w = Math.max(0.2f, le.getBbWidth());
        float h = Math.max(0.2f, le.getBbHeight());
        double cx = le.getX();
        double cy = le.getY() + h * 0.5;
        double cz = le.getZ();
        int shards = shardCount(w, h, SAOConfig.deathShatterDensity());

        spawnShards(mc, cx, cy, cz, w, h, shards, 0.34f);

        // 中心闪光
        int glows = glowCount(w, h);
        for (int i = 0; i < glows; i++) {
            double ox = (mc.level.random.nextDouble() - 0.5) * w * 0.5;
            double oy = (mc.level.random.nextDouble() - 0.5) * h * 0.5;
            double oz = (mc.level.random.nextDouble() - 0.5) * w * 0.5;
            mc.particleEngine.createParticle(SAOMenuPlatform.glowParticle(),
                    cx + ox, cy + oy, cz + oz, 0, 0.02, 0);
        }

        if (SAOConfig.sounds()) {
            try {
                // 位置化播放:碎裂声从尸体处传来,音高上调贴近玻璃碎裂
                mc.getSoundManager().play(new SimpleSoundInstance(
                        SAOMenuPlatform.alertSound(),
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        0.55f, 1.5f + mc.level.random.nextFloat() * 0.25f,
                        net.minecraft.util.RandomSource.create(), cx, cy, cz));
            } catch (Throwable ignored) {
                // 音效缺失不影响特效
            }
        }

        TRAILS.put(le.getId(), new Trail(cx, cy, cz, w, h, shards, TRAIL_TICKS));
    }

    /** 在包围盒体内均匀撒点并按 {@link #burstVelocity} 赋初速。 */
    private static void spawnShards(Minecraft mc, double cx, double cy, double cz,
                                    float w, float h, int count, float speed) {
        if (mc.level == null) {
            return;
        }
        var rnd = mc.level.random;
        for (int i = 0; i < count; i++) {
            // 圆柱体内均匀采样:sqrt 保证面积均匀而非向中心聚集
            double ang = rnd.nextDouble() * Math.PI * 2;
            float radial = (float) Math.sqrt(rnd.nextDouble());
            double rr = radial * w * 0.5;
            double px = cx + Math.cos(ang) * rr;
            double pz = cz + Math.sin(ang) * rr;
            double py = cy + (rnd.nextDouble() - 0.5) * h;

            double[] v = burstVelocity(ang, radial, speed);
            // 直接走 particleEngine:ClientLevel.addParticle 会按「粒子」画质选项
            // 抽稀甚至整批丢弃,碎裂特效需要确定性生成
            mc.particleEngine.createParticle(SAOMenuPlatform.shardParticle(),
                    px, py, pz, v[0], v[1], v[2]);
        }
    }

    /** 世界切换时清空状态(由 SAOWelcome.clientTick 的入世检测顺带调用)。 */
    public static void reset() {
        DONE.clear();
        TRAILS.clear();
    }

    /** 当前正在补碎的实体数(预览自检与单测用)。 */
    public static int pendingTrails() {
        return TRAILS.size();
    }
}
