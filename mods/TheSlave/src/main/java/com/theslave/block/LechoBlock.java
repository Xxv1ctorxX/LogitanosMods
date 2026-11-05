package com.theslave.block;

import com.theslave.ritual.Hogares;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Lecho personal de dos bloques. Su cambio a tumba es un estado del mismo hogar. */
public final class LechoBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BedPart> PART=BlockStateProperties.BED_PART;
    public static final BooleanProperty GRAVE=BooleanProperty.create("grave");
    public static final BooleanProperty FUSIONADO=BooleanProperty.create("fusionado");
    public LechoBlock(Properties properties) {
        super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,BedPart.FOOT).setValue(GRAVE,false).setValue(FUSIONADO,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,PART,GRAVE,FUSIONADO); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new LechoBlockEntity(pos,state); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        boolean cabeza=state.getValue(PART)==BedPart.HEAD;
        return Block.box(0,0,0,16,state.getValue(GRAVE)?(cabeza?12:7):(cabeza?11:9),16);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(player instanceof ServerPlayer p)Hogares.usar(p,pos);return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer p)Hogares.usar(p,pos);return InteractionResult.SUCCESS;
    }
}
