package com.livecrops;

/** Recorrido horizontal por chunks, sin listas de posiciones ni referencias al mundo. */
public final class DiscoveryArea {
    private final int centerX, centerZ, radius;
    private final int minX, minZ, width, depth;

    public DiscoveryArea(int centerX, int centerZ, int radius) {
        if (radius < 0 || radius > 512) throw new IllegalArgumentException("Radio entre 0 y 512 bloques");
        this.centerX = centerX; this.centerZ = centerZ; this.radius = radius;
        minX = Math.toIntExact(Math.floorDiv((long) centerX - radius, 16));
        minZ = Math.toIntExact(Math.floorDiv((long) centerZ - radius, 16));
        width = Math.toIntExact(Math.floorDiv((long) centerX + radius, 16) - minX + 1);
        depth = Math.toIntExact(Math.floorDiv((long) centerZ + radius, 16) - minZ + 1);
    }

    public int candidateCount() { return width * depth; }
    public int chunkX(int index) { checkIndex(index); return minX + index % width; }
    public int chunkZ(int index) { checkIndex(index); return minZ + index / width; }
    public boolean intersects(int index) {
        long x = (long) chunkX(index) * 16;
        long z = (long) chunkZ(index) * 16;
        long closestX = Math.clamp((long) centerX, x, x + 15);
        long closestZ = Math.clamp((long) centerZ, z, z + 15);
        long dx = centerX - closestX, dz = centerZ - closestZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }
    private void checkIndex(int index) {
        if (index < 0 || index >= candidateCount()) throw new IndexOutOfBoundsException(index);
    }
}
