package com.ludopathyroulette;

import com.ludopathyroulette.core.AtomicStore;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

public final class Policy {
    public static final List<String> OPTIONS=List.of("allowCreative","requireSneak","allowFree","enableSpawn","enableKill","maxOperations","maxPending","allowedEntities","allowedEffects");
    public boolean allowCreative=false, requireSneak=false, allowFree=true, enableSpawn=false, enableKill=true;
    public int maxOperations=256, maxPending=128;
    public List<String> allowedEntities=new ArrayList<>(List.of("minecraft:zombie","minecraft:skeleton","minecraft:spider"));
    public List<String> allowedEffects=new ArrayList<>(List.of("minecraft:slowness","minecraft:weakness","minecraft:blindness","minecraft:nausea","minecraft:poison","minecraft:hunger","minecraft:mining_fatigue"));
    public void validate() {
        if(maxOperations<1 || maxOperations>4096 || maxPending<1 || maxPending>1024 || allowedEntities==null || allowedEffects==null)
            throw new IllegalArgumentException("Límites de configuración inválidos");
        for(List<String> ids:List.of(allowedEntities,allowedEffects)) {
            if(ids.size()>128)throw new IllegalArgumentException("Máximo 128 identificadores por lista");
            for(String id:ids)if(id==null||id.length()>128||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Identificador inválido: "+id);
        }
    }
    public Policy withOption(String option,String value) {
        Policy next=Model.GSON.fromJson(Model.GSON.toJson(this),Policy.class);
        switch(option) {
            case "allowCreative" -> next.allowCreative=bool(value);
            case "requireSneak" -> next.requireSneak=bool(value);
            case "allowFree" -> next.allowFree=bool(value);
            case "enableSpawn" -> next.enableSpawn=bool(value);
            case "enableKill" -> next.enableKill=bool(value);
            case "maxOperations" -> next.maxOperations=Integer.parseInt(value);
            case "maxPending" -> next.maxPending=Integer.parseInt(value);
            case "allowedEntities" -> next.allowedEntities=ids(value);
            case "allowedEffects" -> next.allowedEffects=ids(value);
            default -> throw new IllegalArgumentException("Opción desconocida: "+option);
        }
        next.validate();return next;
    }
    private static boolean bool(String value){if(!value.equalsIgnoreCase("true")&&!value.equalsIgnoreCase("false"))throw new IllegalArgumentException("Usa true o false");return Boolean.parseBoolean(value);}
    private static List<String> ids(String value){return value.equals("ninguno")?new ArrayList<>():new ArrayList<>(Arrays.stream(value.split(",",-1)).map(String::trim).distinct().toList());}
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("ludopathyroulette.json");}
    public static void save(Policy policy){
        policy.validate();
        try{AtomicStore.write(path(),Model.GSON.toJson(policy));}catch(Exception e){throw new IllegalStateException("No se pudo guardar la configuración",e);}
    }
    public static Policy load() {
        Path p=path();
        try {
            if(!Files.exists(p)) AtomicStore.write(p,Model.GSON.toJson(new Policy()));
            Policy policy=Model.GSON.fromJson(Files.readString(p),Policy.class); policy.validate(); return policy;
        } catch(Exception e) { throw new IllegalStateException("No se pudo leer "+p,e); }
    }
}
