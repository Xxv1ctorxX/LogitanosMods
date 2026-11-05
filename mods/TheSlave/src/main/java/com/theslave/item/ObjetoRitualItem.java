package com.theslave.item;

import com.theslave.registry.ModComponents;
import com.theslave.registry.ModComponents.Almas;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Relicario, ofrenda, tributos y sello: objetos con almas que muestran sus contadores. */
public class ObjetoRitualItem extends Item {
	public ObjetoRitualItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> out, TooltipFlag flag) {
		Almas a = stack.getOrDefault(ModComponents.ALMAS, Almas.VACIO);
		out.accept(Component.translatable("theslave.alma.hambrienta", a.hambrienta()).withStyle(ChatFormatting.GREEN));
		out.accept(Component.translatable("theslave.alma.vigilante", a.vigilante()).withStyle(ChatFormatting.AQUA));
		out.accept(Component.translatable("theslave.alma.acechante", a.acechante()).withStyle(ChatFormatting.LIGHT_PURPLE));
		if (stack.is(com.theslave.registry.ModItems.RELICARIO)) {
			out.accept(Component.translatable("theslave.relicario.capacidad", a.total(), Almas.CAPACIDAD)
					.withStyle(ChatFormatting.GRAY));
		}
	}
}
