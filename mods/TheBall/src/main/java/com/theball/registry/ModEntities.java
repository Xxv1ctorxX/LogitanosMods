package com.theball.registry;

import com.theball.TheBallMod;
import com.theball.entity.AluminumBallEntity;
import com.theball.entity.ExplosiveShotEntity;
import com.theball.entity.FoilShotEntity;
import com.theball.entity.PowerVisualEntity;
import com.theball.entity.GoalEntity;
import com.theball.entity.GoalPartEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> ALUMINUM_BALL_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheBallMod.id("aluminum_ball"));

	public static final EntityType<AluminumBallEntity> ALUMINUM_BALL = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			ALUMINUM_BALL_KEY,
			EntityType.Builder.<AluminumBallEntity>of(AluminumBallEntity::new, MobCategory.MISC)
					.sized(0.12f, 0.12f) // tamaño inicial (9 hojas); luego es dinámico
					.clientTrackingRange(10)
					.updateInterval(1) // actualizar cada tick para que el movimiento se vea fluido
					.fireImmune()
					.build(ALUMINUM_BALL_KEY));

	public static final ResourceKey<EntityType<?>> GOAL_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheBallMod.id("goal"));

	public static final EntityType<GoalEntity> GOAL = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			GOAL_KEY,
			EntityType.Builder.<GoalEntity>of(GoalEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(16)
					.updateInterval(20)
					.fireImmune()
					.build(GOAL_KEY));

	public static final ResourceKey<EntityType<?>> GOAL_PART_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheBallMod.id("goal_part"));

	/** Pieza sólida invisible de una portería. */
	public static final EntityType<GoalPartEntity> GOAL_PART = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			GOAL_PART_KEY,
			EntityType.Builder.<GoalPartEntity>of(GoalPartEntity::new, MobCategory.MISC)
					.sized(0.1f, 0.1f)
					.clientTrackingRange(16)
					.updateInterval(20)
					.noSave()
					.noSummon()
					.fireImmune()
					.build(GOAL_PART_KEY));

	public static final ResourceKey<EntityType<?>> FOIL_SHOT_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheBallMod.id("foil_shot"));

	/** Bola rebotona del cetro. */
	public static final EntityType<FoilShotEntity> FOIL_SHOT = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			FOIL_SHOT_KEY,
			EntityType.Builder.<FoilShotEntity>of(FoilShotEntity::new, MobCategory.MISC)
					.sized(FoilShotEntity.SIZE, FoilShotEntity.SIZE)
					.clientTrackingRange(6)
					.updateInterval(2)
					.noSave()
					.build(FOIL_SHOT_KEY));

	public static final ResourceKey<EntityType<?>> EXPLOSIVE_SHOT_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheBallMod.id("explosive_shot"));

	/** Bola de aluminio explosivo (y la gigante del remate). */
	public static final EntityType<ExplosiveShotEntity> EXPLOSIVE_SHOT = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			EXPLOSIVE_SHOT_KEY,
			EntityType.Builder.<ExplosiveShotEntity>of(ExplosiveShotEntity::new, MobCategory.MISC)
					.sized(0.7f, 0.7f)
					.clientTrackingRange(16)
					.updateInterval(2)
					.noSave()
					.fireImmune()
					.build(EXPLOSIVE_SHOT_KEY));

	public static final ResourceKey<EntityType<?>> POWER_VISUAL_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheBallMod.id("power_visual"));

	/** Esfera del Modo Bola / halo de la Ascensión (solo visual). */
	public static final EntityType<PowerVisualEntity> POWER_VISUAL = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			POWER_VISUAL_KEY,
			EntityType.Builder.<PowerVisualEntity>of(PowerVisualEntity::new, MobCategory.MISC)
					.sized(0.1f, 0.1f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build(POWER_VISUAL_KEY));

	private ModEntities() {
	}

	public static void register() {
	}
}
