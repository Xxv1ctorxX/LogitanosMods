package com.ludopathyroulette;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;

public final class RouletteBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public final boolean casino;
    public RouletteBlock(Properties p,boolean casino) {
        super(p); this.casino=casino;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new RouletteBlockEntity(p,s);}
    protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){return use(s,l,p,player,hit);}
    protected InteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return use(s,l,p,player,hit);}
    private InteractionResult use(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if(player instanceof ServerPlayer sp && RouletteMod.service!=null) {
            boolean hatch=hit.getDirection()==s.getValue(FACING).getClockWise();
            RouletteMod.service.open(sp,p,hatch);
        }
        return InteractionResult.SUCCESS;
    }
    public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity entity,ItemStack stack) {
        super.setPlacedBy(l,p,s,entity,stack);
        if(l.isClientSide() || !(l.getBlockEntity(p) instanceof RouletteBlockEntity be)) return;
        // Solo un traslado firmado en el registro puede reclamar una identidad existente.
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);
        if(data!=null && entity instanceof ServerPlayer player) RouletteMod.service.place(player,be,data.copyTag().getStringOr("RouletteId",""));
        RouletteMod.service.ensure(be);
    }
}
