package com.sao.saomenu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sao.saomenu.SAOMenu;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SAO「Immortal Object」Boss 横幅:视线对准 {@code #saomenu:boss} 实体时,
 * 屏幕上沿浮现紫色六边形名牌,immortal.png / immortal_change.png 两帧交替闪烁
 * (SAO 原作同款双帧频闪)。
 *
 * <p>横幅画在 HUD 层而非世界空间——与 SAOTargetBar3D.renderLabels 同理:
 * 自建图元在 Iris/Oculus gbuffer 里会被洗成色块,HUD 层完全绕开光影管线。
 * 目标收集复用 SAOTargetBar3D 的世界扫描(lookFactor 视线门控)。</p>
 */
public final class SAOBossBanner {

    private static final ResourceLocation TEX_A = new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/immortal.png");
    private static final ResourceLocation TEX_B = new ResourceLocation(SAOMenu.MOD_ID, "textures/gui/immortal_change.png");

    /** 两帧闪烁半周期(ms):原作频闪观感 ≈ 3-4 Hz。 */
    public static final long FRAME_MS = 140;
    /** 横幅淡入/淡出时长(ms)。 */
    public static final long FADE_MS = 320;
    /** 横幅宽(屏幕高的比例)。 */
    private static final float W_FRAC = 0.34f;
    /** immortal.png 300x126 的宽高比。 */
    private static final float ASPECT = 300f / 126f;

    /** 各 Boss 实体的显示强度(淡入淡出)。 */
    private static final Map<Integer, Float> STRENGTH = new ConcurrentHashMap<>();
    /** 本帧由目标血条扫描登记的 Boss(实体 id → 显示名)。 */
    private static final Map<Integer, String> SEEN = new ConcurrentHashMap<>();

    private SAOBossBanner() {
    }

    /** 是否 Boss 实体(tag 命中)。 */
    public static boolean isBoss(LivingEntity le) {
        return le.getType().is(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ENTITY_TYPE,
                new ResourceLocation(SAOMenu.MOD_ID, "boss")));
    }

    /** SAOTargetBar3D 世界扫描循环调用:视线命中 Boss 时登记。 */
    public static void seen(LivingEntity le, float lookStrength) {
        if (!SAOConfig.showBossBanner()) {
            return;
        }
        if (lookStrength > 0.35f) {
            SEEN.put(le.getId(), le.getDisplayName().getString());
            float cur = STRENGTH.getOrDefault(le.getId(), 0f);
            STRENGTH.put(le.getId(), Math.min(1f, cur + 0.12f));
        }
    }

    /** 视线门控强度(0-1),供扫描方判断。 */
    public static float strengthOf(int entityId) {
        return STRENGTH.getOrDefault(entityId, 0f);
    }

    /** HUD 层渲染入口(SAOHud.render 调用)。 */
    public static void render(GuiGraphics g, int screenW, int screenH) {
        // 关闭开关时连带清掉残留强度,避免重新打开后旧 Boss 立刻闪一帧
        if (!SAOConfig.showBossBanner()) {
            if (!STRENGTH.isEmpty() || !SEEN.isEmpty()) {
                reset();
            }
            return;
        }
        if (SEEN.isEmpty() && STRENGTH.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        // 清理:未被本帧扫描刷新的条目淡出后移除
        List<Integer> dead = null;
        for (Map.Entry<Integer, Float> e : STRENGTH.entrySet()) {
            if (!SEEN.containsKey(e.getKey())) {
                float next = e.getValue() - 0.08f;
                if (next <= 0f) {
                    (dead == null ? dead = new ArrayList<>() : dead).add(e.getKey());
                } else {
                    e.setValue(next);
                }
            }
        }
        if (dead != null) {
            for (Integer id : dead) {
                STRENGTH.remove(id);
            }
        }
        if (STRENGTH.isEmpty()) {
            SEEN.clear();
            return;
        }

        // 取强度最高的一个 Boss 显示(多 Boss 同屏不叠罗汉)
        int bestId = -1;
        float best = 0f;
        for (Map.Entry<Integer, Float> e : STRENGTH.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                bestId = e.getKey();
            }
        }
        if (bestId < 0) {
            return;
        }
        String name = SEEN.get(bestId);
        if (name == null) {
            return;
        }
        draw(g, screenW, screenH, name, best, now);
    }

    /** 全部状态清空(换世界)。 */
    public static void reset() {
        STRENGTH.clear();
        SEEN.clear();
    }

    private static void draw(GuiGraphics g, int screenW, int screenH, String name, float strength, long now) {
        float a = Mth.clamp(strength, 0f, 1f);
        // 两帧交替
        ResourceLocation tex = (now / FRAME_MS) % 2 == 0 ? TEX_A : TEX_B;

        int w = Math.round(screenH * W_FRAC);
        int h = Math.round(w / ASPECT);
        // 上沿浮现:入场从 -h*0.4 滑到位
        int x = (screenW - w) / 2;
        int y = Math.round(-h * 0.45f + h * 0.45f * a) + Math.round(screenH * 0.035f);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, a);
        g.blit(tex, x, y, w, h, 0f, 0f, 300, 126, 300, 126);

        // Boss 名:贴图原有 "Immortal Object" 字样下方,白色描边小字
        Font f = Minecraft.getInstance().font;
        String label = f.width(name) > w - 30
                ? f.plainSubstrByWidth(name, w - 34 - f.width("…")) + "…" : name;
        int ly = y + Math.round(h * 0.72f);
        g.drawString(f, label, x + w / 2 - f.width(label) / 2, ly,
                ((Math.round(230 * a) << 24) | 0xFFFFFF), true);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
