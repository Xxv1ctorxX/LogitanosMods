package com.theball.item;

import com.theball.TheBallMod;
import com.theball.registry.ModItems;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Armadura del Rey del Aluminio: solo se consigue como recompensa del trono. Más dura que el hierro y ligera:
 * cada pieza suma +2,5 % de velocidad. La Corona es el casco.
 */
public final class KingArmor {
	public static final ResourceKey<EquipmentAsset> ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, TheBallMod.id("aluminum_king"));

	/** Se repara con lingotes de aluminio en el yunque. */
	public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, TheBallMod.id("repairs_aluminum_king_armor"));

	public static final ArmorMaterial MATERIAL = new ArmorMaterial(
			40,
			defense(),
			25,
			SoundEvents.ARMOR_EQUIP_IRON,
			3.0f,
			0.05f,
			REPAIRS,
			ASSET);

	private static Map<ArmorType, Integer> defense() {
		Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
		map.put(ArmorType.BOOTS, 3);
		map.put(ArmorType.LEGGINGS, 6);
		map.put(ArmorType.CHESTPLATE, 8);
		map.put(ArmorType.HELMET, 3);
		map.put(ArmorType.BODY, 11);
		return map;
	}

	private KingArmor() {
	}

	/** Propiedades de cada pieza: armadura + velocidad, épica. */
	public static Item.Properties properties(ArmorType type) {
		ItemAttributeModifiers attributes = MATERIAL.createAttributes(type).withModifierAdded(
				Attributes.MOVEMENT_SPEED,
				new AttributeModifier(TheBallMod.id("king_speed_" + type.getSerializedName()), 0.025,
						AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
				EquipmentSlotGroup.bySlot(type.getSlot()));
		return new Item.Properties()
				.humanoidArmor(MATERIAL, type)
				.attributes(attributes)
				.rarity(Rarity.EPIC);
	}

	/** ¿Lleva la Corona puesta? */
	public static boolean wearsCrown(Player player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ALUMINUM_CROWN);
	}

	/** ¿Lleva las cuatro piezas? */
	public static boolean hasFullSet(Player player) {
		return wearsCrown(player)
				&& is(player.getItemBySlot(EquipmentSlot.CHEST), ModItems.ALUMINUM_CHESTPLATE)
				&& is(player.getItemBySlot(EquipmentSlot.LEGS), ModItems.ALUMINUM_LEGGINGS)
				&& is(player.getItemBySlot(EquipmentSlot.FEET), ModItems.ALUMINUM_BOOTS);
	}

	private static boolean is(ItemStack stack, Item item) {
		return stack.is(item);
	}
}
