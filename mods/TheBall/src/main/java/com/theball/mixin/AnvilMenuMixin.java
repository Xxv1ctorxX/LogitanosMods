package com.theball.mixin;

import com.theball.entity.AluminumBallEntity;
import com.theball.item.AluminumBallItem;
import com.theball.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fusiona dos bolas de aluminio en el yunque: el resultado suma las hojas de las dos. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
	@Shadow
	private int repairItemCountCost;
	@Shadow
	private boolean onlyRenaming;
	@Shadow
	@Final
	private DataSlot cost;

	private AnvilMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition definition) {
		super(type, id, inventory, access, definition);
	}

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void theball$mergeBalls(CallbackInfo ci) {
		ItemStack left = this.inputSlots.getItem(0);
		ItemStack right = this.inputSlots.getItem(1);
		if (!left.is(ModItems.ALUMINUM_BALL) || !right.is(ModItems.ALUMINUM_BALL)) {
			return;
		}

		int total = AluminumBallItem.getSheets(left) + AluminumBallItem.getSheets(right);
		ItemStack result = AluminumBallItem.create(Math.min(total, AluminumBallEntity.MAX_SHEETS));

		this.onlyRenaming = false;
		this.repairItemCountCost = 0; // se consumen las dos bolas enteras
		this.cost.set(Math.min(39, 2 + total / 50));
		this.resultSlots.setItem(0, result);
		broadcastChanges();
		ci.cancel();
	}
}
