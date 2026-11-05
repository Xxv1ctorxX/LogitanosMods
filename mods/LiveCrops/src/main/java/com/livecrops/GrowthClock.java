package com.livecrops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class GrowthClock extends SavedData {
    private record State(double growth, List<Double> invalidated) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("growth").forGetter(State::growth),
                Codec.doubleRange(0, Double.MAX_VALUE).listOf().fieldOf("invalidated").forGetter(State::invalidated)
        ).apply(i, State::new));
    }
    private static final Codec<GrowthClock> CODEC = State.CODEC.xmap(GrowthClock::new, GrowthClock::snapshot);
    public static final SavedDataType<GrowthClock> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("livecrops", "clock"), GrowthClock::new, CODEC, null);
    private double growth;
    private final double[] invalidated = new double[9];

    public GrowthClock() {}
    private GrowthClock(State state) {
        this();
        growth = state.growth;
        for (int i = 0; i < Math.min(9, state.invalidated.size()); i++) invalidated[i] = state.invalidated.get(i);
    }
    private synchronized State snapshot() {
        List<Double> revisions = new ArrayList<>(9);
        for (double value : invalidated) revisions.add(value);
        return new State(growth, revisions);
    }
    public synchronized double value() { return growth; }
    public synchronized void advance(double amount) {
        if (amount > 0) { growth += amount; setDirty(); }
    }
    public synchronized void invalidate(int kind) { invalidated[kind]++; setDirty(); }
    public synchronized double revision(int kind) { return invalidated[kind]; }
    public synchronized boolean invalidated(int kind, double admitted) {
        return admitted < invalidated[kind];
    }
}
