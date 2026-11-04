package com.theball.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Cono de entrenamiento: base cuadrada y cuerpo cónico estrecho, para hacer eslálom con la bola. */
public class ConeBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(2, 0, 2, 14, 1, 14),
			Block.box(4, 1, 4, 12, 4, 12),
			Block.box(5, 4, 5, 11, 7, 11),
			Block.box(6, 7, 6, 10, 9, 10),
			Block.box(7, 9, 7, 9, 11, 9));

	public ConeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
