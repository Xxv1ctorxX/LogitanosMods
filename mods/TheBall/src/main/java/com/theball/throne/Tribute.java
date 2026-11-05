package com.theball.throne;

import com.theball.entity.AluminumBallEntity;
import com.theball.registry.ModComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * El "historial" de una bola: quién ha puesto cuánto papel y qué premios se han entregado ya. Viaja con la bola
 * (item ⇄ entidad), así sobrevive a recogerla, cambiarla de trono o fusionarla en el yunque.
 */
public final class Tribute {
	/** Bits de {@link #rewardsGiven}. */
	public static final int[] THRESHOLDS = {250, 500, 1000, 1500, 2000};
	public static final int BIT_FOOD = 0;
	public static final int BIT_BOOTS = 1;
	public static final int BIT_LEGGINGS = 2;
	public static final int BIT_CHESTPLATE = 3;
	public static final int BIT_CROWN = 4;

	private final Map<UUID, Contributor> contributors = new LinkedHashMap<>();
	private int rewardsGiven;

	public Tribute() {
	}

	public Tribute(List<Contributor> list, int rewardsGiven) {
		for (Contributor c : list) {
			this.contributors.merge(c.uuid(), c, (a, b) -> a.plus(b.name(), b.foil(), b.late()));
		}
		this.rewardsGiven = rewardsGiven;
	}

	// ------------------------------------------------------------------ item ⇄ datos

	public static Tribute fromStack(ItemStack stack) {
		return new Tribute(stack.getOrDefault(ModComponents.CONTRIBUTIONS, List.of()),
				stack.getOrDefault(ModComponents.REWARDS_GIVEN, 0));
	}

	public void applyTo(ItemStack stack) {
		if (contributors.isEmpty()) {
			stack.remove(ModComponents.CONTRIBUTIONS);
		} else {
			stack.set(ModComponents.CONTRIBUTIONS, toList());
		}
		if (rewardsGiven == 0) {
			stack.remove(ModComponents.REWARDS_GIVEN);
		} else {
			stack.set(ModComponents.REWARDS_GIVEN, rewardsGiven);
		}
	}

	public Tribute copy() {
		return new Tribute(toList(), rewardsGiven);
	}

	// ------------------------------------------------------------------ aportes

	/** {@code late}: cuántos de esos papeles se pusieron en el tramo final (decide quién es el Rey). */
	public void add(UUID uuid, String name, int foil, int late) {
		if (foil <= 0) {
			return;
		}
		contributors.merge(uuid, new Contributor(uuid, name, foil, late), (a, b) -> a.plus(name, foil, late));
	}

	public int lateOf(UUID uuid) {
		Contributor c = contributors.get(uuid);
		return c == null ? 0 : c.late();
	}

	public int foilOf(UUID uuid) {
		Contributor c = contributors.get(uuid);
		return c == null ? 0 : c.foil();
	}

	public int totalFoil() {
		int total = 0;
		for (Contributor c : contributors.values()) {
			total += c.foil();
		}
		return total;
	}

	/**
	 * Porcentaje del papel de referencia (lo que cuesta una Reina de 9 a 2000 hojas) que ha puesto un jugador.
	 * Se mide contra la referencia fija y no contra el total de la bola: así nadie gana porcentaje porque otros
	 * hayan aportado poco.
	 */
	public double sharePercent(UUID uuid) {
		return 100.0 * foilOf(uuid) / referenceFoil();
	}

	public List<Contributor> toList() {
		return new ArrayList<>(contributors.values());
	}

	public List<Contributor> top(int n) {
		return contributors.values().stream()
				.sorted(Comparator.comparingInt(Contributor::foil).reversed())
				.limit(n)
				.toList();
	}

	public boolean isEmpty() {
		return contributors.isEmpty();
	}

	// ------------------------------------------------------------------ premios

	public int rewardsGiven() {
		return rewardsGiven;
	}

	public boolean isGiven(int bit) {
		return (rewardsGiven & (1 << bit)) != 0;
	}

	public void markGiven(int bit) {
		rewardsGiven |= 1 << bit;
	}

	public void setRewardsGiven(int bits) {
		rewardsGiven = bits;
	}

	/** Fusión en el yunque: aportes sumados por jugador, premios entregados en cualquiera de las dos cuentan. */
	public static Tribute merge(Tribute a, Tribute b) {
		Tribute result = a.copy();
		for (Contributor c : b.contributors.values()) {
			result.add(c.uuid(), c.name(), c.foil(), c.late());
		}
		result.rewardsGiven = a.rewardsGiven | b.rewardsGiven;
		return result;
	}

	// ------------------------------------------------------------------ referencia

	private static int referenceFoil = -1;

	/** Papel que cuesta una Reina entera (Σ foilCostFor de 9 a 1999 = 1.991). Se calcula con la función real. */
	public static int referenceFoil() {
		if (referenceFoil < 0) {
			int total = 0;
			for (int s = com.theball.item.AluminumBallItem.DEFAULT_SHEETS; s < AluminumBallEntity.MAX_SHEETS; s++) {
				total += AluminumBallEntity.foilCostFor(s);
			}
			referenceFoil = Math.max(1, total);
		}
		return referenceFoil;
	}
}
