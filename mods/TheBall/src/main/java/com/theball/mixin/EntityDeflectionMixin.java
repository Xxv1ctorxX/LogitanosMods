package com.theball.mixin;

import com.theball.power.CrownPowers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reflejo de la Corona: vanilla pregunta a la entidad golpeada cómo desviar un proyectil (así lo hace la Breeze).
 * Si el jugador lleva la Corona, a veces se lo devolvemos a quien lo lanzó.
 */
@Mixin(Entity.class)
public abstract class EntityDeflectionMixin {
	@Inject(method = "deflection", at = @At("HEAD"), cancellable = true)
	private void theball$crownReflect(Projectile projectile, CallbackInfoReturnable<ProjectileDeflection> cir) {
		if ((Object) this instanceof ServerPlayer player) {
			ProjectileDeflection deflection = CrownPowers.deflectionFor(player, projectile);
			if (deflection != null) {
				cir.setReturnValue(deflection);
			}
		}
	}
}
