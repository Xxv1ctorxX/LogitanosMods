package com.theslave.client;

import com.google.gson.JsonParser;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;

/** Resuelve contratos contra las clases reales del jar local sin inicializar Minecraft ni Fabric. */
public final class MixinContractTest {
    private static int checks;
    private static void require(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name,false,MixinContractTest.class.getClassLoader());
    }
    private static Method target(String owner,String name,Class<?> result,String... arguments) throws Exception {
        Class<?>[] params=new Class<?>[arguments.length];
        for(int i=0;i<params.length;i++)params[i]=arguments[i].equals("float")?float.class:type(arguments[i]);
        Method method=type(owner).getDeclaredMethod(name,params);
        require(method.getReturnType()==result,"Descriptor de retorno cambiado: "+owner+"."+name);
        require(!Modifier.isStatic(method.getModifiers()),"Target ya no es instancia: "+name);
        return method;
    }
    public static void run() throws Exception {
        String player="net.minecraft.server.level.ServerPlayer";
        Method save=target("net.minecraft.server.players.PlayerList","save",void.class,player);
        require(Modifier.isProtected(save.getModifiers()),"Visibilidad inesperada PlayerList.save");
        target(player,"tick",void.class);target(player,"doTick",void.class);
        target(player,"die",void.class,"net.minecraft.world.damagesource.DamageSource");
        target(player,"hurtServer",boolean.class,"net.minecraft.server.level.ServerLevel","net.minecraft.world.damagesource.DamageSource","float");
        target("net.minecraft.server.network.ServerGamePacketListenerImpl","shouldHandleMessage",boolean.class,"net.minecraft.network.protocol.Packet");
        require(type("org.spongepowered.asm.mixin.MixinEnvironment$CompatibilityLevel").getDeclaredField("JAVA_25")!=null,"Mixin local no soporta Java25");
        try(var input=MixinContractTest.class.getResourceAsStream("/theslave.mixins.json")) {
            require(input!=null,"Falta config mixins en recursos compilados");
            var json=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            require(json.get("required").getAsBoolean(),"Mixins de durabilidad deben fallar cerrado");
            require(json.getAsJsonObject("injectors").get("defaultRequire").getAsInt()==1,"Inyecciones pueden fallar silenciosamente");
            String pack=json.get("package").getAsString();
            for(var name:json.getAsJsonArray("mixins")) {
                String file="/"+(pack+"."+name.getAsString()).replace('.','/')+".class";
                require(MixinContractTest.class.getResource(file)!=null,"Mixin no compilado: "+name);
            }
        }
        System.out.println("Contratos mixins locales: "+checks+" comprobaciones correctas, sin arrancar Fabric.");
    }
    public static void main(String[] args) throws Exception { run(); }
}
