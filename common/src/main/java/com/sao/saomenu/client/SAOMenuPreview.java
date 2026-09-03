package com.sao.saomenu.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.sao.saomenu.SAOMenu;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 开发期可视化校验:用 {@code -Dsaomenu.preview=<输出目录>} 启动客户端时,
 * 自动创建一个临时世界,进世界后抓取 HUD、打开菜单抓取动画帧后退出。
 * 正常游戏中完全不激活。
 */
public final class SAOMenuPreview {

    private static final String PROP = "saomenu.preview";

    private static int ticks = 0;
    private static boolean worldRequested = false;
    private static int worldReadyTicks = 0;
    private static boolean itemsGiven = false;
    private static boolean menuOpened = false;
    private static int menuTicks = 0;
    private static boolean childClicked = false;
    private static boolean done = false;
    private static boolean langSwitched = false;
    private static boolean langReloadDone = false;

    private SAOMenuPreview() {
    }

    /** 是否以预览模式启动(供菜单界面附加可视化校验元素)。 */
    public static boolean requested() {
        String out = System.getProperty(PROP);
        return out != null && !out.isBlank();
    }

    public static void registerIfRequested() {
        if (!requested()) {
            return;
        }
        String out = System.getProperty(PROP);
        ClientTickEvent.CLIENT_POST.register(client -> tick(client, out));
    }

    private static void tick(Minecraft client, String out) {
        if (done) {
            return;
        }
        ticks++;
        if (ticks > 1600) {
            // 世界加载超时,放弃
            SAOMenu.LOGGER.warn("[SAOMenu] preview timed out waiting for world");
            done = true;
            client.stop();
            return;
        }

        if (!worldRequested) {
            if (ticks == 15 && !langSwitched) {
                // 国际化验证:主菜单阶段切换中文语言,并等待重载完成后
                // 才创建世界——重载与活跃世界渲染并发会触发原版 modelGroups 空指针竞态
                langSwitched = true;
                client.options.languageCode = "zh_cn";
                client.getLanguageManager().setSelected("zh_cn");
                client.reloadResourcePacks().thenRun(() -> langReloadDone = true);
            }
            if (ticks < 25 || !langReloadDone) {
                return;
            }
            worldRequested = true;
            // 上次运行的同名存档会让 createFreshLevel 失败,先删掉
            File oldSave = new File(new File(".", "saves"), "saomenupreview");
            if (oldSave.exists()) {
                deleteRecursively(oldSave);
            }
            try {
                client.createWorldOpenFlows().createFreshLevel("saomenupreview",
                        new LevelSettings("saomenupreview", GameType.CREATIVE,
                                false, Difficulty.PEACEFUL, true, new GameRules(),
                                WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20260828L, false, false),
                        registryAccess -> registryAccess.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
            } catch (Exception e) {
                SAOMenu.LOGGER.error("[SAOMenu] preview world creation failed", e);
                done = true;
                client.stop();
            }
            return;
        }

        if (!menuOpened) {
            // 无头运行时窗口可能被抢走焦点,原版会弹出暂停菜单并冻结世界;
            // 自检必须自己把它关掉,否则 worldReadyTicks 永远不前进直到超时
            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                client.setScreen(null);
                return;
            }
            if (client.player != null && client.level != null && client.screen == null) {
                worldReadyTicks++;
                if (!itemsGiven && worldReadyTicks >= 5) {
                    // 放入演示物品:验证圆点物品图标、数量角标与副手圆点
                    itemsGiven = true;
                    // 大 GUI 验证:guiScale 选项设 1(最小)→ 血条板增强布局可见。
                    // 注意直接 setGuiScale(1.0) 会被 auto 重算覆盖,必须改选项
                    client.options.guiScale().set(1);
                    client.resizeDisplay();
                    var inv = client.player.getInventory();
                    inv.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
                    inv.setItem(1, new ItemStack(Items.APPLE, 12));
                    inv.setItem(8, new ItemStack(Items.TORCH, 3));
                    inv.setItem(10, new ItemStack(Items.IRON_CHESTPLATE));
                    inv.setItem(11, new ItemStack(Items.GOLDEN_SWORD));
                    client.player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                    inv.selected = 1;
                    // 状态效果自检:速度 + 生命恢复 → 血条板下方应显示两个效果图标
                    client.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 6000, 1));
                    client.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.REGENERATION, 6000, 1));
                }
                if (worldReadyTicks == 20) {
                    // 提示文字已出现的完整形态
                    SAOMenu.LOGGER.info("[SAOMenu] preview welcome active={} msg={}",
                            SAOWelcome.active(),
                            Component.translatable("saomenu.welcome.msg").getString());
                    grab(client, out, "welcome.png");
                }
                if (worldReadyTicks == 22) {
                    // 收掉欢迎动画,避免遮挡后续 HUD / 菜单截图
                    SAOWelcome.dismiss();
                    // 时钟格式自检:切 12 小时制 + 显示日期
                    SAOConfig.setClock24h(false);
                    SAOConfig.setClockDate(true);
                }
                if (worldReadyTicks == 24) {
                    // 3D 环绕血条自检:召唤一只牛(常规体型)+ 一只巨人(高个子,
                    // 验证环不挂在头脖子上),视线锥门控要求准确看向它们
                    client.player.setYRot(0f);
                    client.player.setXRot(8f);
                    client.player.connection.sendCommand("summon cow ~0 ~ ~3");
                    client.player.connection.sendCommand(
                            "summon giant ~2.5 ~ ~3 {HasVisualFire:0b,Invulnerable:0b}");
                }
                if (worldReadyTicks >= 28 && worldReadyTicks <= 34) {
                    // summon 要往返服务端再同步回客户端(约 6-8 tick),实体到达时刻不确定,
                    // 这几 tick 每帧都重新瞄一次;牛的落点受地形影响,手动算 yaw/pitch 不可靠
                    aimAtNearestCow(client);
                }
                if (worldReadyTicks == 43) {
                    // 满血形态。淡入需 1/FADE_STEP ≈ 8 tick 才到满不透明,
                    // 瞄准结束(34)后必须留够这段时间再抓图,否则拍到半透明的中间态
                    SAOMenu.LOGGER.info("[SAOMenu] preview ring visible={} labels={}",
                            SAOTargetBar3D.visibleCount(), SAOTargetBar3D.labelCount());
                    grab(client, out, "target_full.png");
                }
                if (worldReadyTicks == 44) {
                    // 牛满血 10 → 掉 4.5 到 55%,进入黄色区间并触发受击闪白
                    client.player.connection.sendCommand(
                            "damage @e[type=cow,sort=nearest,limit=1] 4.5");
                }
                if (worldReadyTicks == 52) {
                    // 闪白已衰减完(FLASH_MS=320ms ≈ 6.4 tick),抓到纯黄色档位
                    grab(client, out, "target_mid.png");
                }
                if (worldReadyTicks == 54) {
                    // 侧视验证 3D 环绕:在「看向」判定内偏开一些,环应呈椭圆透视
                    aimAtNearestCow(client);
                    client.player.setYRot(client.player.getYRot() - 7f);
                }
                if (worldReadyTicks == 58) {
                    SAOMenu.LOGGER.info("[SAOMenu] preview ring side visible={}",
                            SAOTargetBar3D.visibleCount());
                    grab(client, out, "target_side.png");
                }
                if (worldReadyTicks == 59) {
                    // 视线彻底转开 → 血条应淡出消失
                    client.player.setYRot(client.player.getYRot() - 120f);
                }
                if (worldReadyTicks == 64) {
                    SAOMenu.LOGGER.info("[SAOMenu] preview ring away visible={}",
                            SAOTargetBar3D.visibleCount());
                    grab(client, out, "target_away.png");
                }
                if (worldReadyTicks == 65) {
                    // 转回来继续后续自检
                    aimAtNearestCow(client);
                }
                if (worldReadyTicks == 68) {
                    // 再掉 4.5 → 剩 1 血(10%),进入正红区间。
                    // 必须与上一次伤害间隔 >10 tick,否则会被原版无敌帧吞掉
                    client.player.connection.sendCommand(
                            "damage @e[type=cow,sort=nearest,limit=1] 4.5");
                }
                if (worldReadyTicks == 76) {
                    SAOMenu.LOGGER.info("[SAOMenu] preview targetbar captured");
                    grab(client, out, "target_low.png");
                }
                if (worldReadyTicks == 78) {
                    // 死亡碎裂自检:再补几只被动生物一起击杀
                    client.player.connection.sendCommand("summon cow ~-1.2 ~ ~3.5");
                    client.player.connection.sendCommand("summon pig ~1.2 ~ ~4");
                    // 顺手清掉巨人,别挡后面的 HUD 截图
                    client.player.connection.sendCommand("kill @e[type=giant]");
                }
                if (worldReadyTicks == 82) {
                    // 命令要往返服务端,死亡状态回传到客户端有约 5 tick 延迟
                    client.player.connection.sendCommand("kill @e[type=cow]");
                    client.player.connection.sendCommand("kill @e[type=pig]");
                }
                if (worldReadyTicks == 90) {
                    SAOMenu.LOGGER.info("[SAOMenu] preview shatter trails={} cfg={} density={}",
                            SAODeathEffect.pendingTrails(), SAOConfig.deathShatter(),
                            SAOConfig.deathShatterDensity());
                    grab(client, out, "death_shatter.png");
                }
                if (worldReadyTicks == 94) {
                    // 碎片开始坠落淡出的形态
                    grab(client, out, "death_shatter2.png");
                }
                if (worldReadyTicks == 96) {
                    // 触发升级(0→2 级),验证升级通知横幅
                    client.player.giveExperiencePoints(40);
                }
                if (worldReadyTicks == 98) {
                    // 成就通知自检:授予全部成就 → Toast 应被替换为 SAO 通知
                    client.player.connection.sendCommand("advancement grant @p everything");
                }
                if (worldReadyTicks == 104) {
                    // 主题色验证:切红色。渲染在批量追赶 tick 后才发生,
                    // 因此红色截图(hud_red.png)留到 6 tick 后抓取
                    SAOConfig.setAccentHue(0f);
                }
                if (worldReadyTicks == 106) {
                    // 受击反馈自检:血量 20→10,触发闪红
                    client.player.setHealth(10f);
                }
                if (worldReadyTicks == 112) {
                    // 低血量自检:血量 3(15%)→ 脉冲 + 低血量通知 + 第二次闪红
                    client.player.setHealth(3f);
                }
                if (worldReadyTicks == 114) {
                    // 抓取 112 号受击的闪红(留足渲染管线滞后裕度)
                    grab(client, out, "hud_flash.png");
                }
                if (worldReadyTicks == 120) {
                    grab(client, out, "hud_low.png");
                }
                if (worldReadyTicks == 121) {
                    client.player.setHealth(20f);
                }
                if (worldReadyTicks == 110) {
                    SAOMenu.LOGGER.info("[SAOMenu] preview lang2={} stat2={}",
                            client.getLanguageManager().getSelected(),
                            SAOHud.tr("saomenu.stat.level", 41));
                    grab(client, out, "hud_red.png");
                }
                if (worldReadyTicks == 116) {
                    // 恢复默认橙色,后续菜单截图保持参考样式
                    SAOConfig.setAccentHue(SAOConfig.DEF_ACCENT_HUE);
                }
                if (worldReadyTicks == 97) {
                    // 用 SAOHud.tr(与 UI 相同的手动替换路径)采样翻译
                    SAOMenu.LOGGER.info("[SAOMenu] preview xp level={} queue={} lang={} stat={}",
                            client.player.experienceLevel, SAONotification.size(),
                            client.getLanguageManager().getSelected(),
                            SAOHud.tr("saomenu.stat.level", client.player.experienceLevel));
                }
                if (worldReadyTicks == 123) {
                    // 演示通知:截图前入队(停留 2.6s,hud.png 应可见)
                    SAONotification.push(Component.translatable("saomenu.notify.demo").getString(), "Preview");
                }
                if (worldReadyTicks == 124) {
                    // 无菜单状态:验证血条板 / 圆点物品栏 / 原版快捷栏已隐藏
                    SAOMenu.LOGGER.info("[SAOMenu] preview hud level={} queue={} hue={}",
                            client.player.experienceLevel, SAONotification.size(), SAOConfig.accentHue());
                    grab(client, out, "hud.png");
                } else if (worldReadyTicks == 126) {
                    // Alt 自由观察自检:模拟按住左 Alt(GLFW 层注入按下状态由
                    // SAOFreeLook.tick 轮询读取,无法合成;直接调用状态机等价入口)
                    // → 第三人称 + 中键锁定 → 截图;随后解锁退出恢复
                    SAOFreeLook.debugEnterForPreview(client);
                } else if (worldReadyTicks == 128) {
                    // Alt 自由观察:无头环境无法合成「按住」的 GLFW 状态,
                    // 状态机会被 tick 立即退出;仅验证 enter/restore 对相机类型的切换
                    SAOFreeLook.debugEnterForPreview(client);
                    boolean thirdPerson = client.options.getCameraType()
                            == net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                    SAOFreeLook.debugExitForPreview(client);
                    boolean restored = client.options.getCameraType()
                            == net.minecraft.client.CameraType.FIRST_PERSON;
                    SAOMenu.LOGGER.info("[SAOMenu] preview freelook switch={} restore={}",
                            thirdPerson, restored);
                    client.setScreen(new SAOMenuScreen());
                    menuOpened = true;
                    menuTicks = 0;
                }
            } else {
                worldReadyTicks = 0;
            }
            return;
        }

        menuTicks++;
        if (menuTicks == 8) {
            // 打开动画进行中
            grab(client, out, "menu_opening.png");
        } else if (menuTicks == 12) {
            moveCursorTo(client, MenuLayout.firstButtonCenterX(client.getWindow().getGuiScaledWidth()),
                    MenuLayout.buttonCenterY(client.getWindow().getGuiScaledHeight(), 0));
        } else if (menuTicks == 46) {
            int cardH = MenuLayout.cardH(client.getWindow().getGuiScaledHeight());
            SAOMenu.LOGGER.info("[SAOMenu] preview card cardH={} statLines={}",
                    cardH, cardH >= 140 ? 8 : 6);
            grab(client, out, "menu.png");
        } else if (menuTicks == 48) {
            // 合成点击"队伍"主按钮:验证 scoreboard 队伍面板(光标悬停不可靠,点击才是确定性路径)
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, MenuLayout.firstButtonCenterX(w), MenuLayout.buttonCenterY(h, 1), 0);
        } else if (menuTicks == 52) {
            grab(client, out, "menu_party.png");
        } else if (menuTicks == 54) {
            // 合成点击"好友"主按钮:验证在线玩家列表面板
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, MenuLayout.firstButtonCenterX(w), MenuLayout.buttonCenterY(h, 2), 0);
        } else if (menuTicks == 58) {
            grab(client, out, "menu_friends.png");
        } else if (menuTicks == 59) {
            // 回到"队伍"面板
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, MenuLayout.firstButtonCenterX(w), MenuLayout.buttonCenterY(h, 1), 0);
        } else if (menuTicks == 61) {
            // 面板切换动作自检:点击队伍面板的"邀请玩家" → 应切到好友面板
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            var rect = MenuLayout.menuItemRect(w, h, 2, MenuLayout.buttonCenterY(h, 1), 0);
            clickScreen(client, rect.centerX(), rect.centerY(), 0);
        } else if (menuTicks == 63) {
            grab(client, out, "menu_invite.png");
        } else if (menuTicks == 64 && client.screen instanceof SAOMenuScreen) {
            // 回到"个人"按钮
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, MenuLayout.firstButtonCenterX(w), MenuLayout.buttonCenterY(h, 0), 0);
        } else if (menuTicks == 66 && client.screen instanceof SAOMenuScreen) {
            // 模拟点击"装备"展开二级菜单
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            var rect = MenuLayout.menuItemRect(w, h, 3, MenuLayout.buttonCenterY(h, 0), 1);
            moveCursorTo(client, rect.centerX(), rect.centerY());
            clickScreen(client, rect.centerX(), rect.centerY(), 0);
            childClicked = true;
        } else if (menuTicks == 70 && childClicked && client.screen instanceof SAOMenuScreen) {
            // 装备第三列自检:先穿上护甲,再点击"护甲"子项展开已装备列表
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new ItemStack(Items.IRON_HELMET));
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                    new ItemStack(Items.IRON_CHESTPLATE));
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,
                    new ItemStack(Items.IRON_LEGGINGS));
            client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
                    new ItemStack(Items.IRON_BOOTS));
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            var child = MenuLayout.childItemRect(w, h, 3, MenuLayout.buttonCenterY(h, 0), 1);
            moveCursorTo(client, child.centerX(), child.centerY());
            clickScreen(client, child.centerX(), child.centerY(), 0);
        } else if (menuTicks == 82 && childClicked && client.screen instanceof SAOMenuScreen) {
            grab(client, out, "menu_child.png");
        } else if (menuTicks == 83 && childClicked && client.screen instanceof SAOMenuScreen) {
            // 悬停"武器"子项:装备列应切到武器分类(主手钻石剑)
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            var child = MenuLayout.childItemRect(w, h, 3, MenuLayout.buttonCenterY(h, 0), 0);
            moveCursorTo(client, child.centerX(), child.centerY());
            moveScreen(client, child.centerX(), child.centerY());
        } else if (menuTicks == 90 && childClicked && client.screen instanceof SAOMenuScreen) {
            grab(client, out, "menu_equip_weapon.png");
        } else if (menuTicks == 91 && childClicked && client.screen instanceof SAOMenuScreen) {
            // 悬停"首饰":副手盾牌
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            var child = MenuLayout.childItemRect(w, h, 3, MenuLayout.buttonCenterY(h, 0), 2);
            moveCursorTo(client, child.centerX(), child.centerY());
            moveScreen(client, child.centerX(), child.centerY());
        } else if (menuTicks == 97 && childClicked && client.screen instanceof SAOMenuScreen) {
            grab(client, out, "menu_equip_trinket.png");
        } else if (menuTicks == 98 && childClicked) {
            // 验证配置持久化:关闭音效并落盘
            SAOConfig.setSounds(false);
            SAOConfig.save(SAOConfig.path());
            // 打开模组设置界面(提前到 98 给入场动画留足截图余量);
            // 菜单若已被上层时序关闭(tick 66 二级展开回归),用新菜单实例兜底作为返回目标
            client.setScreen(new SAOSettingsScreen(
                    client.screen instanceof SAOMenuScreen ms ? ms : new SAOMenuScreen()));
        } else if (menuTicks == 108 && childClicked) {
            // 主题色落盘验证:蓝色(200°)保存,config.png 应为蓝色主题
            SAOConfig.setAccentHue(200f);
            SAOConfig.save(SAOConfig.path());
        } else if (menuTicks == 110 && childClicked) {
            // 设置界面入场动画完成后截图(视频背景 + P5 分类按钮)
            grab(client, out, "settings.png");
        } else if (menuTicks == 111 && childClicked) {
            // O 键层级返回自检:配置界面按 O → 回到菜单
            long win = client.getWindow().getWindow();
            client.keyboardHandler.keyPress(win, GLFW.GLFW_KEY_O, 0, GLFW.GLFW_PRESS, 0);
            client.keyboardHandler.keyPress(win, GLFW.GLFW_KEY_O, 0, GLFW.GLFW_RELEASE, 0);
        } else if (menuTicks == 112 && childClicked) {
            SAOMenu.LOGGER.info("[SAOMenu] preview okey1 screen={}",
                    client.screen == null ? "null" : client.screen.getClass().getSimpleName());
            // 再按一次 O → 关闭菜单回游戏
            long win = client.getWindow().getWindow();
            client.keyboardHandler.keyPress(win, GLFW.GLFW_KEY_O, 0, GLFW.GLFW_PRESS, 0);
            client.keyboardHandler.keyPress(win, GLFW.GLFW_KEY_O, 0, GLFW.GLFW_RELEASE, 0);
        } else if (menuTicks == 116 && childClicked) {
            // 第二次 O 在 112 按下,关闭动画 170ms,此处应已回到游戏(无界面)
            SAOMenu.LOGGER.info("[SAOMenu] preview okey2 screen={}",
                    client.screen == null ? "null" : client.screen.getClass().getSimpleName());
            grab(client, out, "config.png");
        } else if (menuTicks == 117 && childClicked) {
            // 打开 SAO 物品栏(菜单"物品"同款界面)
            client.setScreen(new SAOInventoryScreen(client.player));
        } else if (menuTicks == 118 && childClicked) {
            // 滚轮切槽自检:向下滚 → 选中槽 1→2
            scrollScreen(client, 0, 0, -1);
            SAOMenu.LOGGER.info("[SAOMenu] preview scroll selected={}",
                    client.player.getInventory().selected);
                } else if (menuTicks == 142 && childClicked) {
            // 交互自检:左键拿起快捷栏 slot0 的钻石剑
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.slotCenterX(w, 0), SAOInventoryScreen.slotCenterY(h), 0);
        } else if (menuTicks == 120 && childClicked) {
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.slotCenterX(w, 5), SAOInventoryScreen.slotCenterY(h), 0);
        } else if (menuTicks == 130 && childClicked) {
            var inv = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview inv slot0={} slot5={}",
                    inv.getItem(0).getDescriptionId(), inv.getItem(5).getDescriptionId());
        } else if (menuTicks == 131 && childClicked) {
            // 护甲自动入槽自检:主区 slot10(铁胸甲) Shift → 护甲槽 index 2
            ((SAOInventoryScreen) client.screen).previewShiftClick(10);
            var inv0 = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview armor slot10={} chest={}",
                    inv0.getItem(10).getDescriptionId(), inv0.armor.get(2).getDescriptionId());
            // Shift 转移自检:快捷栏 slot5(剑) → 主物品区 slot9
            ((SAOInventoryScreen) client.screen).previewShiftClick(5);
            var inv = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview shift slot5={} slot9={}",
                    inv.getItem(5).getDescriptionId(), inv.getItem(9).getDescriptionId());
        } else if (menuTicks == 132 && childClicked) {
            // 数字键自检:悬停主区 slot9(剑) 按 3 → 与快捷栏下标 2 交换
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            moveScreen(client, SAOInventoryScreen.slotCenterX(w, 0), SAOInventoryScreen.mainSlotCenterY(h));
            client.screen.keyPressed(GLFW.GLFW_KEY_3, 0, 0);
            var inv = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview numkey slot9={} slot2={}",
                    inv.getItem(9).getDescriptionId(), inv.getItem(2).getDescriptionId());
        } else if (menuTicks == 133 && childClicked) {
            // Q 丢弃自检:悬停快捷栏 slot2(剑) 按 Q → 槽位清空
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            moveScreen(client, SAOInventoryScreen.slotCenterX(w, 2), SAOInventoryScreen.slotCenterY(h));
            client.screen.keyPressed(GLFW.GLFW_KEY_Q, 0, 0);
            var inv = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview qdrop slot2={}",
                    inv.getItem(2).getDescriptionId());
        } else if (menuTicks == 134 && childClicked) {
            grab(client, out, "inventory.png");
        } else if (menuTicks == 135 && childClicked) {
            // 分类标签自检:点击"武器"标签
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.tabCenterX(w, 1), SAOInventoryScreen.tabCenterY(h), 0);
        } else if (menuTicks == 136 && childClicked) {
            grab(client, out, "inventory_tab.png");
        } else if (menuTicks == 137 && childClicked) {
            // 切回"全部"
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.tabCenterX(w, 0), SAOInventoryScreen.tabCenterY(h), 0);
        } else if (menuTicks == 138 && childClicked) {
            // 拖拽自检准备:拿起快捷栏 slot1 的 12 个苹果
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.slotCenterX(w, 1), SAOInventoryScreen.slotCenterY(h), 0);
        } else if (menuTicks == 139 && childClicked) {
            // 左键拖放:拖过 slot3、slot4 → 均分 6/6
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            dragScreen(client, SAOInventoryScreen.slotCenterX(w, 3), SAOInventoryScreen.slotCenterY(h), 0, 0, 0);
            dragScreen(client, SAOInventoryScreen.slotCenterX(w, 4), SAOInventoryScreen.slotCenterY(h), 0, 0, 0);
            releaseScreen(client, SAOInventoryScreen.slotCenterX(w, 4), SAOInventoryScreen.slotCenterY(h), 0);
            var inv = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview dragsplit slot3={} slot4={}",
                    inv.getItem(3).getCount(), inv.getItem(4).getCount());
        } else if (menuTicks == 140 && childClicked) {
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.slotCenterX(w, 3), SAOInventoryScreen.slotCenterY(h), 0);
        } else if (menuTicks == 141 && childClicked) {
            // 右键拖放:拖过 slot5、slot6、slot7 → 各放 1 个
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            dragScreen(client, SAOInventoryScreen.slotCenterX(w, 5), SAOInventoryScreen.slotCenterY(h), 1, 0, 0);
            dragScreen(client, SAOInventoryScreen.slotCenterX(w, 6), SAOInventoryScreen.slotCenterY(h), 1, 0, 0);
            dragScreen(client, SAOInventoryScreen.slotCenterX(w, 7), SAOInventoryScreen.slotCenterY(h), 1, 0, 0);
            releaseScreen(client, SAOInventoryScreen.slotCenterX(w, 7), SAOInventoryScreen.slotCenterY(h), 1);
            var inv = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview dragone slot5={} slot6={} slot7={}",
                    inv.getItem(5).getCount(), inv.getItem(6).getCount(), inv.getItem(7).getCount());
        } else if (menuTicks == 142 && childClicked) {
            grab(client, out, "inventory2.png");
        } else if (menuTicks == 143 && childClicked) {
            clickScreen(client, 5, 5, 0);
        } else if (menuTicks == 144 && childClicked) {
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.slotCenterX(w, 4), SAOInventoryScreen.slotCenterY(h), 0);
        } else if (menuTicks == 145 && childClicked) {
            // 双击同槽位 → 聚合同种苹果(slot3/5/6/7 应被吸空)
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.slotCenterX(w, 4), SAOInventoryScreen.slotCenterY(h), 0);
            var inv = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview gather slot3={} slot5={} slot7={}",
                    inv.getItem(3).getCount(), inv.getItem(5).getCount(), inv.getItem(7).getCount());
        } else if (menuTicks == 146 && childClicked) {
            clickScreen(client, 5, 5, 0);
        } else if (menuTicks == 147 && childClicked) {
            // 右键拆分自检:slot8 有 3 个火把 → 拿起 2,槽位剩 1
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.slotCenterX(w, 8), SAOInventoryScreen.slotCenterY(h), 1);
            SAOMenu.LOGGER.info("[SAOMenu] preview rsplit slot8={}",
                    client.player.getInventory().getItem(8).getCount());
        } else if (menuTicks == 148 && childClicked) {
            clickScreen(client, 5, 5, 0);
        } else if (menuTicks == 149 && childClicked) {
            // 数量 1 再右键(空手)→ 整堆拿起,槽位清空
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, SAOInventoryScreen.slotCenterX(w, 8), SAOInventoryScreen.slotCenterY(h), 1);
            SAOMenu.LOGGER.info("[SAOMenu] preview rsplit1 slot8={}",
                    client.player.getInventory().getItem(8).getCount());
        } else if (menuTicks == 150 && childClicked) {
            // F 键副手交换自检:悬停 slot6(空) 按 F → 盾牌换入 slot6
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            moveScreen(client, SAOInventoryScreen.slotCenterX(w, 6), SAOInventoryScreen.slotCenterY(h));
            client.screen.keyPressed(GLFW.GLFW_KEY_F, 0, 0);
            var inv = client.player.getInventory();
            SAOMenu.LOGGER.info("[SAOMenu] preview fswap slot6={}",
                    inv.getItem(6).getDescriptionId());
        } else if (menuTicks == 151 && childClicked) {
            grab(client, out, "inventory3.png");
        } else if (menuTicks == 152 && childClicked) {
            // 关闭物品栏,重新打开菜单测试"技能"面板
            closeScreen(client);
        } else if (menuTicks == 153 && childClicked) {
            client.setScreen(new SAOMenuScreen());
        } else if (menuTicks == 155 && childClicked) {
            // 点击"技能"菜单项 → 打开属性面板
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            var rect = MenuLayout.menuItemRect(w, h, 3, MenuLayout.buttonCenterY(h, 0), 0);
            clickScreen(client, rect.centerX(), rect.centerY(), 0);
        } else if (menuTicks == 158 && childClicked) {
            SAOMenu.LOGGER.info("[SAOMenu] preview stats screen={}",
                    client.screen == null ? "null" : client.screen.getClass().getSimpleName());
            grab(client, out, "stats.png");
        } else if (menuTicks == 160 && childClicked) {
            // 关闭属性面板,回到菜单
            closeScreen(client);
        } else if (menuTicks == 166 && childClicked) {
            // 等菜单打开动画结束(260ms)再点击,否则逆变换会把点击映射出按钮区
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            clickScreen(client, MenuLayout.firstButtonCenterX(w), MenuLayout.buttonCenterY(h, 2), 0);
        } else if (menuTicks == 168 && childClicked) {
            // 点击"成就图鉴"(好友面板第 1 项)
            int w = client.getWindow().getGuiScaledWidth();
            int h = client.getWindow().getGuiScaledHeight();
            var rect = MenuLayout.menuItemRect(w, h, 2, MenuLayout.buttonCenterY(h, 2), 0);
            clickScreen(client, rect.centerX(), rect.centerY(), 0);
        } else if (menuTicks == 172 && childClicked) {
            SAOMenu.LOGGER.info("[SAOMenu] preview adv screen={}",
                    client.screen == null ? "null" : client.screen.getClass().getSimpleName());
            grab(client, out, "adv.png");
        } else if (menuTicks == 174) {
            // 第三人称菜单板自检:重新打开菜单 → F5 切第三人称 → 截图验证
            client.setScreen(new SAOMenuScreen());
        } else if (menuTicks == 182) {
            client.keyboardHandler.keyPress(client.getWindow().getWindow(),
                    GLFW.GLFW_KEY_F5, 0, GLFW.GLFW_PRESS, 0);
            client.keyboardHandler.keyPress(client.getWindow().getWindow(),
                    GLFW.GLFW_KEY_F5, 0, GLFW.GLFW_RELEASE, 0);
        } else if (menuTicks == 192) {
            SAOMenu.LOGGER.info("[SAOMenu] preview world_menu cam={}",
                    client.options.getCameraType());
            grab(client, out, "world_menu.png");
        } else if (menuTicks == 194) {
            // F5 切回第一人称,收尾
            client.keyboardHandler.keyPress(client.getWindow().getWindow(),
                    GLFW.GLFW_KEY_F5, 0, GLFW.GLFW_PRESS, 0);
            client.keyboardHandler.keyPress(client.getWindow().getWindow(),
                    GLFW.GLFW_KEY_F5, 0, GLFW.GLFW_RELEASE, 0);
            closeScreen(client);
            done = true;
            client.stop();
        }
    }

    /** 把视线转到最近一只牛的躯干上,供 3D 血条的视线门控判定为「看向」。 */
    private static void aimAtNearestCow(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        net.minecraft.world.entity.LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (var e : client.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.animal.Cow cow)) {
                continue;
            }
            double d = cow.distanceToSqr(client.player);
            if (d < bestDist) {
                bestDist = d;
                best = cow;
            }
        }
        if (best == null) {
            SAOMenu.LOGGER.warn("[SAOMenu] preview aim: no cow found");
            return;
        }
        client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                best.position().add(0, best.getBbHeight() * 0.62, 0));
    }

    private static void deleteRecursively(File file) {        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            SAOMenu.LOGGER.warn("[SAOMenu] failed to delete {}", file);
        }
    }

    private static void moveCursorTo(Minecraft client, int guiX, int guiY) {
        double scale = client.getWindow().getGuiScale();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(), guiX * scale, guiY * scale);
    }

    // ------------------------------------------------------------ 界面操作 helper(界面已关时静默跳过,避免自检中断)

    private static void clickScreen(Minecraft client, double x, double y, int btn) {
        if (client.screen != null) {
            client.screen.mouseClicked(x, y, btn);
        }
    }

    private static void moveScreen(Minecraft client, double x, double y) {
        if (client.screen != null) {
            client.screen.mouseMoved(x, y);
        }
    }

    private static void scrollScreen(Minecraft client, double x, double y, double delta) {
        if (client.screen != null) {
            client.screen.mouseScrolled(x, y, delta);
        }
    }

    private static void dragScreen(Minecraft client, double x, double y, int btn, double dx, double dy) {
        if (client.screen != null) {
            client.screen.mouseDragged(x, y, btn, dx, dy);
        }
    }

    private static void releaseScreen(Minecraft client, double x, double y, int btn) {
        if (client.screen != null) {
            client.screen.mouseReleased(x, y, btn);
        }
    }

    private static void closeScreen(Minecraft client) {
        if (client.screen != null) {
            client.screen.onClose();
        }
    }

    private static void grab(Minecraft client, String out, String name) {
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            Path dir = new File(out).toPath();
            Files.createDirectories(dir);
            Path file = dir.resolve(name);
            image.writeToFile(file);
            SAOMenu.LOGGER.info("[SAOMenu] preview written to {}", file.toAbsolutePath());
        } catch (IOException e) {
            SAOMenu.LOGGER.error("[SAOMenu] preview failed", e);
        }
    }
}
