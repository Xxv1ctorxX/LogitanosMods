package com.ludopathyroulette;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.ludopathyroulette.core.WheelMath;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;

/** Modelo serializable. Los stacks solo entran desde copias validadas del inventario servidor. */
public final class Model {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public enum Category { GRAND, NORMAL, PUNISH }
    public enum Kind { GIVE_ITEM, EFFECT, SPAWN, SURCHARGE, KILL, NOTHING }
    public static class Action {
        public Kind kind = Kind.NOTHING;
        public JsonElement source = JsonNull.INSTANCE;
        public int factor = 200, uses = 3, count = 1;
        public Action() {}
        public Action(Kind kind) { this.kind = kind; }
    }
    public static class Segment {
        public String id = UUID.randomUUID().toString();
        public Category category = Category.NORMAL;
        public int weight = 1;
        public JsonElement item = JsonNull.INSTANCE;
        public List<Action> actions = new ArrayList<>();
    }
    public static class Config {
        public int schema = 1, revision;
        public String name = "Ruleta de la casa";
        public boolean published, paused, announce = true, free;
        public JsonElement price = JsonNull.INSTANCE;
        public int xp;
        public List<Segment> segments = new ArrayList<>();
        public int[] weights() { return segments.stream().mapToInt(s -> s.weight).toArray(); }
        public Config copy() { return GSON.fromJson(GSON.toJson(this), Config.class); }
    }
    public static JsonElement stack(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return JsonNull.INSTANCE;
        return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), stack).getOrThrow();
    }
    public static ItemStack stack(JsonElement json, HolderLookup.Provider registries) {
        if (json == null || json.isJsonNull()) return ItemStack.EMPTY;
        if (json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8192) throw new IllegalArgumentException("Objeto mayor de 8 KiB");
        return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json).getOrThrow();
    }
    public static void validate(Config c, HolderLookup.Provider r, Policy policy, boolean op) {
        if (c == null || c.schema != 1 || c.name == null || c.name.isBlank() || c.name.length()>32)
            throw new IllegalArgumentException("Nombre o versión inválidos");
        if (GSON.toJson(c).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>131072)
            throw new IllegalArgumentException("Configuración mayor de 128 KiB");
        WheelMath.sum(c.weights());
        if (c.xp<0 || c.xp>1000) throw new IllegalArgumentException("XP entre 0 y 1000 niveles");
        checkStack(c.price, r, false);
        if (c.free && (!stack(c.price,r).isEmpty() || c.xp!=0)) throw new IllegalArgumentException("Gratis exige precio vacío");
        if (c.free && (!op || !policy.allowFree)) throw new IllegalArgumentException("Gratuidad reservada a operadores");
        if (!c.free && stack(c.price,r).isEmpty() && c.xp==0) throw new IllegalArgumentException("Configura precio o activa gratis");
        Set<String> ids = new HashSet<>();
        for (Segment s : c.segments) {
            if(s.id==null || !ids.add(s.id)) throw new IllegalArgumentException("Identificador duplicado");
            UUID.fromString(s.id);
            if(s.category==null) throw new IllegalArgumentException("Categoría vacía");
            if(s.category!=Category.PUNISH) { checkStack(s.item,r,true); if(!s.actions.isEmpty()) throw new IllegalArgumentException("Un premio no tiene castigos"); }
            else {
                if(s.actions==null || s.actions.isEmpty() || s.actions.size()>3) throw new IllegalArgumentException("1–3 acciones por castigo");
                Set<Kind> kinds = EnumSet.noneOf(Kind.class);
                for(Action a:s.actions) {
                    if(a==null || a.kind==null || !kinds.add(a.kind)) throw new IllegalArgumentException("Acción repetida o vacía");
                    if((a.kind==Kind.KILL || a.kind==Kind.NOTHING) && s.actions.size()!=1) throw new IllegalArgumentException("Muerte/nada son exclusivos");
                    ItemStack sample=stack(a.source,r);
                    switch(a.kind) {
                        case GIVE_ITEM -> checkStack(a.source,r,true);
                        case EFFECT -> {
                            PotionContents potion=sample.get(DataComponents.POTION_CONTENTS);
                            if(potion==null || !potion.hasEffects()) throw new IllegalArgumentException("Elige una poción con efectos");
                            int n=0;
                            for(MobEffectInstance effect:potion.getAllEffects()) {
                                String id=effect.getEffect().unwrapKey().orElseThrow().identifier().toString();
                                if(++n>8 || effect.getDuration()<1 || effect.getDuration()>2400 || effect.getAmplifier()>4 || effect.getEffect().value().isInstantaneous() || !policy.allowedEffects.contains(id))
                                    throw new IllegalArgumentException("Efecto no permitido o fuera de límites: "+id);
                            }
                        }
                        case SPAWN -> {
                            if(!(sample.getItem() instanceof SpawnEggItem)) throw new IllegalArgumentException("Elige un huevo de invocación");
                            String id=net.minecraft.world.entity.EntityType.getKey(SpawnEggItem.getType(sample)).toString();
                            if(!policy.enableSpawn || !policy.allowedEntities.contains(id) || a.count<1 || a.count>3) throw new IllegalArgumentException("Invocación deshabilitada o entidad no permitida");
                        }
                        case KILL -> { if(!policy.enableKill) throw new IllegalArgumentException("Daño letal deshabilitado en servidor"); }
                        case SURCHARGE -> { if(c.free || a.factor<150 || a.factor>1000 || a.uses<1 || a.uses>5) throw new IllegalArgumentException("Recargo inválido"); }
                        case NOTHING -> {}
                    }
                }
            }
        }
        int publicBytes=0;
        for(Segment s:c.segments)publicBytes+=GSON.toJson(describe(s,r)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length+128;
        if(publicBytes>12000)throw new IllegalArgumentException("Descripción pública demasiado larga: acorta nombres y efectos");
    }
    private static void checkStack(JsonElement e, HolderLookup.Provider r, boolean required) {
        ItemStack s=stack(e,r);
        if(required && s.isEmpty()) throw new IllegalArgumentException("Falta un objeto de muestra");
        if(!s.isEmpty() && (s.getCount()<1 || s.getCount()>Math.min(64,s.getMaxStackSize()))) throw new IllegalArgumentException("Cantidad de objeto inválida");
    }
    public static String describe(Segment s, HolderLookup.Provider r) {
        if(s.category!=Category.PUNISH) return itemName(s.item,r);
        List<String> text=new ArrayList<>();
        for(Action a:s.actions) text.add(switch(a.kind) {
            case GIVE_ITEM -> itemName(a.source,r);
            case NOTHING -> "Sin consecuencia";
            case KILL -> "Intento de daño LETAL (respeta tótem)";
            case SURCHARGE -> "Precio ×"+(a.factor/100.0)+" durante "+a.uses+" tiradas";
            case SPAWN -> "Invoca "+a.count+" × "+SpawnEggItem.getType(stack(a.source,r)).getDescription().getString();
            case EFFECT -> {
                List<String> effects=new ArrayList<>();
                PotionContents potion=stack(a.source,r).get(DataComponents.POTION_CONTENTS);
                if(potion!=null) for(MobEffectInstance e:potion.getAllEffects()) effects.add(e.getEffect().value().getDisplayName().getString()+" "+(e.getAmplifier()+1)+" · "+(e.getDuration()/20)+" s");
                yield String.join(", ",effects);
            }
        });
        return String.join(" + ",text);
    }
    public static String itemName(JsonElement e,HolderLookup.Provider r) {
        ItemStack s=stack(e,r); return s.isEmpty()?"—":s.getCount()+" × "+s.getHoverName().getString();
    }
}
