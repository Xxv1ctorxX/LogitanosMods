package com.livecrops;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;

public final class LiveCropsCommand {
    private LiveCropsCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("livecrops")
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("ayuda").executes(context -> help(context.getSource())))
                .then(Commands.literal("estado").executes(context -> status(context.getSource())))
                .then(Commands.literal("info").requires(LiveCropsCommand::operator)
                        .executes(context -> message(context.getSource(), engine().info())))
                .then(configuration())
                .then(Commands.literal("ritmo").requires(LiveCropsCommand::operator)
                        .then(rate("conectados", true)).then(rate("vacio", false)))
                .then(Commands.literal("adoptar").requires(LiveCropsCommand::operator)
                        .executes(context -> adopt(context, 1, false))
                        .then(Commands.argument("radio", IntegerArgumentType.integer(0, 16))
                                .executes(context -> adopt(context, IntegerArgumentType.getInteger(context, "radio"), false))
                                .then(Commands.literal("confirmar").executes(context -> adopt(context,
                                        IntegerArgumentType.getInteger(context, "radio"), true)))))
                .then(Commands.literal("olvidar").requires(LiveCropsCommand::operator)
                        .then(Commands.literal("chunk").executes(context -> forget(context, false))
                                .then(Commands.literal("confirmar").executes(context -> forget(context, true)))));
        dispatcher.register(root);
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> configuration() {
        var command = Commands.literal("config").requires(LiveCropsCommand::operator)
                .executes(context -> message(context.getSource(), engine().config().describe()))
                .then(Commands.literal("restablecer").executes(context -> {
                    engine().resetSettings();
                    return message(context.getSource(), "Restablecidos los valores predeterminados de LiveCrops.");
                }))
                .then(Commands.literal("cultivo").then(Commands.argument("id", IdentifierArgument.id())
                        .suggests((context, builder) -> {
                            for (String id : LiveCropsConfig.IDS) {
                                String full = "minecraft:" + id;
                                if (full.startsWith(builder.getRemaining())) builder.suggest(full);
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("activo", BoolArgumentType.bool()).executes(context -> {
                            try {
                                String id = IdentifierArgument.getId(context, "id").toString();
                                engine().cropSetting(id, BoolArgumentType.getBool(context, "activo"));
                                return message(context.getSource(), "Actualizado " + id + ". Cambio guardado con el mundo.");
                            } catch (Exception exception) { return error(context.getSource(), exception); }
                        }))));
        for (var field : LiveCropsConfig.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            var option = Commands.literal(field.getName());
            if (field.getType() == boolean.class) {
                option.then(Commands.argument("valor", BoolArgumentType.bool()).executes(context ->
                        setting(context, field.getName(), BoolArgumentType.getBool(context, "valor"))));
            } else if (field.getType() == int.class) {
                option.then(Commands.argument("valor", IntegerArgumentType.integer()).executes(context ->
                        setting(context, field.getName(), IntegerArgumentType.getInteger(context, "valor"))));
            } else if (field.getType() == double.class) {
                option.then(Commands.argument("valor", DoubleArgumentType.doubleArg()).executes(context ->
                        setting(context, field.getName(), DoubleArgumentType.getDouble(context, "valor"))));
            } else continue;
            command.then(option);
        }
        return command;
    }
    private static int setting(CommandContext<CommandSourceStack> context, String name, Object value) {
        try {
            engine().setting(name, value);
            return message(context.getSource(), name + " = " + value + ". Cambio guardado con el mundo.");
        } catch (Exception exception) { return error(context.getSource(), exception); }
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> rate(String name, boolean online) {
        return Commands.literal(name).then(Commands.argument("valor", DoubleArgumentType.doubleArg(0, 1)).executes(context -> {
            double value = DoubleArgumentType.getDouble(context, "valor");
            try {
                var config = engine().config();
                engine().rates(online ? value : config.onlineRate, online ? config.emptyServerRate : value);
                return message(context.getSource(), "Ritmo " + name + ": " + Math.round(value * 10000) / 100.0 + " %. Efectivo desde los siguientes ticks.");
            } catch (Exception exception) { return error(context.getSource(), exception); }
        }));
    }
    private static boolean operator(CommandSourceStack source) { return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }
    private static GrowthEngine engine() {
        GrowthEngine engine = LiveCropsMod.engine();
        if (engine == null) throw new IllegalStateException("El servidor todavía no está listo");
        return engine;
    }
    private static int help(CommandSourceStack source) {
        var config = engine().config();
        return message(source, "LiveCrops: cerca crece vanilla (100 %); lejos con jugadores: " + config.onlineRate * 100
                + " %; servidor vacío: " + config.emptyServerRate * 100
                + " %. Detecta huertos existentes al cargar y alrededor de la cama/respawn. No cuenta tiempo apagado o pausado. /livecrops estado inspecciona el cultivo. Operadores: info, config, config <opción> <valor>, config cultivo <id> <true|false>, config restablecer, ritmo conectados|vacio <0..1>, adoptar [radio], adoptar <radio> confirmar, olvidar chunk [confirmar].");
    }
    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockPos previous = null;
        for (double distance = 0; distance <= 6; distance += 0.25) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(distance)));
            if (pos.equals(previous)) continue;
            previous = pos;
            var chunk = source.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) break;
            if (!chunk.getBlockState(pos).isAir()) return message(source, engine().cropStatus(source.getLevel(), pos));
        }
        return message(source, "Apunta a un cultivo a un máximo de seis bloques.");
    }
    private static int adopt(CommandContext<CommandSourceStack> context, int radius, boolean confirm) {
        try {
            var source = context.getSource();
            int count = engine().adoption(source.getLevel(), BlockPos.containing(source.getPosition()), radius, confirm,
                    source.getPlayer() == null ? null : source.getPlayer().getUUID());
            return message(source, confirm ? "Adopción encolada en " + count + " chunks cargados. Recibirás el resultado al terminar; también se registra en el log."
                    : count + " chunks cargados candidatos. Para ejecutar: /livecrops adoptar " + radius + " confirmar");
        } catch (Exception exception) { return error(context.getSource(), exception); }
    }
    private static int forget(CommandContext<CommandSourceStack> context, boolean confirm) {
        var source = context.getSource();
        int count = engine().forget(source.getLevel(), BlockPos.containing(source.getPosition()), confirm);
        return message(source, confirm ? "Retirado seguimiento de " + count + " cultivos. Los bloques se conservan."
                : count + " cultivos registrados. Para retirar: /livecrops olvidar chunk confirmar");
    }
    private static int message(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
    private static int error(CommandSourceStack source, Exception exception) {
        source.sendFailure(Component.literal("LiveCrops: " + exception.getMessage()));
        return 0;
    }
}
