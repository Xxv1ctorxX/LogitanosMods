package com.livecrops;

import java.util.random.RandomGenerator;

/** Matemática independiente del juego, comprobable sin arrancar Minecraft. */
public final class GrowthMath {
    private GrowthMath() {}

    public static double increment(int randomTickSpeed, double rate) {
        if (randomTickSpeed < 0 || !Double.isFinite(rate) || rate < 0 || rate > 1) {
            throw new IllegalArgumentException("Tasa de crecimiento inválida");
        }
        return randomTickSpeed * rate / 4096.0;
    }

    /** Poisson truncada: como máximo cap pasos, sin bucles por tiempo ausente. */
    public static int sample(double lambda, int cap, RandomGenerator random) {
        if (cap <= 0 || lambda <= 0) return 0;
        if (!Double.isFinite(lambda)) throw new IllegalArgumentException("Intensidad no finita");
        double mass = Math.exp(-lambda);
        double cumulative = mass;
        double draw = random.nextDouble();
        for (int count = 0; count < cap; count++) {
            if (draw < cumulative) return count;
            mass *= lambda / (count + 1.0);
            cumulative += mass;
        }
        return cap;
    }

    public static double debt(double target, double consumed) {
        return Math.max(0, target - consumed);
    }

    /** Etapas exponenciales: madurar y después producir con probabilidad espacial. */
    public static int stemStages(double intensity, double probability, int remaining, double spaceFraction, RandomGenerator random) {
        if (intensity <= 0 || probability <= 0) return 0;
        int stages = 0;
        for (; stages < remaining; stages++) {
            double wait = -Math.log1p(-random.nextDouble()) / probability;
            if (wait > intensity) return stages;
            intensity -= wait;
        }
        if (spaceFraction > 0 && random.nextDouble() < -Math.expm1(-intensity * probability * spaceFraction)) stages++;
        return stages;
    }
}
