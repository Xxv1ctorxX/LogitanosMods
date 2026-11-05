package com.theball.block;

import com.theball.block.entity.ThroneBlockEntity;
import com.theball.registry.ModItems;
import com.theball.throne.ThroneLogic;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trono de aluminio: la peana donde se sienta la bola. La bola sentada crece, paga XP y reparte premios
 * (ver {@link ThroneLogic}). FACING es hacia donde mira el asiento (hacia el jugador que lo colocó).
 */
public class ThroneBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** Altura del asiento, donde apoya la base de la bola (en bloques, desde la base del trono). */
	public static final double SEAT_Y = 9.0 / 16.0;

	/** Piezas del trono mirando al sur (el respaldo queda al norte), en píxeles. */
	private static final double[][] BOXES_SOUTH = {
			{0, 0, 0, 16, 2, 16},     // peana
			{2, 2, 3, 14, 9, 15},     // asiento con cojín
			{1, 2, 0, 15, 16, 3},     // respaldo
			{0, 2, 3, 2, 13, 16},     // brazo oeste
			{14, 2, 3, 16, 13, 16}    // brazo este
	};

	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			VoxelShape shape = Shapes.empty();
			for (double[] b : BOXES_SOUTH) {
				shape = Shapes.or(shape, rotated(b, facing));
			}
			SHAPES.put(facing, shape.optimize());
		}
	}

	/** Gira una caja (definida mirando al sur) alrededor del centro del bloque. */
	private static VoxelShape rotated(double[] b, Direction facing) {
		double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
		return switch (facing) {
			case NORTH -> Block.box(16 - x2, b[1], 16 - z2, 16 - x1, b[4], 16 - z1);
			case EAST -> Block.box(z1, b[1], 16 - x2, z2, b[4], 16 - x1);
			case WEST -> Block.box(16 - z2, b[1], x1, 16 - z1, b[4], x2);
			default -> Block.box(x1, b[1], z1, x2, b[4], z2);
		};
	}

	public ThroneBlock(Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ThroneBlockEntity(pos, state);
	}

	// ------------------------------------------------------------------ interacción

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (stack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (stack.is(ModItems.ALUMINUM_BALL)) {
			if (level instanceof ServerLevel serverLevel) {
				ThroneLogic.seat(serverLevel, pos, player, hand);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(ModItems.ALUMINUM_FOIL)) {
			// Click en el trono con papel: alimenta la bola sentada (como si clicaras la bola).
			if (level instanceof ServerLevel serverLevel) {
				var ball = ThroneLogic.seatedBall(serverLevel, pos);
				if (ball != null) {
					ThroneLogic.feed(ball, player, hand);
				} else {
					ThroneLogic.statusFor(serverLevel, pos, player);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel) {
			var ball = ThroneLogic.seatedBall(serverLevel, pos);
			if (player.isShiftKeyDown() && ball != null) {
				ThroneLogic.unseat(ball, player);
			} else {
				ThroneLogic.statusFor(serverLevel, pos, player);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
