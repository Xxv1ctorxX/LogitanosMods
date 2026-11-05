package com.theball.registry;

import com.theball.TheBallMod;
import com.theball.block.entity.ThroneBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<ThroneBlockEntity> THRONE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			TheBallMod.id("throne"),
			new BlockEntityType<>(ThroneBlockEntity::new, Set.of(ModBlocks.THRONE)));

	private ModBlockEntities() {
	}

	public static void register() {
	}
}
