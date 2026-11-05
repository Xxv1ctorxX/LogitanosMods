package com.theball.registry;

import com.theball.TheBallMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Sonidos propios (definidos en assets/theball/sounds.json; los .ogg van en assets/theball/sounds/). */
public final class ModSounds {
	/** Al terminar de ascender y entrar en Modo Dios del aluminio. */
	public static final SoundEvent GOD_MODE = register("explosive.god_mode");
	/** Al impactar el golpe al suelo (click izquierdo en Modo Dios). */
	public static final SoundEvent SLAM = register("explosive.slam");

	/** Al llegar la Reina a 2000 hojas (empieza la cuenta atrás del Coronamiento). */
	public static final SoundEvent CORONATION_START = register("coronation.start");

	/** Música de fondo durante el espectáculo del Coronamiento (juicio y coronación; se corta al terminar). */
	public static final SoundEvent CORONATION_MUSIC = register("coronation.music");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = TheBallMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void register() {
	}
}
