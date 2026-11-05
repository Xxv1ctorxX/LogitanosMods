package com.theslave.block;

import com.theslave.ritual.Ceremonias;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Altar de Ceniza. El frontal (símbolo del Notario) mira al jugador que lo coloca; La Muerte aparece siempre por detrás.
 * Las cuatro velas forman parte del modelo y solo las enciende la ceremonia.
 */
public class AltarBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty[] VELAS = {
			BooleanProperty.create("vela_0"), BooleanProperty.create("vela_1"),
			BooleanProperty.create("vela_2"), BooleanProperty.create("vela_3") };
	private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 10, 16);

	public AltarBlock(Properties properties) {
		super(properties);
		BlockState s = stateDefinition.any().setValue(FACING, Direction.NORTH);
		for (BooleanProperty v : VELAS) {
			s = s.setValue(v, false);
		}
		registerDefaultState(s);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, VELAS[0], VELAS[1], VELAS[2], VELAS[3]);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return FORMA;
	}

	/** Si el servidor se cayó a media ceremonia, las velas que quedaron encendidas se apagan solas. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (Ceremonias.activa(pos)) {
			level.scheduleTick(pos, this, 20);
			return;
		}
		boolean alguna = false;
		BlockState nuevo = state;
		for (BooleanProperty v : VELAS) {
			alguna |= state.getValue(v);
			nuevo = nuevo.setValue(v, false);
		}
		if (alguna) {
			level.setBlock(pos, nuevo, 3);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		return Ceremonias.alUsar(sp, hand, stack, pos);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			Ceremonias.alUsar(sp, InteractionHand.MAIN_HAND, ItemStack.EMPTY, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
