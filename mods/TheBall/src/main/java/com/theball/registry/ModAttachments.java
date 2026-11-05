package com.theball.registry;

import com.theball.TheBallMod;
import com.theball.power.PowerState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/** Datos que se cuelgan de los jugadores (API de attachments de Fabric). */
public final class ModAttachments {
	/** Esperas de los poderes y botín final recibido. Se guarda y sobrevive a la muerte. */
	public static final AttachmentType<PowerState> POWER = AttachmentRegistry.create(TheBallMod.id("power"),
			b -> b.persistent(PowerState.CODEC).copyOnDeath().initializer(() -> PowerState.EMPTY));

	/** Marca persistente en cada chunk: ya se ha completado con aluminio (ver OreRetrogen). */
	public static final AttachmentType<Boolean> RETROGEN_DONE = AttachmentRegistry.create(TheBallMod.id("retrogen_done"),
			b -> b.persistent(com.mojang.serialization.Codec.BOOL));

	private ModAttachments() {
	}

	public static void register() {
	}

	public static PowerState power(Player player) {
		PowerState s = player.getAttached(POWER);
		return s == null ? PowerState.EMPTY : s;
	}

	public static void setPower(Player player, PowerState state) {
		player.setAttached(POWER, state);
	}
}
