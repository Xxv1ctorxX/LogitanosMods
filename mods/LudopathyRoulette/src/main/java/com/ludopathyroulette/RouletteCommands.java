package com.ludopathyroulette;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class RouletteCommands {
    private static BlockPos target(ServerPlayer p){return p.pick(8,0,false) instanceof BlockHitResult hit?hit.getBlockPos():p.blockPosition();}
    public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,r,e)->{
        var root=Commands.literal("ludopathyroulette");
        root.then(Commands.literal("config")
            .requires(source->source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .executes(ctx->{ctx.getSource().sendSuccess(()->Component.literal(Model.GSON.toJson(RouletteMod.policy)),false);return 1;})
            .then(Commands.argument("opcion",StringArgumentType.word())
                .suggests((ctx,builder)->{for(String option:Policy.OPTIONS)if(option.toLowerCase(java.util.Locale.ROOT).startsWith(builder.getRemainingLowerCase()))builder.suggest(option);return builder.buildFuture();})
                .then(Commands.argument("valor",StringArgumentType.greedyString())
                    .executes(ctx->{try{
                        String option=StringArgumentType.getString(ctx,"opcion"),value=StringArgumentType.getString(ctx,"valor");
                        Policy next=RouletteMod.policy.withOption(option,value);
                        Policy.save(next);RouletteMod.policy=next;
                        ctx.getSource().sendSuccess(()->Component.literal("Configuración guardada: "+option+" = "+value),false);return 1;
                    }catch(Exception error){ctx.getSource().sendFailure(Component.literal(error.getMessage()==null?"Valor inválido":error.getMessage()));return 0;}}))));
        for(String command:new String[]{"ayuda","stats","pendientes","recoger","editar","probar","pausar","reanudar","retirar","recargar"}) {
            var node=Commands.literal(command);
            if(java.util.Set.of("editar","probar","pausar","reanudar","retirar").contains(command))node.requires(s->s.getPlayer()!=null&&Service.staff(s.getPlayer()));
            if(command.equals("recargar"))node.requires(s->s.getPlayer()!=null&&Service.op(s.getPlayer()));
            node.executes(ctx->{ServerPlayer p=ctx.getSource().getPlayerOrException();try{
                Service s=RouletteMod.service;switch(command){
                    case "ayuda" -> Service.message(p,"Abre la pantalla para jugar; puerta lateral para staff. Premios ilimitados por plantilla, cobro consumido. /ludopathyroulette pendientes | recoger");
                    case "stats" -> s.stats(p,target(p));
                    case "pendientes" -> s.pending(p);
                    case "recoger" -> s.claim(p);
                    case "editar","probar" -> s.open(p,target(p),true);
                    case "pausar" -> s.pause(p,target(p),true);
                    case "reanudar" -> s.pause(p,target(p),false);
                    case "retirar" -> s.retire(p,target(p));
                    case "recargar" -> {RouletteMod.policy=Policy.load();Service.message(p,"Política recargada.");}
                }return 1;
            }catch(Exception ex){Service.message(p,ex.getMessage());return 0;}});root.then(node);
        }
        root.then(Commands.literal("diagnostico").requires(s->s.getPlayer()!=null&&Service.op(s.getPlayer())).then(Commands.argument("spinId",StringArgumentType.word()).executes(c->{RouletteMod.service.diagnose(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"spinId"));return 1;})));
        root.then(Commands.literal("resolver").requires(s->s.getPlayer()!=null&&Service.op(s.getPlayer())).then(Commands.argument("spinId",StringArgumentType.word()).then(Commands.argument("accion",StringArgumentType.word()).executes(c->{try{RouletteMod.service.resolve(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"spinId"),StringArgumentType.getString(c,"accion"));return 1;}catch(Exception ex){Service.message(c.getSource().getPlayerOrException(),ex.getMessage());return 0;}}))));
        dispatcher.register(root);
    });}
}
