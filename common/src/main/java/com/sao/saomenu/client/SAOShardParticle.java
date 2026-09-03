package com.sao.saomenu.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;

/**
 * SAO 死亡碎裂碎片:随机取一枚三角/条状碎片贴图,自旋 + 先飘后落 + 末段淡出。
 *
 * <p>自发光渲染({@link #getLightColor} 恒为满亮),在夜里与洞穴中依然是
 * 动漫里那种亮蓝玻璃碴的观感。光晕形态({@code glow=true})改用叠加混合,
 * 用于爆散瞬间的中心闪光。</p>
 */
public class SAOShardParticle extends TextureSheetParticle {

    /** 叠加混合的粒子渲染类型:光晕互相叠亮而不是相互遮挡。 */
    public static final ParticleRenderType ADDITIVE_SHEET = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            textureManager.bindForSetup(TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "saomenu:additive";
        }
    };

    private final boolean glow;
    private final float spin;
    /** 碎片起始尺寸,淡出时按比例收缩。 */
    private final float baseSize;

    protected SAOShardParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz,
                               SpriteSet sprites, boolean glow) {
        super(level, x, y, z, 0, 0, 0);
        this.glow = glow;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        pickSprite(sprites);

        if (glow) {
            this.lifetime = 8 + this.random.nextInt(5);
            this.baseSize = 0.42f + this.random.nextFloat() * 0.34f;
            this.gravity = 0f;
            this.friction = 0.86f;
            this.spin = 0f;
            this.hasPhysics = false;
        } else {
            this.lifetime = 26 + this.random.nextInt(22);
            // 碎片尺寸参照动漫:单枚只有几厘米,靠数量而非体积堆出「碎开」的观感
            this.baseSize = 0.045f + this.random.nextFloat() * 0.055f;
            // 先几乎悬浮、后段才明显下落,靠 tick 里逐步加大 gravity 实现
            this.gravity = 0f;
            this.friction = 0.94f;
            this.spin = (this.random.nextFloat() - 0.5f) * 0.9f;
            this.hasPhysics = true;
        }
        this.quadSize = this.baseSize;
        this.roll = this.random.nextFloat() * Mth.PI * 2f;
        this.oRoll = this.roll;
        // 贴图自带 SAO 蓝,主题色只做轻微偏移,避免把碎片染死
        this.rCol = 1f;
        this.gCol = 1f;
        this.bCol = 1f;
        this.alpha = 1f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return glow ? ADDITIVE_SHEET : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 自发光:不受方块光照影响。 */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    // ------------------------------------------------------------ UV 收进(防贴图集渗色)

    /** 每边收进比例(占图块宽高):隔断双线性过滤/mipmap 对图集相邻图块的采样。 */
    private static final float UV_INSET = 0.0625f;

    /** 关键:收进 UV 防渗色。自旋粒子在原版渲染里是轴对齐 UV 采样,pickSprite
     * 给的 UV 贴着图块边缘,双线性过滤 + mipmap 会把图集里相邻贴图(原版粒子/
     * 物品图块)的颜色渗到碎片上,表现为碎片上出现彩格/方块刻纹等错误贴图。 */
    @Override
    public float getU0() {
        return Mth.lerp(UV_INSET, super.getU0(), super.getU1());
    }

    @Override
    public float getU1() {
        return Mth.lerp(UV_INSET, super.getU1(), super.getU0());
    }

    @Override
    public float getV0() {
        return Mth.lerp(UV_INSET, super.getV0(), super.getV1());
    }

    @Override
    public float getV1() {
        return Mth.lerp(UV_INSET, super.getV1(), super.getV0());
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (this.removed) {
            return;
        }
        float t = this.age / (float) this.lifetime;
        if (glow) {
            // 闪光:迅速放大再淡掉
            this.quadSize = baseSize * (0.55f + 1.15f * easeOutCubic(t));
            this.alpha = 1f - t * t;
            return;
        }
        this.roll += spin * (1f - t * 0.55f);
        // 悬浮 → 坠落:重力在生命周期前 45% 内线性拉满
        this.gravity = 0.62f * Mth.clamp(t / 0.45f, 0f, 1f);
        // 末段 35% 收缩并淡出,像碎片彻底消散
        if (t > 0.65f) {
            float f = (t - 0.65f) / 0.35f;
            this.alpha = 1f - f;
            this.quadSize = baseSize * (1f - 0.45f * f);
        }
    }

    private static float easeOutCubic(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    /** 碎片:随机三角/条状贴图。 */
    public static class ShardProvider implements net.minecraft.client.particle.ParticleProvider<
            net.minecraft.core.particles.SimpleParticleType> {

        private final SpriteSet sprites;

        public ShardProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(net.minecraft.core.particles.SimpleParticleType type,
                                       ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SAOShardParticle(level, x, y, z, vx, vy, vz, sprites, false);
        }
    }

    /** 中心闪光:叠加混合的柔和光晕。 */
    public static class GlowProvider implements net.minecraft.client.particle.ParticleProvider<
            net.minecraft.core.particles.SimpleParticleType> {

        private final SpriteSet sprites;

        public GlowProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(net.minecraft.core.particles.SimpleParticleType type,
                                       ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SAOShardParticle(level, x, y, z, vx, vy, vz, sprites, true);
        }
    }
}
