package com.sao.saomenu.mixin;

import com.sao.saomenu.SAOMenu;
import com.sao.saomenu.client.SAOConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 成就聊天栏过滤:saoToasts 开启时隐藏
 * "X has made the advancement [Y]" 系统聊天消息(通知横幅已替代它)。
 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public class AdvancementChatMixin {

    private static boolean saomenu$logged;

    @Inject(method = "handleSystemChat(Lnet/minecraft/network/protocol/game/ClientboundSystemChatPacket;)V",
            at = @At("HEAD"), cancellable = true)
    private void saomenu$filterAdvancementChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!SAOConfig.saoToasts()) {
            return;
        }
        Component content = packet.content();
        if (content instanceof MutableComponent mc
                && mc.getContents() instanceof TranslatableContents tc
                && tc.getKey().startsWith("chat.type.advancement")) {
            if (!saomenu$logged) {
                saomenu$logged = true;
                SAOMenu.LOGGER.info("[SAOMenu] advancement chat suppressed: {}", tc.getKey());
            }
            ci.cancel();
        }
    }
}
