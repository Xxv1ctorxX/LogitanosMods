package com.theslave.registry;

import com.mojang.serialization.MapCodec;
import com.theslave.TheSlaveMod;
import com.theslave.registry.ModComponents.Almas;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Receta de la Brújula de Ceniza. Cuarzo, hierro, cuarzo / hierro, redstone, hierro / netherrack, hierro, relicario con
 * al menos un Alma Vigilante. Consume exactamente un Alma Vigilante y devuelve el relicario con el resto de sus almas.
 */
public class BrujulaRecipe extends CustomRecipe {
	public static final RecipeSerializer<BrujulaRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			TheSlaveMod.id("brujula_ceniza"),
			new RecipeSerializer<>(MapCodec.unit(BrujulaRecipe::new), StreamCodec.unit(new BrujulaRecipe())));

	private static final Item[] PATRON = { Items.QUARTZ, Items.IRON_INGOT, Items.QUARTZ, Items.IRON_INGOT, Items.REDSTONE,
			Items.IRON_INGOT, Items.NETHERRACK, Items.IRON_INGOT };

	@Override
	public boolean matches(CraftingInput in, Level level) {
		if (in.width() != 3 || in.height() != 3) {
			return false;
		}
		for (int i = 0; i < PATRON.length; i++) {
			if (!in.getItem(i).is(PATRON[i])) {
				return false;
			}
		}
		ItemStack r = in.getItem(8);
        Almas almas = r.getOrDefault(ModComponents.ALMAS, Almas.VACIO);
        return r.is(ModItems.RELICARIO) && r.getCount() == 1 && almas.vigilante() >= 1 && almas.total() <= Almas.CAPACIDAD;
	}

	@Override
	public ItemStack assemble(CraftingInput in) {
		return new ItemStack(ModItems.BRUJULA);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput in) {
		NonNullList<ItemStack> l = CraftingRecipe.defaultCraftingReminder(in);
        if (!matches(in, null)) return l;
		ItemStack r = in.getItem(8);
		Almas a = r.getOrDefault(ModComponents.ALMAS, Almas.VACIO);
		ItemStack devuelto = r.copyWithCount(1);
		devuelto.set(ModComponents.ALMAS, new Almas(a.hambrienta(), a.vigilante() - 1, a.acechante()));
		l.set(8, devuelto);
		return l;
	}

	@Override
	public RecipeSerializer<? extends CustomRecipe> getSerializer() {
		return SERIALIZER;
	}

	public static void register() {
	}
}
