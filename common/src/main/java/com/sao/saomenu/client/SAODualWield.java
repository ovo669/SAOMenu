package com.sao.saomenu.client;

import com.sao.saomenu.SAOMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;

/**
 * 「二刀流」技能:主手 + 副手各装一把剑,并切换到史诗战斗(Epic Fight)的战斗模式。
 *
 * <p>装备走服务端权威路径({@code SwapToOffhandC2S}),客户端只负责挑选槽位;
 * 战斗模式切换是纯客户端行为——按下 Epic Fight 自己的 {@code SWITCH_MODE}
 * 按键映射,由它的按键处理逻辑走自身网络同步,本模组不直接碰它的能力对象。</p>
 *
 * <p>Epic Fight 通过反射按需接入:没装该模组时二刀流仍会装备双剑,
 * 只是不切换战斗模式(不抛异常、不产生硬依赖)。</p>
 */
public final class SAODualWield {

    /** Epic Fight 的能力入口与客户端玩家补丁类。 */
    private static final String EF_CAPS = "yesman.epicfight.world.capabilities.EpicFightCapabilities";
    private static final String EF_LOCAL_PATCH = "yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch";

    private static boolean efResolved;
    private static boolean efPresent;

    private SAODualWield() {
    }

    /** Epic Fight 是否可用(能力类能取到)。 */
    public static boolean epicFightPresent() {
        resolve();
        return efPresent;
    }

    private static void resolve() {
        if (efResolved) {
            return;
        }
        efResolved = true;
        try {
            Class.forName(EF_CAPS);
            Class.forName(EF_LOCAL_PATCH);
            efPresent = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            SAOMenu.LOGGER.info("[SAOMenu] 未检测到史诗战斗,二刀流只装备双剑: {}", e.toString());
        }
    }

    /**
     * 在背包里挑两把剑的槽位。
     *
     * @return {@code [主手槽, 副手槽]};找不到两把剑时返回 null
     */
    public static int[] findTwoSwords(Player p) {
        int first = -1;
        int second = -1;
        boolean offhandIsSword = isSword(p.getOffhandItem());
        // 已经握在主手/副手的剑优先保留,避免无谓换手
        if (isSword(p.getMainHandItem())) {
            first = p.getInventory().selected;
        }
        // 副手槽(40)用 -2 标记;服务端 handleDualWield 认得这个哨兵值
        if (offhandIsSword) {
            if (first < 0) {
                first = -2;
            } else {
                second = -2;
            }
        }
        for (int i = 0; i < 36; i++) {
            if (i == first) {
                continue;
            }
            if (isSword(p.getInventory().getItem(i))) {
                if (first < 0) {
                    first = i;
                } else {
                    second = i;
                    break;
                }
            }
        }
        return (first >= 0 && second >= 0) ? new int[]{first, second} : null;
    }

    /** 是否可作为二刀流的一把「剑」(原版剑 + 其他模组的剑类工具)。 */
    public static boolean isSword(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof SwordItem) {
            return true;
        }
        // 其他模组的剑往往只继承 TieredItem;按注册名兜底识别
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).getPath();
        return stack.getItem() instanceof TieredItem
                && (id.contains("sword") || id.contains("blade") || id.contains("katana"));
    }

    /**
     * 切到史诗战斗的战斗模式。
     *
     * <p>正确入口是 {@code EpicFightCapabilities.getEntityPatch(player, LocalPlayerPatch.class)
     * .toEpicFightMode(true)}——它内部会自己发 {@code CPChangePlayerMode} 包同步服务端,
     * 并处理相机/技能 UI 的连带切换。曾试过硬按它的 SWITCH_MODE 按键映射,
     * 但 Epic Fight 的按键走自家 InputManager 事件总线,KeyMapping.setDown
     * 不会触发,表现为点了没反应。</p>
     */
    public static void toBattleMode() {
        resolve();
        if (!efPresent) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        try {
            Class<?> caps = Class.forName(EF_CAPS);
            Class<?> patchCls = Class.forName(EF_LOCAL_PATCH);
            Object patch = caps.getMethod("getEntityPatch",
                    net.minecraft.world.entity.Entity.class, Class.class)
                    .invoke(null, mc.player, patchCls);
            if (patch != null) {
                patchCls.getMethod("toEpicFightMode", boolean.class).invoke(patch, true);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            SAOMenu.LOGGER.warn("[SAOMenu] 切换史诗战斗战斗模式失败: {}", e.toString());
        }
    }
}
