package com.theball.power;

import com.theball.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** La "gasolina" del cetro: papel de aluminio de cualquier ranura del inventario. En creativo es gratis. */
public final class FoilFuel {
	private FoilFuel() {
	}

	public static int count(Player player) {
		Inventory inv = player.getInventory();
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(ModItems.ALUMINUM_FOIL)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static boolean has(Player player, int amount) {
		return amount <= 0 || player.hasInfiniteMaterials() || count(player) >= amount;
	}

	/** Gasta el papel. Si no hay suficiente no gasta nada, avisa y devuelve false. */
	public static boolean take(Player player, int amount) {
		if (amount <= 0 || player.hasInfiniteMaterials()) {
			return true;
		}
		if (count(player) < amount) {
			player.sendOverlayMessage(Component.translatable("message.theball.scepter.no_foil", amount)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		Inventory inv = player.getInventory();
		int left = amount;
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(ModItems.ALUMINUM_FOIL)) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
			}
		}
		inv.setChanged();
		return true;
	}
}
