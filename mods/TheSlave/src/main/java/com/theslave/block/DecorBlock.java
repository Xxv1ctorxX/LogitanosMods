package com.theslave.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bloque decorativo de los enclaves: solo forma. No tiene lógica, no se propaga y no da recursos. */
public class DecorBlock extends Block {
	private final VoxelShape forma;

	public DecorBlock(Properties properties, VoxelShape forma) {
		super(properties);
		this.forma = forma;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return forma;
	}
}
