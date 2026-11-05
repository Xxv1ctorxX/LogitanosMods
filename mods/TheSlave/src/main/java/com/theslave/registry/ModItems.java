package com.theslave.registry;

import com.theslave.TheSlaveMod;
import com.theslave.item.BrujulaItem;
import com.theslave.item.ContratoItem;
import com.theslave.item.LatigoItem;
import com.theslave.item.ObjetoRitualItem;
import com.theslave.registry.ModComponents.Almas;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class ModItems {
	public static final Item CONTRATO = register("contrato", ContratoItem::new, new Item.Properties()
			.component(DataComponents.LORE, new ItemLore(List.of(
					Component.translatable("item.theslave.contrato.lore1"),
					Component.translatable("item.theslave.contrato.lore2")))));

	public static final Item LATIGO = register("latigo", LatigoItem::new, new Item.Properties()
			.stacksTo(1).durability(64).repairable(Items.LEATHER));

	private static Item.Properties conAlmas() {
		return new Item.Properties().stacksTo(1).component(ModComponents.ALMAS, Almas.VACIO);
	}

	public static final Item RELICARIO = register("relicario_animas", ObjetoRitualItem::new, conAlmas());
	public static final Item OFRENDA = register("ofrenda_muerte", ObjetoRitualItem::new, conAlmas());
	public static final Item TRIBUTO = register("tributo_condena", ObjetoRitualItem::new, conAlmas());
	public static final Item SELLO = register("sello_almas", ObjetoRitualItem::new, conAlmas());
	public static final Item BRUJULA = register("brujula_ceniza", BrujulaItem::new, new Item.Properties().stacksTo(1));
	public static final Item DOBLE = register("tributo_doble_condena", ObjetoRitualItem::new, conAlmas());
    public static final Item ALMA_VINCULADA=register("alma_vinculada",Item::new,new Item.Properties().stacksTo(1));

	public static final ResourceKey<CreativeModeTab> TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, TheSlaveMod.id("the_slave"));

	public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(LATIGO))
			.title(Component.translatable("itemGroup.theslave"))
			.displayItems((parameters, output) -> {
				output.accept(CONTRATO);
				output.accept(LATIGO);
				output.accept(RELICARIO);
				output.accept(BRUJULA);
				output.accept(ModBlocks.ALTAR);
				output.accept(ModBlocks.TUMBA);
                output.accept(ModBlocks.LECHO_CENIZA);
				ModBlocks.EXTRA.values().forEach(output::accept);
			})
			.build();

	private ModItems() {
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TheSlaveMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
	}
}
