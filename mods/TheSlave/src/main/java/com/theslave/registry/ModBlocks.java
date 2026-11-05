package com.theslave.registry;

import com.theslave.TheSlaveMod;
import com.theslave.block.AltarBlock;
import com.theslave.block.DecorBlock;
import com.theslave.block.TumbaBlock;
import com.theslave.block.TumbaBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ModBlocks {
	public static final Block TUMBA;
	public static final Block ALTAR;
    public static final Block LECHO_CENIZA;
	public static final BlockEntityType<TumbaBlockEntity> TUMBA_BE;
    public static final BlockEntityType<com.theslave.block.LechoBlockEntity> LECHO_BE;

	/** Terreno infectado y decoración de los enclaves, en orden de pestaña. Ninguno propaga nada. */
	public static final Map<String, Block> EXTRA = new LinkedHashMap<>();

	public static Block PIEDRA_INFECTADA;
	public static Block PIEDRA_NEGRA_INFECTADA;
	public static Block NETHERRACK_INFECTADA;

	static {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, TheSlaveMod.id("tumba"));
		TUMBA = Registry.register(BuiltInRegistries.BLOCK, blockKey, new TumbaBlock(
				BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).noLootTable().noOcclusion()
						.sound(SoundType.STONE).setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, TheSlaveMod.id("tumba"));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(TUMBA, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ResourceKey<Block> altarKey = ResourceKey.create(Registries.BLOCK, TheSlaveMod.id("altar_ceniza"));
		ALTAR = Registry.register(BuiltInRegistries.BLOCK, altarKey, new AltarBlock(
				BlockBehaviour.Properties.of().strength(3.5f, 6.0f).requiresCorrectToolForDrops().noOcclusion()
						.sound(SoundType.DEEPSLATE)
						.lightLevel(s -> s.getValue(AltarBlock.VELAS[0]) || s.getValue(AltarBlock.VELAS[1])
								|| s.getValue(AltarBlock.VELAS[2]) || s.getValue(AltarBlock.VELAS[3]) ? 8 : 0)
						.setId(altarKey)));
		ResourceKey<Item> altarItem = ResourceKey.create(Registries.ITEM, TheSlaveMod.id("altar_ceniza"));
		Registry.register(BuiltInRegistries.ITEM, altarItem,
				new BlockItem(ALTAR, new Item.Properties().setId(altarItem).useBlockDescriptionPrefix()));
		TUMBA_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TheSlaveMod.id("tumba"),
				FabricBlockEntityTypeBuilder.create(TumbaBlockEntity::new, TUMBA).build());
        ResourceKey<Block> lechoKey=ResourceKey.create(Registries.BLOCK,TheSlaveMod.id("lecho_ceniza"));
        LECHO_CENIZA=Registry.register(BuiltInRegistries.BLOCK,lechoKey,new com.theslave.block.LechoBlock(
                BlockBehaviour.Properties.of().strength(-1.0f,3600000.0f).noLootTable().noOcclusion()
                        .sound(SoundType.DEEPSLATE)
                        .lightLevel(s -> s.getValue(com.theslave.block.LechoBlock.GRAVE)
                                && s.getValue(com.theslave.block.LechoBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.HEAD?3:0)
                        .setId(lechoKey)));
        ResourceKey<Item> lechoItem=ResourceKey.create(Registries.ITEM,TheSlaveMod.id("lecho_ceniza"));
        Registry.register(BuiltInRegistries.ITEM,lechoItem,new com.theslave.item.LechoItem(LECHO_CENIZA,
                new Item.Properties().stacksTo(1).setId(lechoItem).useBlockDescriptionPrefix()));
        LECHO_BE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,TheSlaveMod.id("lecho_ceniza"),
                FabricBlockEntityTypeBuilder.create(com.theslave.block.LechoBlockEntity::new,LECHO_CENIZA).build());

		// Terreno infectado: bloques completos y estables (la grava y la arena no caen). No cambian a sus vecinos.
		PIEDRA_INFECTADA = terreno("piedra_infectada", Blocks.STONE);
		terreno("pizarra_infectada", Blocks.DEEPSLATE);
		terreno("tierra_infectada", Blocks.DIRT);
		terreno("cesped_infectado", Blocks.DIRT);
		terreno("grava_infectada", Blocks.STONE);
		terreno("arena_infectada", Blocks.SANDSTONE);
		NETHERRACK_INFECTADA = terreno("netherrack_infectada", Blocks.NETHERRACK);
		PIEDRA_NEGRA_INFECTADA = terreno("piedra_negra_infectada", Blocks.BLACKSTONE);

		// Decoración temática: solo ambientación.
		VoxelShape lleno = Shapes.block();
		decor("raiz_ceniza", Blocks.DARK_OAK_LOG, lleno, 5, false);
		decor("raicillas_infectadas", Blocks.DARK_OAK_LOG, Block.box(1, 0, 1, 15, 16, 15), 0, true);
		decor("losa_ceniza", Blocks.DIRT, Block.box(0, 0, 0, 16, 2, 16), 0, false);
		decor("velas_notario", Blocks.CANDLE, Block.box(4, 0, 4, 12, 6, 12), 8, false);
		decor("urna_animas", Blocks.DEEPSLATE, Block.box(3, 0, 3, 13, 12, 13), 3, false);
		decor("archivo_condenas", Blocks.DARK_OAK_PLANKS, lleno, 0, false);
		decor("mesa_notario", Blocks.DARK_OAK_PLANKS, Shapes.or(Block.box(0, 12, 0, 16, 16, 16),
				Block.box(1, 0, 1, 3, 12, 3), Block.box(13, 0, 1, 15, 12, 3), Block.box(1, 0, 13, 3, 12, 15),
				Block.box(13, 0, 13, 15, 12, 15)), 0, false);
		decor("lampara_animas", Blocks.LANTERN, Block.box(4, 0, 4, 12, 10, 12), 10, false);
		decor("piedra_registro", Blocks.DEEPSLATE, lleno, 0, false);
	}

	private ModBlocks() {
	}

	private static Block extra(String nombre, Block copia, java.util.function.Function<BlockBehaviour.Properties, Block> fabrica,
			java.util.function.UnaryOperator<BlockBehaviour.Properties> ajuste) {
		ResourceKey<Block> k = ResourceKey.create(Registries.BLOCK, TheSlaveMod.id(nombre));
		BlockBehaviour.Properties p = ajuste.apply(BlockBehaviour.Properties.ofFullCopy(copia).mapColor(copia.defaultBlockState().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)));
		Block b = Registry.register(BuiltInRegistries.BLOCK, k, fabrica.apply(p.setId(k)));
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, TheSlaveMod.id(nombre));
		Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		EXTRA.put(nombre, b);
		return b;
	}

	private static Block terreno(String nombre, Block copia) {
		return extra(nombre, copia, Block::new, p -> p);
	}

	private static Block decor(String nombre, Block copia, VoxelShape forma, int luz, boolean sinColision) {
		return extra(nombre, copia, p -> new DecorBlock(p, forma), p -> {
			BlockBehaviour.Properties q = p.noOcclusion().lightLevel(s -> luz);
			return sinColision ? q.noCollision() : q;
		});
	}

	public static void register() {
	}
}

