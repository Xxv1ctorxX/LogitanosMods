package com.theslave.registry;

import com.theslave.TheSlaveMod;
import com.theslave.entity.EsclavoEntity;
import com.theslave.entity.LaMuerteEntity;
import com.theslave.entity.NotarioEntity;
import com.theslave.entity.UmbralEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> ESCLAVO_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheSlaveMod.id("esclavo"));

	public static final EntityType<EsclavoEntity> ESCLAVO = Registry.register(BuiltInRegistries.ENTITY_TYPE, ESCLAVO_KEY,
			EntityType.Builder.<EsclavoEntity>of(EsclavoEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).updateInterval(2)
					.build(ESCLAVO_KEY));

	public static final ResourceKey<EntityType<?>> MUERTE_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheSlaveMod.id("la_muerte"));

	public static final EntityType<LaMuerteEntity> LA_MUERTE = Registry.register(BuiltInRegistries.ENTITY_TYPE, MUERTE_KEY,
			EntityType.Builder.<LaMuerteEntity>of(LaMuerteEntity::new, MobCategory.MISC)
					.sized(0.7f, 2.4f).eyeHeight(2.1f).clientTrackingRange(12).updateInterval(2)
					.noSave().noSummon().fireImmune().build(MUERTE_KEY));

	public static final ResourceKey<EntityType<?>> NOTARIO_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheSlaveMod.id("notario"));

	public static final EntityType<NotarioEntity> NOTARIO = Registry.register(BuiltInRegistries.ENTITY_TYPE, NOTARIO_KEY,
			EntityType.Builder.<NotarioEntity>of(NotarioEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.95f).eyeHeight(1.7f).clientTrackingRange(10).updateInterval(3).build(NOTARIO_KEY));

	public static final ResourceKey<EntityType<?>> UMBRAL_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, TheSlaveMod.id("umbral"));

	public static final EntityType<UmbralEntity> UMBRAL = Registry.register(BuiltInRegistries.ENTITY_TYPE, UMBRAL_KEY,
			EntityType.Builder.<UmbralEntity>of(UmbralEntity::new, MobCategory.MISC)
					.sized(2.0f, 3.0f).clientTrackingRange(12).updateInterval(2)
					.noSave().noSummon().fireImmune().build(UMBRAL_KEY));

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(ESCLAVO, EsclavoEntity.atributos());
		FabricDefaultAttributeRegistry.register(LA_MUERTE, LaMuerteEntity.atributos());
		FabricDefaultAttributeRegistry.register(NOTARIO, NotarioEntity.atributos());
	}
}
