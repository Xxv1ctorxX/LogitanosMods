package com.theball.registry;

import com.theball.TheBallMod;
import com.theball.item.AluminumBallItem;
import com.theball.item.FoilFoodItem;
import com.theball.item.GoalItem;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	/** Bocata: devuelve 1 papel de aluminio. */
	public static final Item BOCATA = register("bocata", props -> new FoilFoodItem(props, 1),
			new Item.Properties().food(new FoodProperties.Builder()
					.nutrition(8)
					.saturationModifier(0.8f)
						.alwaysEdible()
					.build()));

	/** Durum: devuelve 3 papeles de aluminio. */
	public static final Item DURUM = register("durum", props -> new FoilFoodItem(props, 3),
			new Item.Properties().food(new FoodProperties.Builder()
					.nutrition(10)
					.saturationModifier(0.9f)
						.alwaysEdible()
					.build()));

	/** Portería de fútbol 11 (entidad). */
	public static final Item GOAL = register("goal", GoalItem::new, new Item.Properties().stacksTo(4));

	public static final Item RAW_ALUMINUM = register("raw_aluminum", Item::new, new Item.Properties());

	public static final Item ALUMINUM_INGOT = register("aluminum_ingot", Item::new, new Item.Properties());

	public static final Item ALUMINUM_FOIL = register("aluminum_foil", Item::new, new Item.Properties());

	public static final Item ALUMINUM_BALL = register("aluminum_ball", AluminumBallItem::new,
			new Item.Properties()
					.stacksTo(1)
					.component(ModComponents.SHEETS, AluminumBallItem.DEFAULT_SHEETS));

	public static final ResourceKey<CreativeModeTab> TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, TheBallMod.id("the_ball"));

	public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(ALUMINUM_BALL))
			.title(Component.translatable("itemGroup.theball"))
			.displayItems((parameters, output) -> {
				output.accept(BOCATA);
				output.accept(DURUM);
				output.accept(ModBlocks.ALUMINUM_ORE);
				output.accept(ModBlocks.DEEPSLATE_ALUMINUM_ORE);
				output.accept(GOAL);
				output.accept(ModBlocks.CONE);
				output.accept(RAW_ALUMINUM);
				output.accept(ALUMINUM_INGOT);
				output.accept(ModBlocks.ALUMINUM_BLOCK);
				output.accept(ALUMINUM_FOIL);
				output.accept(AluminumBallItem.create(AluminumBallItem.DEFAULT_SHEETS));
				output.accept(AluminumBallItem.create(100));
				output.accept(AluminumBallItem.create(500));
				output.accept(AluminumBallItem.create(1000));
				output.accept(AluminumBallItem.create(1500));
				output.accept(AluminumBallItem.create(2000));
			})
			.build();

	private ModItems() {
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TheBallMod.id(name));
		// Desde 1.21.2 es obligatorio indicar la clave del item en sus Properties.
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
	}
}
