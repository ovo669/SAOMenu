package com.sao.saomenu.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * SAO 3D 环绕目标血条:在世界空间围着生物腰身绘制一圈弧带,随生物移动实时跟随,
 * 只有玩家视线对准生物时才淡入显示。头顶另有正对相机的红色菱形标识,
 * 名称与血量文字则由 {@link #renderLabels} 画在 HUD 层。
 *
 * <h2>为什么每一层都不重叠、都不透明</h2>
 * <p>旧实现把「暗槽 / 半透明玻璃壳 / 血量光带」画成半径互相穿插的多层曲面,
 * 靠绘制顺序与半透明混合叠出观感。装了 Iris/Oculus 光影(Photon 等)的客户端上
 * 这会整条退化成一堵白墙:自建的 POSITION_COLOR 图元被光影当作不透明几何写进
 * gbuffer,顶点 alpha 与 NO_DEPTH_TEST 都被忽略,批次先后也由光影自己的 pass 决定,
 * 于是亮白玻璃壳压住了血量带,血条只剩一圈白。</p>
 *
 * <p>所以现在整条带子是一个单薄壳体:半径 {@link #BAND_THICK} 的外读数面、
 * 上下两圈环面(厚度)、内衬面。任意两个面在径向、纵向、角度三个维度上
 * 至少有一个维度完全不相交,颜色一律不透明——光影是否尊重 alpha 与绘制顺序,
 * 都不影响血量读数。</p>
 *
 * <h2>为什么弧带锚定相机而不是生物朝向</h2>
 * <p>锚定生物朝向时,绕到生物背后就只能看到壳的背面,读数面被自身挡住;
 * 旧代码为此给填充层单独开了「无深度测试」的 X 射线图元,而光影会忽略该状态。
 * 现在弧中点恒定朝向相机,读数面永远对着观察者,血量恒定自屏幕左侧起涨,
 * 不依赖任何深度技巧。</p>
 *
 * <p>视线锥判定({@link #lookFactor})、弧带几何({@link #arcSpan}、{@link #fillAngle})
 * 与淡入淡出({@link #stepAlpha})是不依赖渲染的纯函数,可单元测试。</p>
 */
public final class SAOTargetBar3D {
    // ------------------------------------------------------------ 造型与门控常量

    /** 弧带跨越的总角度(度):主弧 + 间隙 + 尾块。 */
    public static final float ARC_DEGREES = 98f;
    /** 整条弧的分段数,越多越圆滑。 */
    public static final int SEGMENTS = 28;
    /** 尾块占总弧的角度(度)。 */
    public static final float TAIL_DEGREES = 12f;
    /** 主弧与尾块之间的角度间隙(度)。 */
    public static final float TAIL_GAP_DEGREES = 6f;

    /** 环绕半径相对身宽的外扩量(格)。 */
    public static final double RADIUS_MARGIN = 0.80;
    /**
     * 弧带纵向高度:按「身宽」而非体高缩放。
     * 高个子生物(巨人/末影人)若按体高算,环会变成一条肥得不协调的腰带;
     * 身宽才决定环的周长,带厚跟身宽走比例才稳定。
     */
    public static final float BAND_HEIGHT_PER_WIDTH = 0.34f;
    /** 弧带高度下限/上限(格)。 */
    public static final float BAND_H_MIN = 0.14f;
    public static final float BAND_H_MAX = 0.32f;
    /**
     * 弧带所在高度相对体高的比例。
     *
     * <p>0.80 会把环挂到高个子生物(巨人/末影人)的脖子和头上;
     * 0.55 对常规生物落在胸腹之间,对高个子也稳定落在躯干中段。</p>
     */
    public static final float BAND_Y_FRAC = 0.55f;
    /**
     * 壳体径向厚度(格)。
     *
     * <p>整条带子只有内外两个同心面,厚度就是两者的半径差。
     * 旧实现是「暗槽 + 玻璃壳 + 光带 + 包边」四层半径互相穿插的曲面,
     * 一旦渲染器不按提交顺序出批(光影),外层就会盖住读数。</p>
     */
    public static final double BAND_THICK = 0.05;
    /** 上下亮边条各占带高的比例;中间剩余部分才是血量读数区。 */
    public static final float EDGE_FRAC = 0.17f;
    /**
     * 视线偏离生物轮廓多少弧度以内算完全显示。
     *
     * <p>门控比的是「视线与生物轮廓的夹角」而非与中心点的夹角,
     * 所以走到近处、生物占满半个屏幕时,看向它身上任意位置都算看向它。</p>
     */
    public static final float LOOK_FULL_MARGIN = 0.06f;
    /** 超出轮廓多少弧度后完全隐藏(约 18°)。 */
    public static final float LOOK_FADE_MARGIN = 0.32f;
    /** 生效距离(格)。 */
    public static final double MAX_DISTANCE = 42;
    /** 每 tick 的淡入/淡出步长。 */
    public static final float FADE_STEP = 0.18f;
    /**
     * 弧带朝向每 tick 最大转速(度)。
     *
     * <p>弧带锚定生物朝向,但怪物 AI 每 tick 都在调整身体角度,
     * 直接跟随会疯转;限速后环带只缓慢优雅地转动,小抖动被滤平。</p>
     */
    public static final float ROT_SPEED_DEG_PER_TICK = 2.5f;

    // 配色:全部不透明。alpha 只在淡入淡出期间参与,且淡入完成后恒为 FF,
    // 这样即使光影丢弃顶点 alpha,稳定态的观感也与原设计一致。
    private static final int EDGE_LIGHT = 0xFFF4FBFF;
    private static final int TRACK_DARK = 0xFF141A1E;

    private static final int KITE_FILL = 0xFFD81E3C;
    private static final int KITE_EDGE = 0xFFFF6A82;

    // 队友绿三角(参照 SAO 队友头顶标识)
    private static final int PARTY_FILL = 0xFF3ED44F;
    private static final int PARTY_EDGE = 0xFFB9FFC2;

    /**
     * 世界空间 UI 图元:纯色 + 双面 + 读写深度。
     *
     * <p>必须<strong>写</strong>深度。壳体的内衬面与外读数面角度区间相同、只差半径,
     * 不写深度时两者谁盖谁完全取决于提交顺序,而提交顺序在光影客户端上不受控——
     * 表现就是内衬的暗色整片盖住血量读数。写深度后由几何位置定胜负:
     * 离相机更近的外读数面永远在前,任何渲染器上都一致。</p>
     *
     * <p>环带、尾块、头顶菱形共用这一个类型:BufferSource 对自建类型
     * 按哈希序出批,拆成多个类型后与其他缓冲交错会把标识冲掉。</p>
     *
     * <p>延迟创建:{@code RenderType.create} 会触碰 RenderStateShard 的静态初始化,
     * 在无 GL 的单元测试环境里会抛 ExceptionInInitializerError,
     * 所以不能作为类的静态字段直接初始化。</p>
     */
    private static RenderType uiQuads;

    private static RenderType uiQuads() {
        if (uiQuads == null) {
            uiQuads = RenderType.create(
                    "saomenu_target_ring",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                            .createCompositeState(false));
        }
        return uiQuads;
    }

    /** 每个实体的当前显示强度(视线离开后平滑淡出)。 */
    private static final Map<Integer, Float> ALPHA = new HashMap<>();
    /** 每个实体的弧带当前朝向角(度,限速追赶生物朝向)。 */
    private static final Map<Integer, Float> BAND_ROT = new HashMap<>();
    /** 上一帧时间戳(算帧步长用)。 */
    private static long lastFrameAt;
    /** 本帧需要在 HUD 层补画名称/血量文字的目标。 */
    private static final List<Label> LABELS = new ArrayList<>();

    private SAOTargetBar3D() {
    }

    /** HUD 文字的一次性投影结果(世界渲染阶段算好,HUD 阶段消费)。 */
    private record Label(int entityId, float x, float y, float frac, float alpha) {
    }
    // ------------------------------------------------------------ 纯函数(可测)

    /**
     * 视线对准程度:1 表示看在生物身上,0 表示视线之外。
     *
     * @param angleToCenter 视线与「相机→生物中心」的夹角(弧度)
     * @param angularRadius 生物轮廓相对相机的角半径(弧度)
     */
    public static float lookFactor(float angleToCenter, float angularRadius) {
        float full = angularRadius + LOOK_FULL_MARGIN;
        float fade = angularRadius + LOOK_FADE_MARGIN;
        if (angleToCenter <= full) {
            return 1f;
        }
        if (angleToCenter >= fade) {
            return 0f;
        }
        return 1f - (angleToCenter - full) / (fade - full);
    }

    /**
     * 生物相对相机的角半径(弧度):体型越大、距离越近,张角越大。
     */
    public static float angularRadius(float bbWidth, float bbHeight, double distance) {
        double half = Math.max(bbWidth, bbHeight) * 0.5;
        return (float) Math.atan2(half, Math.max(0.5, distance));
    }

    /** 主弧跨越角度(弧度):总弧减去尾块与间隙。 */
    public static float arcSpan() {
        return (ARC_DEGREES - TAIL_DEGREES - TAIL_GAP_DEGREES) * Mth.DEG_TO_RAD;
    }

    /** 血量填充所占的弧角(弧度)。 */
    public static float fillAngle(float frac) {
        return arcSpan() * Mth.clamp(frac, 0f, 1f);
    }

    /** 弧带起始角(弧度):以正对相机的角为中点铺开主弧。 */
    public static float arcStart(float midAngleRad) {
        return midAngleRad - arcSpan() / 2f;
    }

    /**
     * 血量段的起始角(弧度)。
     *
     * <p>环上角 t 增大时点沿屏幕向左移动(切向 = −相机右向量),所以血量段
     * 锚定在主弧的高角端(屏幕最左),向低角方向生长——观感上就是常规血条
     * 「左端固定、向右涨」。空槽段自然落在剩下的低角区间,两段角度不重叠。</p>
     */
    /**
     * 血量段的起始角(弧度)。
     *
     * <p>环上角 t 增大时点沿逆时针移动;血量段锚定在主弧的高角端、向低角方向生长。
     * 弧带锚定生物朝向后,生物怎么转、血量都恒定从同一端起读——
     * 环跟着身体转,读数像刻在环上一样随之转动(参照 SAO 实拍)。</p>
     */
    public static float fillStart(float start, float frac) {
        return start + arcSpan() - fillAngle(frac);
    }

    /** 环绕半径:随身宽增长,并给瘦长生物一个下限。 */
    public static double ringRadius(float bbWidth) {
        return Math.max(0.42, bbWidth * 0.5 + RADIUS_MARGIN);
    }

    /**
     * 弧带纵向高度:按身宽缩放并钳制。
     *
     * <p>刻意不按体高算——巨人这种高瘦生物按体高会把环撑成肥腰带;
     * 身宽决定环的周长,带厚跟身宽走比例,任何体型都协调。</p>
     */
    public static float bandHeight(float bbWidth) {
        return Mth.clamp(bbWidth * BAND_HEIGHT_PER_WIDTH, BAND_H_MIN, BAND_H_MAX);
    }

    /** 上下亮边条的高度(格);中间剩余部分才是血量读数区。 */
    public static float edgeHeight(float bandH) {
        return Math.max(0.012f, bandH * EDGE_FRAC);
    }

    /** 淡入淡出一步:朝目标值逼近固定步长。 */
    public static float stepAlpha(float current, float target) {
        if (current < target) {
            return Math.min(target, current + FADE_STEP);
        }
        return Math.max(target, current - FADE_STEP);
    }

    /** 弧带朝向一步:朝目标角沿最短方向推进,步长被限速钳制(纯函数,可测)。 */
    public static float approachAngle(float current, float target, float maxStepDeg) {
        float diff = Mth.wrapDegrees(target - current);
        diff = Mth.clamp(diff, -maxStepDeg, maxStepDeg);
        return current + diff;
    }

    /**
     * 环带贴脸淡出系数:相机到环心的水平距离低于此值时环带开始淡出,归一化到 0~1。
     *
     * <p>镜头贴近生物时环带近侧离相机只剩零点几格,被透视放大成
     * 「漂在生物旁边的一大片弧」;此时收掉环带,只留头顶菱形与 HUD 文字。</p>
     */
    public static float proximityFade(double horizontalDist) {
        return Mth.clamp((float) ((horizontalDist - 1.6) / (2.8 - 1.6)), 0f, 1f);
    }
    // ------------------------------------------------------------ 每帧渲染入口

    /**
     * 世界渲染阶段调用:扫描附近生物,为视线锥内的目标绘制环绕血条。
     *
     * @param pose 世界渲染的 PoseStack(已应用相机旋转、未应用相机平移)
     * @param src  世界渲染的缓冲源
     */
    public static void render(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource src,
                              float partialTick) {
        LABELS.clear();
        if (!SAOConfig.showTargetBar() || mc.level == null || mc.player == null) {
            return;
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        org.joml.Vector3f lookV = cam.getLookVector();
        Vec3 look = new Vec3(lookV.x(), lookV.y(), lookV.z());
        long now = net.minecraft.Util.getMillis();
        // 帧步长(以 50ms/tick 为基准,钳到 3 tick):限速转动的推进量按帧缩放
        float dtTicks = lastFrameAt == 0L ? 1f : Mth.clamp((now - lastFrameAt) / 50f, 0f, 3f);
        lastFrameAt = now;

        java.util.Set<Integer> present = new java.util.HashSet<>();
        // 队友头顶绿三角:成员表里的在线玩家(不含自己)逐帧收集
        java.util.Set<java.util.UUID> partyIds = collectPartyIds(mc);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive()) {
                continue;
            }
            if (le instanceof Player && mc.player.isPassenger()) {
                continue;
            }
            // 队友:画绿三角,跳过敌对红菱形逻辑
            if (le instanceof Player && partyIds.contains(le.getUUID())) {
                present.add(le.getId());
                drawPartyMarker(mc, pose, src, le, camPos, partialTick, now);
                continue;
            }
            Vec3 center = le.getPosition(partialTick)
                    .add(0, le.getBbHeight() * BAND_Y_FRAC, 0);
            // 距离平方早退:视野内实体多时省掉逐实体的 sqrt/normalize/acos
            double distSq = center.distanceToSqr(camPos);
            if (distSq > MAX_DISTANCE * MAX_DISTANCE || distSq < 0.16) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            present.add(le.getId());

            Vec3 dir = center.subtract(camPos).normalize();
            float angle = (float) Math.acos(Mth.clamp(look.dot(dir), -1.0, 1.0));
            float gate = lookFactor(angle,
                    angularRadius(le.getBbWidth(), le.getBbHeight(), dist));
            // 隔墙目标不显示:相机到环带中心的视线被方块挡住时清零门控,
            // 环带/菱形/名称血量文字与 Boss 横幅共用 gate,一起随淡出收掉。
            // 只对已过视线锥的候选做 clip,避免对全场实体逐帧射线检测。
            if (gate > 0.01f && !hasLineOfSight(mc, camPos, center)) {
                gate = 0f;
            }
            // Boss 横幅登记(视线门控,HUD 层绘制)
            if (SAOBossBanner.isBoss(le)) {
                SAOBossBanner.seen(le, gate);
            }
            float a = stepAlpha(ALPHA.getOrDefault(le.getId(), 0f), gate);
            ALPHA.put(le.getId(), a);
            if (a <= 0.01f) {
                continue;
            }
            drawRing(mc, pose, src, le, center, camPos, a, now, partialTick, dtTicks);
        }
        // 离开视野的实体逐步淡出后清理
        for (Iterator<Map.Entry<Integer, Float>> it = ALPHA.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Float> en = it.next();
            if (present.contains(en.getKey())) {
                continue;
            }
            float a = stepAlpha(en.getValue(), 0f);
            if (a <= 0.01f) {
                it.remove();
                BAND_ROT.remove(en.getKey());
            } else {
                en.setValue(a);
            }
        }
    }

    /**
     * 相机到环带中心之间是否有无遮挡的视线。
     *
     * <p>用原版 {@code clip}(实体碰撞形状,不检测液体)做一次方块射线:
     * 命中点到相机的距离小于到目标的距离,说明中间有方块挡着(隔墙透视)。
     * 目标自身贴着的方块不会误挡——环带中心悬在生物腰身外侧,
     * 命中点距离只会小于「目标距离 − 身宽半径」这类明显差距才判遮挡。
     * clipContext 的 collidable=false 顺便排除碰撞形状存在但不可选中的方块
     * (如发光地衣附着的方块)造成的边缘误判。</p>
     */
    private static boolean hasLineOfSight(Minecraft mc, Vec3 camPos, Vec3 target) {
        net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(
                new net.minecraft.world.level.ClipContext(camPos, target,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        null));
        return hit.getType() == net.minecraft.world.phys.BlockHitResult.Type.MISS;
    }

    /** 当前队伍里在线(可渲染)队友的 UUID 集;无队伍为空集。 */
    private static java.util.Set<java.util.UUID> collectPartyIds(Minecraft mc) {
        java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
        if (!com.sao.saomenu.party.SAOClientPartyState.inParty()) {
            return ids;
        }
        String self = mc.player != null ? mc.player.getGameProfile().getName() : "";
        var conn = mc.getConnection();
        if (conn == null) {
            return ids;
        }
        for (String name : com.sao.saomenu.party.SAOClientPartyState.teamMembers()) {
            if (name.equals(self)) {
                continue;
            }
            var info = conn.getPlayerInfo(name);
            if (info != null) {
                ids.add(info.getProfile().getId());
            }
        }
        return ids;
    }

    /**
     * 队友头顶绿色倒三角(上边宽、下端收尖,与敌方红菱形区分),随距离轻微缩放,
     * 无视线门控——队友标识 SAO 原作中常显,越远越小即可。
     */
    private static void drawPartyMarker(Minecraft mc, PoseStack pose, MultiBufferSource src,
                                        LivingEntity mate, Vec3 camPos, float partialTick, long now) {
        Vec3 head = mate.getPosition(partialTick).add(0, mate.getBbHeight() + 0.55, 0);
        double dist = Math.max(2.0, head.distanceTo(camPos));
        // 距离缩放:近大远小,钳制在 0.5x..1.6x(基准按 10 格观感)
        float size = Mth.clamp((float) (10.0 / dist), 0.5f, 1.6f);
        float bob = SAOTargetBar.kiteBob(now) * 0.6f;

        pose.pushPose();
        pose.translate(head.x - camPos.x, head.y - camPos.y, head.z - camPos.z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        pose.scale(0.04f, -0.04f, 0.04f);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = src.getBuffer(uiQuads());

        // 倒三角:13 行,顶行最宽(halfW),向下线性收到 1px 尖端
        int kh = 13;
        int maxHalf = Math.max(3, Math.round(5 * size));
        for (int i = 0; i < kh; i++) {
            float t = i / (float) (kh - 1);
            int half = Math.max(1, Math.round(maxHalf * (1f - t)));
            float yy = i + bob;
            if (half > 1) {
                quad(vc, m, -half + 1, yy, half - 1, yy + 1, PARTY_FILL, 1f);
            }
            quad(vc, m, -half, yy, -half + 1, yy + 1, PARTY_EDGE, 1f);
            quad(vc, m, half - 1, yy, half, yy + 1, PARTY_EDGE, 1f);
        }
        pose.popPose();
    }

    /**
     * HUD 阶段调用:把本帧收集到的名称/血量文字画在屏幕上。
     *
     * <p>文字刻意不留在世界空间。世界里的 {@code font.drawInBatch} 依赖
     * 半透明字形与自身的 RenderType 排序,光影客户端上会被 gbuffer pass
     * 洗成纯色块或整体消失(用户实拍中名称与血量数字都不见了)。
     * 走 HUD 层则完全绕开光影管线,任何画质设置下都稳定可读。</p>
     */
    public static void renderLabels(GuiGraphics g, Minecraft mc) {
        if (LABELS.isEmpty() || mc.level == null) {
            return;
        }
        var font = mc.font;
        for (Label lb : LABELS) {
            Entity e = mc.level.getEntity(lb.entityId());
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            String name = le.getDisplayName().getString();
            String hp = trim(le.getHealth()) + " / " + trim(le.getMaxHealth());
            int a = Math.round(255 * Mth.clamp(lb.alpha(), 0f, 1f)) << 24;
            if (a == 0) {
                continue;
            }
            int nameX = Math.round(lb.x() - font.width(name) / 2f);
            int hpX = Math.round(lb.x() - font.width(hp) / 2f);
            // lb.y() 是菱形顶点的屏幕位置;两行文字整体码在它上方
            int hpY = Math.round(lb.y()) - font.lineHeight - 2;
            int nameY = hpY - font.lineHeight - 1;
            // 深色底衬:亮天空/雪地上白字不糊进背景
            g.fill(nameX - 2, nameY - 1, nameX + font.width(name) + 2,
                    nameY + font.lineHeight, (Math.round(150 * lb.alpha()) << 24));
            g.fill(hpX - 2, hpY - 1, hpX + font.width(hp) + 2,
                    hpY + font.lineHeight, (Math.round(150 * lb.alpha()) << 24));
            g.drawString(font, name, nameX, nameY, 0xF2F5F8 | a, true);
            g.drawString(font, hp, hpX, hpY,
                    (SAOTargetBar.hpColor(lb.frac()) & 0xFFFFFF) | a, true);
        }
    }

    /** 世界或维度切换时清空淡入状态。 */
    public static void reset() {
        ALPHA.clear();
        BAND_ROT.clear();
        lastFrameAt = 0L;
        LABELS.clear();
    }

    /** 当前正在显示血条的目标数(预览自检用)。 */
    public static int visibleCount() {
        int n = 0;
        for (float a : ALPHA.values()) {
            if (a > 0.5f) {
                n++;
            }
        }
        return n;
    }

    /** 本帧待绘制的 HUD 文字条数(预览自检用)。 */
    public static int labelCount() {
        return LABELS.size();
    }
    // ------------------------------------------------------------ 绘制

    private static void drawRing(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource src,
                                 LivingEntity le, Vec3 center, Vec3 camPos,
                                 float alpha, long now, float partialTick, float dtTicks) {
        float frac = le.getMaxHealth() <= 0f ? 0f
                : Mth.clamp(le.getHealth() / le.getMaxHealth(), 0f, 1f);
        // 菱形与文字不受贴脸淡出约束,近距离仍要能读表
        float markerAlpha = alpha;
        float ringAlpha = Math.min(alpha, proximityFade(
                Math.hypot(center.x - camPos.x, center.z - camPos.z)));
        if (ringAlpha > 0.01f) {
            drawBand(pose, src, le, center, camPos, ringAlpha, frac, now, partialTick, dtTicks);
        }
        drawMarker(mc, pose, src, le, center, camPos, frac, markerAlpha, now, partialTick);
    }

    /**
     * 环绕弧带:单薄壳体,层与层在径向/纵向/角度上互不重叠。
     *
     * <p>纵向自上而下是「亮边条 / 血量读数区 / 亮边条」三段,加上顶底两圈
     * 表现厚度的环面与一层内衬。读数区内,血量段与空槽段占互不相交的角度区间。</p>
     */
    private static void drawBand(PoseStack pose, MultiBufferSource.BufferSource src,
                                 LivingEntity le, Vec3 center, Vec3 camPos,
                                 float alpha, float frac, long now, float partialTick, float dtTicks) {
        double rIn = ringRadius(le.getBbWidth());
        double rOut = rIn + BAND_THICK;
        float bandH = bandHeight(le.getBbWidth());
        float flash = SAOTargetBar.flashStrength(now, SAOCombatHud.hurtAt(le.getId()));

        float span = arcSpan();
        // 弧带锚定生物身体朝向,但转速被限速阻尼:怪物 AI 每 tick 调整朝向,
        // 直接跟随会疯转;限速后环带只缓慢转动,小幅抖动被滤平。
        // 中点角 = 90° − bodyRot(MC 朝向向量 (sin yRot, cos yRot) 与弧角方向重合)
        float targetRot = 90f - Mth.rotLerp(partialTick, le.yBodyRotO, le.yBodyRot);
        float bandRot = approachAngle(
                BAND_ROT.getOrDefault(le.getId(), targetRot), targetRot,
                ROT_SPEED_DEG_PER_TICK * dtTicks);
        BAND_ROT.put(le.getId(), bandRot);
        float start = arcStart((float) Math.toRadians(bandRot));
        float tailStart = start + span + TAIL_GAP_DEGREES * Mth.DEG_TO_RAD;
        float tailSpan = TAIL_DEGREES * Mth.DEG_TO_RAD;

        float y1 = bandH / 2f;
        float y0 = -y1;
        float edge = edgeHeight(bandH);
        float yr1 = y1 - edge;
        float yr0 = y0 + edge;

        int hp = SAOTargetBar.hpColor(frac) | 0xFF000000;
        if (flash > 0f) {
            hp = SAOTargetBar.lerpColor(hp, 0xFFFFFFFF, flash * 0.42f);
        }
        float fillSpan = fillAngle(frac);
        float fillFrom = fillStart(start, frac);

        pose.pushPose();
        pose.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = src.getBuffer(uiQuads());

        for (int pass = 0; pass < 2; pass++) {
            boolean tail = pass == 1;
            float from = tail ? tailStart : start;
            float len = tail ? tailSpan : span;
            // 顺序也是一道保险:内衬与顶底环面先画,读数面最后画。
            // 内衬(rIn)与读数面(rOut)只差半径,深度写入负责让更近的面胜出;
            // 万一渲染器忽略深度,后画的读数面同样在上。
            // 内衬面双面读数:与外表面完全相同的角度布局——环带是一个整体,
            // 两个面花纹一致,斜着同时看到两个面时色带才衔接得上;
            // 若按视角镜像(内外锚点对调),三视角下色带会看起来左右错位。
            arcBand(vc, m, rIn, rIn, y0, yr0, from, len, EDGE_LIGHT, alpha);
            arcBand(vc, m, rIn, rIn, yr1, y1, from, len, EDGE_LIGHT, alpha);
            arcBand(vc, m, rIn, rOut, y1, y1, from, len, EDGE_LIGHT, alpha);
            arcBand(vc, m, rIn, rOut, y0, y0, from, len, EDGE_LIGHT, alpha);
            // 内衬读数区:与外表面同角度区间
            if (tail) {
                arcBand(vc, m, rIn, rIn, yr0, yr1, from, len,
                        frac > 0.98f ? hp : TRACK_DARK, alpha);
            } else {
                if (fillSpan < len) {
                    arcBand(vc, m, rIn, rIn, yr0, yr1, from, len - fillSpan,
                            TRACK_DARK, alpha);
                }
                if (fillSpan > 0f) {
                    arcBand(vc, m, rIn, rIn, yr0, yr1, fillFrom, fillSpan, hp, alpha);
                }
            }
            // 外表面上下亮边条:与读数区纵向不重叠
            arcBand(vc, m, rOut, rOut, yr1, y1, from, len, EDGE_LIGHT, alpha);
            arcBand(vc, m, rOut, rOut, y0, yr0, from, len, EDGE_LIGHT, alpha);
            // 读数区:空槽段与血量段角度互斥,谁都盖不住谁
            if (tail) {
                arcBand(vc, m, rOut, rOut, yr0, yr1, from, len,
                        frac > 0.98f ? hp : TRACK_DARK, alpha);
            } else {
                if (fillSpan < len) {
                    arcBand(vc, m, rOut, rOut, yr0, yr1, from, len - fillSpan,
                            TRACK_DARK, alpha);
                }
                if (fillSpan > 0f) {
                    arcBand(vc, m, rOut, rOut, yr0, yr1, fillFrom, fillSpan, hp, alpha);
                }
            }
        }
        pose.popPose();
    }

    /**
     * 头顶红色菱形 + 记录文字投影位置。
     *
     * <p>菱形是纯色块,走世界空间没问题;文字改由 {@link #renderLabels} 在 HUD 层绘制,
     * 这里只把头顶上方的屏幕坐标算出来存进 {@link #LABELS}。</p>
     */
    private static void drawMarker(Minecraft mc, PoseStack pose, MultiBufferSource src,
                                   LivingEntity le, Vec3 center, Vec3 camPos,
                                   float frac, float alpha, long now, float partialTick) {
        if (alpha <= 0.01f) {
            return;
        }
        Vec3 head = le.getPosition(partialTick).add(0, le.getBbHeight() + 0.62, 0);
        float bob = SAOTargetBar.kiteBob(now) * 0.8f;

        pose.pushPose();
        pose.translate(head.x - camPos.x, head.y - camPos.y, head.z - camPos.z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        // 世界单位 → 像素单位:公告板内部按 1/25 格每像素作图。
        // Y 取负:像素坐标系 +y 向下,与世界 +y 相反
        pose.scale(0.04f, -0.04f, 0.04f);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = src.getBuffer(uiQuads());

        // 菱形:上段迅速张开、下段收成长尖(与 2D 版同一轮廓函数)
        int kh = 13;
        int maxHalf = 4;
        for (int i = 0; i < kh; i++) {
            float t = i / (float) (kh - 1);
            int half = Math.max(1, Math.round(maxHalf * SAOTargetBar.kiteHalfWidth(t)));
            float yy = i + bob;
            // 中段填充与两侧亮边横向不重叠
            if (half > 1) {
                quad(vc, m, -half + 1, yy, half - 1, yy + 1, KITE_FILL, alpha);
            }
            quad(vc, m, -half, yy, -half + 1, yy + 1, KITE_EDGE, alpha);
            quad(vc, m, half - 1, yy, half, yy + 1, KITE_EDGE, alpha);
        }
        pose.popPose();

        float[] sp = project(mc, head);
        if (sp != null) {
            LABELS.add(new Label(le.getId(), sp[0], sp[1], frac, alpha));
        }
    }

    /**
     * 世界坐标 → GUI 屏幕坐标;身后/屏幕外返回 null。
     *
     * <p>视图旋转必须与 MC 世界渲染一致:GameRenderer 用
     * {@code XP.rotationDegrees(camera.getXRot())} 再
     * {@code YP.rotationDegrees(camera.getYRot() + 180f)} 级联构建。
     * {@code Axis.rotation} 收的是弧度,必须显式换算,否则会放大 57 倍。</p>
     */
    private static float[] project(Minecraft mc, Vec3 pos) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = pos.subtract(cam.getPosition());
        float deg2rad = (float) (Math.PI / 180.0);
        Matrix4f view = new Matrix4f()
                .rotate(com.mojang.math.Axis.XP.rotation(cam.getXRot() * deg2rad))
                .rotate(com.mojang.math.Axis.YP.rotation((cam.getYRot() + 180f) * deg2rad));
        Matrix4f mvp = new Matrix4f(mc.gameRenderer.getProjectionMatrix(mc.options.fov().get()))
                .mul(view);
        org.joml.Vector4f v = new org.joml.Vector4f(
                (float) rel.x, (float) rel.y, (float) rel.z, 1f).mul(mvp);
        if (v.w <= 0.001f) {
            return null;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        return new float[]{
                (v.x / v.w * 0.5f + 0.5f) * w,
                (0.5f - v.y / v.w * 0.5f) * h};
    }

    /**
     * 沿圆周铺一段带子:每段一个四边形。
     *
     * <p>{@code r0 == r1} 得到竖直面,{@code yLo == yHi} 得到水平环面,
     * 两者都用得到,所以半径与高度都各留两个参数。</p>
     *
     * @param r0   底边(yLo 侧)半径
     * @param r1   顶边(yHi 侧)半径
     * @param from 起始角(弧度)
     * @param span 跨越角(弧度)
     */
    private static void arcBand(VertexConsumer vc, Matrix4f m,
                                double r0, double r1, float yLo, float yHi,
                                float from, float span, int argb, float alpha) {
        if (span <= 0f) {
            return;
        }
        int segs = Math.max(1, Math.round(SEGMENTS * span / (ARC_DEGREES * Mth.DEG_TO_RAD)));
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0f, 1f));
        if (a <= 0) {
            return;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        for (int i = 0; i < segs; i++) {
            float t0 = from + span * i / segs;
            float t1 = from + span * (i + 1) / segs;
            float c0 = Mth.cos(t0);
            float s0 = Mth.sin(t0);
            float c1 = Mth.cos(t1);
            float s1 = Mth.sin(t1);
            vertex(vc, m, (float) (r0 * c0), yLo, (float) (r0 * s0), r, g, b, a);
            vertex(vc, m, (float) (r0 * c1), yLo, (float) (r0 * s1), r, g, b, a);
            vertex(vc, m, (float) (r1 * c1), yHi, (float) (r1 * s1), r, g, b, a);
            vertex(vc, m, (float) (r1 * c0), yHi, (float) (r1 * s0), r, g, b, a);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float x1, float y1, int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0f, 1f));
        if (a <= 0) {
            return;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        vertex(vc, m, x0, y0, 0f, r, g, b, a);
        vertex(vc, m, x0, y1, 0f, r, g, b, a);
        vertex(vc, m, x1, y1, 0f, r, g, b, a);
        vertex(vc, m, x1, y0, 0f, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                               int r, int g, int b, int a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).endVertex();
    }

    private static String trim(float v) {
        float r = Math.round(v * 10f) / 10f;
        return (r == Math.rint(r)) ? String.valueOf((int) r) : String.valueOf(r);
    }
}
