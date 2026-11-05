package com.livecrops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Arrays primitivos residentes solo mientras Minecraft mantiene el chunk cargado. */
public final class CropChunkData {
    private static final Codec<Double> NONNEGATIVE = Codec.doubleRange(0, Double.MAX_VALUE);
    private record Entry(long pos, int kind, double consumed, double admitted) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                Codec.intRange(0, 8).fieldOf("kind").forGetter(Entry::kind),
                NONNEGATIVE.fieldOf("consumed").forGetter(Entry::consumed),
                NONNEGATIVE.fieldOf("admitted").forGetter(Entry::admitted)
        ).apply(i, Entry::new));
    }
    private record Snapshot(int version, double accumulated, double anchor, List<Entry> crops) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1).fieldOf("version").forGetter(Snapshot::version),
                NONNEGATIVE.fieldOf("accumulated").forGetter(Snapshot::accumulated),
                NONNEGATIVE.fieldOf("anchor").forGetter(Snapshot::anchor),
                Entry.CODEC.listOf().validate(list -> list.size() <= 4096
                        ? DataResult.success(list) : DataResult.error(() -> "Demasiados cultivos en un chunk"))
                        .fieldOf("crops").forGetter(Snapshot::crops)
        ).apply(i, Snapshot::new));
    }
    public static final Codec<CropChunkData> CODEC = Snapshot.CODEC.xmap(CropChunkData::new, CropChunkData::snapshot);
    final DormantLedger ledger;
    private long[] positions = new long[0];
    private byte[] kinds = new byte[0];
    private double[] consumed = new double[0];
    private double[] admitted = new double[0];
    private int size;

    public CropChunkData(double clock) { ledger = new DormantLedger(0, clock); }

    private CropChunkData(Snapshot snapshot) {
        ledger = new DormantLedger(snapshot.accumulated, snapshot.anchor);
        for (Entry entry : snapshot.crops) {
            if (find(entry.pos) >= 0) continue;
            add(entry.pos, entry.kind, entry.consumed, entry.admitted);
        }
    }

    private synchronized Snapshot snapshot() {
        List<Entry> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(new Entry(positions[i], kinds[i], consumed[i], admitted[i]));
        return new Snapshot(1, ledger.accumulated(), ledger.anchor(), result);
    }

    public synchronized int size() { return size; }
    public synchronized int find(long pos) {
        for (int i = 0; i < size; i++) if (positions[i] == pos) return i;
        return -1;
    }
    public synchronized void add(long pos, int kind, double baseline, double clock) {
        if (size == positions.length) {
            int capacity = Math.min(4096, Math.max(16, size * 2));
            if (size == capacity) throw new IllegalStateException("Límite duro de chunk");
            positions = Arrays.copyOf(positions, capacity);
            kinds = Arrays.copyOf(kinds, capacity);
            consumed = Arrays.copyOf(consumed, capacity);
            admitted = Arrays.copyOf(admitted, capacity);
        }
        positions[size] = pos;
        kinds[size] = (byte) kind;
        consumed[size] = baseline;
        admitted[size++] = clock;
    }
    public synchronized void remove(int i) {
        int last = --size;
        positions[i] = positions[last]; kinds[i] = kinds[last];
        consumed[i] = consumed[last]; admitted[i] = admitted[last];
    }
    public synchronized long position(int i) { return positions[i]; }
    public synchronized int kind(int i) { return kinds[i]; }
    public synchronized double consumed(int i) { return consumed[i]; }
    public synchronized double admitted(int i) { return admitted[i]; }
    public synchronized void consume(int i, double target) { consumed[i] = Math.max(consumed[i], target); }
    public synchronized double effective(double clock) { return ledger.effective(clock); }
    public synchronized void attended(double before, double after) { ledger.attended(before, after); }
    public synchronized boolean repairFuture(double clock) {
        if (ledger.anchor() <= clock) return false;
        ledger.rebase(clock);
        for (int i = 0; i < size; i++) consumed[i] = Math.max(consumed[i], ledger.accumulated());
        return true;
    }
}
