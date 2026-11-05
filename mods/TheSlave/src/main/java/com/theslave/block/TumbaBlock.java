package com.theslave.block;

import com.theslave.Registro;
import com.theslave.registry.ModBlocks;
import com.theslave.registry.ModItems;
import com.theslave.ritual.Ritual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** La lápida del esclavo muerto. Indestructible salvo entierro del dueño (agachado, con pico, dos clics). */
public class TumbaBlock extends Block implements EntityBlock {
	private static final VoxelShape FORMA = Block.box(2, 0, 2, 14, 12, 14);

	public TumbaBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return FORMA;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TumbaBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModBlocks.TUMBA_BE) {
			return null;
		}
		return (l, p, s, be) -> TumbaBlockEntity.tick(l, p, s, (TumbaBlockEntity) be);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (stack.is(ModItems.LATIGO)) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("theslave.tumba.no_azotar"));
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(ItemTags.PICKAXES) && player.isShiftKeyDown()) {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
				enterrar(sl, pos, sp);
			}
			return InteractionResult.SUCCESS;
		}
		return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp) || player.isShiftKeyDown()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level.getBlockEntity(pos) instanceof TumbaBlockEntity be) || be.dueno == null || !be.dueno.equals(sp.getUUID())) {
			sp.sendOverlayMessage(Component.translatable("theslave.tumba.ajena"));
			return InteractionResult.SUCCESS;
		}
		Ritual.abrirLapida(sp, pos);
		return InteractionResult.SUCCESS;
	}

	private void enterrar(ServerLevel sl, BlockPos pos, ServerPlayer sp) {
        if(com.theslave.persistencia.CerrojosJugador.ocupada(sp.getUUID()))return;
		if (!(sl.getBlockEntity(pos) instanceof TumbaBlockEntity be) || be.dueno == null || !be.dueno.equals(sp.getUUID())) {
			sp.sendOverlayMessage(Component.translatable("theslave.tumba.ajena"));
			return;
		}
		long ahora = sl.getGameTime();
		if (be.confirmar != 0 && ahora - be.confirmar <= 100) {
			Ritual.cancelarEn(sl, pos);
            if(!com.theslave.ritual.MuerteDurable.enterrar(sp,be)) { be.confirmar=0;return; }
			sl.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
			sp.sendOverlayMessage(Component.translatable("theslave.tumba.enterrada"));
		} else {
			be.confirmar = ahora;
			sp.sendOverlayMessage(Component.translatable("theslave.tumba.seguro"));
		}
	}
}
