package com.theball.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Datos persistentes de un jugador (attachment {@code theball:power}): cuándo vuelve a estar listo cada poder (en
 * ticks de juego del mundo, que siguen contando tras un reinicio) y si ya recibió el botín final de una Reina.
 */
public record PowerState(long ballModeReady, long decreeReady, long explosiveReady, boolean finalLootReceived) {
	public static final PowerState EMPTY = new PowerState(0L, 0L, 0L, false);

	public static final Codec<PowerState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("ball_mode_ready", 0L).forGetter(PowerState::ballModeReady),
			Codec.LONG.optionalFieldOf("decree_ready", 0L).forGetter(PowerState::decreeReady),
			Codec.LONG.optionalFieldOf("explosive_ready", 0L).forGetter(PowerState::explosiveReady),
			Codec.BOOL.optionalFieldOf("final_loot_received", false).forGetter(PowerState::finalLootReceived)
	).apply(i, PowerState::new));

	public PowerState withBallModeReady(long t) {
		return new PowerState(t, decreeReady, explosiveReady, finalLootReceived);
	}

	public PowerState withDecreeReady(long t) {
		return new PowerState(ballModeReady, t, explosiveReady, finalLootReceived);
	}

	public PowerState withExplosiveReady(long t) {
		return new PowerState(ballModeReady, decreeReady, t, finalLootReceived);
	}

	public PowerState withFinalLootReceived(boolean v) {
		return new PowerState(ballModeReady, decreeReady, explosiveReady, v);
	}

	public PowerState resetCooldowns() {
		return new PowerState(0L, 0L, 0L, finalLootReceived);
	}
}
