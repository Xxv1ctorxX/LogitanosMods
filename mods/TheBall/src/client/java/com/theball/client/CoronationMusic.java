package com.theball.client;

import com.theball.network.MusicPayload;
import com.theball.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Música de fondo del Coronamiento: suena para todos igual (sin posición) y se puede difuminar poco a poco. */
public final class CoronationMusic {
	private static Instance current;

	private CoronationMusic() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MusicPayload.TYPE, (payload, context) -> {
			switch (payload.action()) {
				case MusicPayload.START -> {
					if (current != null) {
						current.halt();
					}
					current = new Instance();
					Minecraft.getInstance().getSoundManager().play(current);
				}
				case MusicPayload.FADE -> {
					if (current != null) {
						current.fade(Math.max(1, payload.ticks()));
					}
				}
				default -> {
					if (current != null) {
						current.halt();
						current = null;
					}
				}
			}
		});
	}

	private static final class Instance extends AbstractTickableSoundInstance {
		private int fadeTotal;
		private int fadeLeft;
		private float startVolume = 1.0f;

		Instance() {
			super(ModSounds.CORONATION_MUSIC, SoundSource.RECORDS, RandomSource.create());
			this.volume = 1.0f;
			this.looping = false;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.relative = true;
		}

		void fade(int ticks) {
			if (fadeTotal == 0) {
				fadeTotal = ticks;
				fadeLeft = ticks;
				startVolume = this.volume;
			}
		}

		void halt() {
			stop();
		}

		@Override
		public void tick() {
			if (fadeTotal > 0) {
				fadeLeft--;
				this.volume = startVolume * Math.max(0.0f, fadeLeft / (float) fadeTotal);
				if (fadeLeft <= 0) {
					stop();
				}
			}
		}
	}
}
