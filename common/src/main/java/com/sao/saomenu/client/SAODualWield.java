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
    /** 副手槽哨兵:表示「这把剑已经在副手上」,不是背包下标。 */
    public static final int OFFHAND = -2;

    /**
     * 挑两把剑的槽位。
     *
     * <p>返回 {@code [主手来源, 副手来源]};槽位是背包下标 0-35,
     * 或哨兵 {@link #OFFHAND}(该手已经握着剑)。找不到两把返回 null。</p>
     *
     * <p>此前把「主手已握剑」记成 {@code inventory.selected}(0-8),
     * 与「背包扫描时跳过该下标」混在一起,当剑在副手 + 背包各一把时
     * 计数会漏一把,表现为「副手有剑却提示需要两把」。现在主手/副手
     * 各自独立判定,背包扫描只跳过真正已被占用的下标。</p>
     */
    public static int[] findTwoSwords(Player p) {
        boolean mainIsSword = isSword(p.getMainHandItem());
        boolean offIsSword = isSword(p.getOffhandItem());
        // 两手都是剑:什么都不用搬,只切模式
        if (mainIsSword && offIsSword) {
            return new int[]{OFFHAND, OFFHAND};
        }
        int selected = p.getInventory().selected;
        // 背包里所有剑的下标(排除主手当前槽——那把已经算在 mainIsSword 里)
        java.util.List<Integer> spare = new java.util.ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (mainIsSword && i == selected) {
                continue;
            }
            if (isSword(p.getInventory().getItem(i))) {
                spare.add(i);
            }
        }
        if (offIsSword) {
            // 副手已有剑:主手要么已是剑(上面已返回),要么从背包补一把
            return spare.isEmpty() ? null : new int[]{spare.get(0), OFFHAND};
        }
        if (mainIsSword) {
            // 主手已是剑:副手从背包补一把
            return spare.isEmpty() ? null : new int[]{OFFHAND, spare.get(0)};
        }
        // 两手都不是剑:背包里得有两把
        return spare.size() >= 2 ? new int[]{spare.get(0), spare.get(1)} : null;
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

    /** 待切战斗模式的剩余 tick;>0 时每 tick 递减,归零那帧执行切换。 */
    private static int pendingModeTicks;

    /**
     * 请求切战斗模式(延后 3 tick 执行)。
     *
     * <p>不能立即切:装备是服务端权威的,发包后主手的剑要等服务端回传才到位;
     * 而 Epic Fight 进战斗模式时按「当前主手武器」解析动作集,切太早会按
     * 空手解析,表现为进了战斗模式却没有持剑架势。</p>
     */
    public static void requestBattleMode() {
        pendingModeTicks = 3;
    }

    /** 客户端每 tick 调用(SAOKeybinds 挂钩):到点执行延后的模式切换。 */
    public static void tick() {
        if (pendingModeTicks > 0 && --pendingModeTicks == 0) {
            toBattleMode();
        }
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
