package com.theslave.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Mochila del esclavo: 27 huecos. Todo lo que entra lo hace por {@link #insertar}, siempre con una {@link Fuente}. */
public final class Inv {
	public static final int SIZE = 27;

	/** Origen de un item (invariante del mod: nada aparece de la nada). */
	public enum Fuente {
		MINADO, CRAFTEO, FUNDIDO, DUENO, RECOGIDO
	}

	private final ItemStack[] slots = new ItemStack[SIZE];

	public Inv() {
		for (int i = 0; i < SIZE; i++) {
			slots[i] = ItemStack.EMPTY;
		}
	}

	public ItemStack get(int i) {
		return slots[i];
	}

	public void set(int i, ItemStack s) {
		slots[i] = s;
	}

	public List<ItemStack> stacks() {
		List<ItemStack> l = new ArrayList<>();
		for (ItemStack s : slots) {
			if (!s.isEmpty()) {
				l.add(s);
			}
		}
		return l;
	}

	public int count(Item item) {
		int n = 0;
		for (ItemStack s : slots) {
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	public int count(Predicate<ItemStack> p) {
		int n = 0;
		for (ItemStack s : slots) {
			if (!s.isEmpty() && p.test(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	public boolean isEmpty() {
		for (ItemStack s : slots) {
			if (!s.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Mete el item; devuelve lo que no cupo. */
	public ItemStack insertar(ItemStack in, Fuente fuente) {
		ItemStack rest = in.copy();
		for (int i = 0; i < SIZE && !rest.isEmpty(); i++) {
			ItemStack s = slots[i];
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, rest) && s.getCount() < s.getMaxStackSize()) {
				int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
				s.grow(move);
				rest.shrink(move);
			}
		}
		for (int i = 0; i < SIZE && !rest.isEmpty(); i++) {
			if (slots[i].isEmpty()) {
				int move = Math.min(rest.getCount(), rest.getMaxStackSize());
				slots[i] = rest.copyWithCount(move);
				rest.shrink(move);
			}
		}
		return rest;
	}

	/** Quita hasta n unidades que cumplan el predicado; devuelve cuántas quitó. */
	public int quitar(Predicate<ItemStack> p, int n) {
		int left = n;
		for (int i = 0; i < SIZE && left > 0; i++) {
			ItemStack s = slots[i];
			if (!s.isEmpty() && p.test(s)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
				if (s.isEmpty()) {
					slots[i] = ItemStack.EMPTY;
				}
			}
		}
		return n - left;
	}

	public int quitar(Item item, int n) {
		return quitar(s -> s.is(item), n);
	}

	/** Primer hueco que cumple el predicado, o EMPTY. */
	public ItemStack buscar(Predicate<ItemStack> p) {
		for (ItemStack s : slots) {
			if (!s.isEmpty() && p.test(s)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	public boolean cabe(ItemStack in) {
		int free = 0;
		for (ItemStack s : slots) {
			if (s.isEmpty()) {
				free += in.getMaxStackSize();
			}
			if (ItemStack.isSameItemSameComponents(s, in)) {
				free += s.getMaxStackSize() - s.getCount();
			}
			if (free >= in.getCount()) return true;
		}
		return free >= in.getCount();
	}

    public Inv copiar() {
        Inv copia=new Inv();for(int i=0;i<SIZE;i++)copia.slots[i]=slots[i].copy();return copia;
    }
    public void reemplazar(Inv copia) {
        for(int i=0;i<SIZE;i++)slots[i]=copia.slots[i].copy();
    }
	public void clear() {
		for (int i = 0; i < SIZE; i++) {
			slots[i] = ItemStack.EMPTY;
		}
	}
}
