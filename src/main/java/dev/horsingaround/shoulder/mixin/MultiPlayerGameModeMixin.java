package dev.horsingaround.shoulder.mixin;

import dev.horsingaround.shoulder.ShoulderAim;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Using an item reports the rotation it was used with; point it at the crosshair target too. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@ModifyArg(
		method = "startPrediction",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V")
	)
	private Packet<?> horsingaroundShoulder$aimUse(final Packet<?> packet) {
		return packet instanceof ServerboundUseItemPacket use && ShoulderAim.isActive()
			? new ServerboundUseItemPacket(use.hand(), use.sequence(), ShoulderAim.yaw(), ShoulderAim.pitch())
			: packet;
	}
}
