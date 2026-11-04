package com.theball.item;

import com.theball.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Comida envuelta en papel de aluminio: al terminar de comerla devuelve {@code foilCount} papeles.
 * Bocata = 1, durum = 3.
 */
public class FoilFoodItem extends Item {
	private final int foilCount;

	public FoilFoodItem(Properties properties, int foilCount) {
		super(properties);
		this.foilCount = foilCount;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack result = super.finishUsingItem(stack, level, entity);

		if (!level.isClientSide() && entity instanceof Player player) {
			ItemStack foil = new ItemStack(ModItems.ALUMINUM_FOIL, this.foilCount);
			// Lo que no quepa en el inventario cae al suelo.
			if (!player.getInventory().add(foil) && !foil.isEmpty()) {
				player.drop(foil, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}

		return result;
	}
}
