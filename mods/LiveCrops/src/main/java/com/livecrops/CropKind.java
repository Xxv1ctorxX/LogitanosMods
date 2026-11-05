package com.livecrops;

import com.livecrops.mixin.CropBlockAccessor;
import com.livecrops.mixin.StemBlockAccessor;
import java.util.random.RandomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public enum CropKind {
    WHEAT(Blocks.WHEAT, 7, CropBlock.AGE),
    CARROTS(Blocks.CARROTS, 7, CropBlock.AGE),
    POTATOES(Blocks.POTATOES, 7, CropBlock.AGE),
    BEETROOTS(Blocks.BEETROOTS, 3, BeetrootBlock.AGE),
    NETHER_WART(Blocks.NETHER_WART, 3, NetherWartBlock.AGE),
    COCOA(Blocks.COCOA, 2, CocoaBlock.AGE),
    SWEET_BERRY(Blocks.SWEET_BERRY_BUSH, 3, SweetBerryBushBlock.AGE),
    PUMPKIN(Blocks.PUMPKIN_STEM, 7, StemBlock.AGE),
    MELON(Blocks.MELON_STEM, 7, StemBlock.AGE);

    public static final CropKind[] ALL = values();
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    public final Block block;
    public final int maxAge;
    public final IntegerProperty ageProperty;
    CropKind(Block block, int maxAge, IntegerProperty property) {
        this.block = block; this.maxAge = maxAge; this.ageProperty = property;
    }
    public String id() { return "minecraft:" + LiveCropsConfig.IDS[ordinal()]; }
    public boolean enabled(LiveCropsConfig config) { return config.crops.getOrDefault(id(), false); }
    public boolean stem() { return this == PUMPKIN || this == MELON; }
    public Block attached() { return this == PUMPKIN ? Blocks.ATTACHED_PUMPKIN_STEM : Blocks.ATTACHED_MELON_STEM; }
    public Block fruit() { return this == PUMPKIN ? Blocks.PUMPKIN : Blocks.MELON; }
    public boolean matches(BlockState state) { return state.is(block) || stem() && state.is(attached()); }
    public boolean attached(BlockState state) { return stem() && state.is(attached()); }
    public int age(BlockState state) { return attached(state) ? 7 : state.getValue(ageProperty); }
    public boolean finished(BlockState state, LiveCropsConfig config) {
        return !stem() && age(state) >= maxAge || stem() && !config.stemsProduceFruit && age(state) >= maxAge;
    }
    public static CropKind of(BlockState state) {
        for (CropKind kind : ALL) if (kind.matches(state)) return kind;
        return null;
    }

    /** Los vecinos se comprueban antes; el getter vanilla no provocará cargas. */
    public double probability(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.canSurvive(level, pos) || attached(state)) return 0;
        if (this == NETHER_WART) return 0.1;
        if (this == COCOA) return 0.2;
        if (this == SWEET_BERRY) return level.getRawBrightness(pos.above(), 0) >= 9 ? 0.2 : 0;
        if (level.getRawBrightness(pos, 0) < 9) return 0;
        float points = CropBlockAccessor.livecrops$growthSpeed(block, level, pos);
        double probability = 1.0 / ((int) (25.0F / points) + 1);
        return this == BEETROOTS ? probability * (2.0 / 3.0) : probability;
    }

    /** Una sola fruta; usa tags reales del tallo y solo vecinos residentes. */
    public int fruitSpaces(ServerLevel level, BlockPos pos) {
        int mask = 0;
        for (int n = 0; n < 4; n++) {
            Direction direction = DIRECTIONS[n];
            BlockPos target = pos.relative(direction);
            if (!level.getBlockState(target).isAir()) continue;
            if (!level.getBlockState(target.below()).is(((StemBlockAccessor) block).livecrops$fruitSupport())) continue;
            mask |= 1 << n;
        }
        return mask;
    }

    public boolean produceFruit(ServerLevel level, BlockPos pos, RandomGenerator random) {
        int mask = fruitSpaces(level, pos);
        if (mask == 0) return false;
        int chosen = random.nextInt(Integer.bitCount(mask));
        for (int n = 0; n < 4; n++) {
            if ((mask & (1 << n)) == 0 || chosen-- > 0) continue;
            Direction direction = DIRECTIONS[n];
            BlockPos target = pos.relative(direction);
            if (!level.setBlock(target, fruit().defaultBlockState(), Block.UPDATE_ALL)) return false;
            level.setBlock(pos, attached().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }
}
