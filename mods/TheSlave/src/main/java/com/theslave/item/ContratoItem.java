package com.theslave.item;

import com.theslave.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Contrato de Condena. Ya no se craftea ni invoca nada por sí solo: se compra a un Notario de Ceniza y se entrega,
 * junto a la Ofrenda a la Muerte, ante un Altar de Ceniza. Los contratos antiguos (sin pacto) solo sirven para liberar.
 */
public class ContratoItem extends Item {
	public ContratoItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() instanceof ServerPlayer sp && !context.getLevel().isClientSide()) {
			boolean pacto = context.getItemInHand().has(ModComponents.PACTO);
			sp.sendSystemMessage(Component.translatable(pacto ? "theslave.contrato.usa_altar" : "theslave.contrato.antiguo"), true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> out, TooltipFlag flag) {
		ModComponents.Pacto p = stack.get(ModComponents.PACTO);
		if (p == null) {
			out.accept(Component.translatable("theslave.contrato.tooltip_antiguo").withStyle(ChatFormatting.GRAY));
			return;
		}
		out.accept(Component.translatable("theslave.contrato.tooltip_grado", p.grado() + 1).withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable("theslave.contrato.tooltip_altar").withStyle(ChatFormatting.DARK_GRAY));
	}
}
