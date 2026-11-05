package com.theball.item;

import com.theball.registry.ModComponents;
import com.theball.throne.Contributor;
import com.theball.throne.Tribute;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Medalla del tributo: recuerdo para quien aportó al menos un 2 % del papel de una Reina. Cosmética. */
public class TributeMedalItem extends Item {
	public TributeMedalItem(Properties properties) {
		super(properties);
	}

	public static ItemStack create(Contributor who) {
		ItemStack stack = new ItemStack(com.theball.registry.ModItems.TRIBUTE_MEDAL);
		if (who != null) {
			stack.set(ModComponents.MEDAL, who);
		}
		return stack;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		Contributor who = stack.get(ModComponents.MEDAL);
		if (who == null) {
			tooltip.accept(Component.translatable("tooltip.theball.medal.generic").withStyle(ChatFormatting.GRAY));
			return;
		}
		tooltip.accept(Component.translatable("tooltip.theball.medal.owner", who.name()).withStyle(ChatFormatting.GOLD));
		if (who.foil() > 0) {
			tooltip.accept(Component.translatable("tooltip.theball.medal.foil", who.foil(),
					String.format("%.1f", 100.0 * who.foil() / Tribute.referenceFoil())).withStyle(ChatFormatting.GRAY));
		}
	}
}
