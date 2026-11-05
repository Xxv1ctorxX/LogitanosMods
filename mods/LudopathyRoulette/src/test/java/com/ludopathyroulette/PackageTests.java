package com.ludopathyroulette;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Inspección del artefacto sin iniciar el juego ni cargar sus clases. */
public final class PackageTests {
    public static void main(String[] args)throws Exception{
        try(ZipFile zip=new ZipFile(args[0])){
            for(String entry:List.of("fabric.mod.json","ludopathyroulette.mixins.json","com/ludopathyroulette/RouletteMod.class","com/ludopathyroulette/client/RouletteClient.class","com/ludopathyroulette/mixin/ExpiryMixin.class","com/ludopathyroulette/mixin/PunishmentLootMixin.class","assets/ludopathyroulette/textures/block/white.png","assets/ludopathyroulette/icon.png","data/ludopathyroulette/damage_type/ruleta.json"))
                if(zip.getEntry(entry)==null)throw new AssertionError("Falta "+entry);
            JsonObject manifest=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("fabric.mod.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            if(!manifest.get("version").getAsString().equals("1.0.0"))throw new AssertionError("Versión sin expandir");
            for(String side:List.of("main","client"))for(JsonElement entry:manifest.getAsJsonObject("entrypoints").getAsJsonArray(side))
                if(zip.getEntry(entry.getAsString().replace('.','/')+".class")==null)throw new AssertionError("Punto de entrada ausente");
            int classes=0,jsons=0;
            for(var entries=zip.entries();entries.hasMoreElements();){var entry=entries.nextElement();String name=entry.getName();
                if(name.endsWith(".json")){JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8));jsons++;}
                if(name.endsWith(".class")){
                    classes++;
                    if(!name.contains("/client/")&&new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.ISO_8859_1).contains("net/minecraft/client/"))throw new AssertionError("Referencia cliente en clase común: "+name);
                }
            }
            System.out.println("JAR verificado: "+classes+" clases, "+jsons+" JSON; entradas y separación cliente/común correctas.");
        }
    }
}
