package com.ludopathyroulette.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Matemática independiente del juego: pesos exactos, precios y trayectorias por tiempo absoluto. */
public final class WheelMath {
    public static final double TAU = 2 * Math.PI;
    private WheelMath() {}
    public static long sum(int[] weights) {
        if (weights.length < 2 || weights.length > 12) throw new IllegalArgumentException("2–12 casillas");
        long sum = 0;
        for (int w : weights) { if (w < 1 || w > 1_000_000) throw new IllegalArgumentException("Peso inválido"); sum += w; }
        return sum;
    }
    public static int select(int[] weights, long roll) {
        long total = sum(weights);
        if (roll < 0 || roll >= total) throw new IllegalArgumentException("Sorteo fuera de intervalo");
        long n = 0;
        for (int i = 0; i < weights.length; i++) { n += weights[i]; if (roll < n) return i; }
        throw new AssertionError();
    }
    public static double center(int[] weights, int index) {
        long total = sum(weights), before = 0;
        if (index < 0 || index >= weights.length) throw new IllegalArgumentException("Casilla inválida");
        for (int i = 0; i < index; i++) before += weights[i];
        return TAU * (before + weights[index] * .5) / total;
    }
    public static double target(int[] weights, int index, long seed) {
        return center(weights, index) + (new Random(seed).nextDouble() - .5) * .6 * TAU * weights[index] / sum(weights);
    }
    public static double mod(double value) { return (value % TAU + TAU) % TAU; }
    public static double progress(double elapsed, int duration) { return Math.clamp(elapsed / duration, 0, 1); }
    public static double fortuna(int[] weights, int winner, long seed, double elapsed, int duration) {
        double u = progress(elapsed, duration);
        double end = TAU * (2 + Math.floorMod(seed, 3)) + mod(-Math.PI / 2 - target(weights, winner, seed));
        return end * (1 - Math.pow(1-u, 5));
    }
    public record Ball(double rotor, double angle, double radius, double height) {}
    public static Ball casino(int[] weights, int winner, long seed, double elapsed, int duration) {
        double u = progress(elapsed, duration), smooth = u*u*(3-2*u);
        double rotor = 2 * TAU * (1 - Math.pow(1-u, 3));
        double end = 2 * TAU + target(weights, winner, seed);
        double angle = end - 8 * TAU * Math.pow(1-u, 2);
        double r = .94 - .30 * smooth;
        double h = u < .5 ? 0 : .08 * Math.pow(Math.sin(Math.PI * Math.clamp((u-.5)/.5,0,1)), 2) * Math.abs(Math.sin(12*Math.PI*u));
        return new Ball(rotor, angle, r, h);
    }
    public static int price(int base, int multiplier100) {
        if (base < 0 || multiplier100 < 100 || multiplier100 > 1000) throw new IllegalArgumentException("Precio inválido");
        return Math.toIntExact(((long)base * multiplier100 + 99) / 100);
    }
    public static int[] percentages(List<String> values) {
        if (values.size() < 2 || values.size() > 12) throw new IllegalArgumentException("2–12 porcentajes");
        BigDecimal total = BigDecimal.ZERO;
        int[] result = new int[values.size()];
        List<BigDecimal> remainders = new ArrayList<>();
        int allocated = 0;
        for (int i=0;i<values.size();i++) {
            BigDecimal p = new BigDecimal(values.get(i).replace(',', '.'));
            if (p.signum() <= 0) throw new IllegalArgumentException("Porcentaje positivo requerido");
            total = total.add(p);
            BigDecimal exact = p.multiply(BigDecimal.valueOf(10000));
            result[i] = exact.setScale(0, RoundingMode.DOWN).intValueExact();
            allocated += result[i]; remainders.add(exact.subtract(BigDecimal.valueOf(result[i])));
        }
        if (total.compareTo(BigDecimal.valueOf(100)) != 0) throw new IllegalArgumentException("El reparto debe sumar 100 %");
        List<Integer> order = new ArrayList<>(); for(int i=0;i<result.length;i++) order.add(i);
        order.sort((a,b) -> { int c = remainders.get(b).compareTo(remainders.get(a)); return c != 0 ? c : Integer.compare(a,b); });
        for (int i=0;i<1_000_000-allocated;i++) result[order.get(i)]++;
        sum(result); return result;
    }
}
