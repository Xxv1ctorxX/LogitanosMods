package com.theball.registry;

import com.theball.TheBallMod;
import com.theball.block.ConeBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final Block ALUMINUM_ORE = registerBlock("aluminum_ore", Block::new,
			BlockBehaviour.Properties.of().strength(3.0f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));

	public static final Block ALUMINUM_BLOCK = registerBlock("aluminum_block", Block::new,
			BlockBehaviour.Properties.of().strength(5.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL));

	public static final Block DEEPSLATE_ALUMINUM_ORE = registerBlock("deepslate_aluminum_ore", Block::new,
			BlockBehaviour.Properties.of().strength(4.5f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));

	public static final Block CONE = registerBlock("cone", ConeBlock::new,
			BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.WOOL).noOcclusion());

	private ModBlocks() {
	}

	/** Registra el bloque y su item de bloque. */
	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, TheBallMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, TheBallMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void register() {
	}
}
