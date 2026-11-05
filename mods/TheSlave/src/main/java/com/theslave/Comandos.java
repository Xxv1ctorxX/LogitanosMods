package com.theslave;

import com.theslave.ai.Planificador;
import com.theslave.ritual.Ritual;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/** Comandos de operador y de depuración: recargar, simular un plan sin mover a nadie, probar el ritual. */
public final class Comandos {
	private Comandos() {
	}

	public static void registrar() {
		ServerLifecycleEvents.SERVER_STARTING.register(Registro::cargar);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Registro.guardar());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("theslave")
					.requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.literal("recargar").executes(ctx -> {
						Config.cargar();
						ctx.getSource().sendSuccess(() -> Component.literal("Configuración recargada."), false);
						return 1;
					}))
					.then(Commands.literal("simular")
							.then(Commands.argument("item", StringArgumentType.word())
									.then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 2304))
											.executes(ctx -> {
												String id = StringArgumentType.getString(ctx, "item");
												int n = IntegerArgumentType.getInteger(ctx, "cantidad");
												Identifier ident = id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
												Item item = BuiltInRegistries.ITEM.getOptional(ident).orElse(null);
												if (item == null) {
													ctx.getSource().sendFailure(Component.literal("Item desconocido: " + id));
													return 0;
												}
												ServerLevel level = ctx.getSource().getLevel();
												Planificador.Resultado r = Planificador.planificar(level, item, n, new com.theslave.entity.Inv());
												ctx.getSource().sendSuccess(() -> Component.literal(r.describir()), false);
												return 1;
											}))))
					.then(Commands.literal("notario").executes(ctx -> {
						ServerPlayer p = ctx.getSource().getPlayerOrException();
						var n = com.theslave.registry.ModEntities.NOTARIO.create((ServerLevel) p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
						if (n == null) {
							return 0;
						}
						n.setPos(p.getX(), p.getY(), p.getZ());
						p.level().addFreshEntity(n);
						return 1;
					}))
					.then(Commands.literal("enclave")
							.then(Commands.argument("tipo", StringArgumentType.word()).executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								ServerLevel sl = (ServerLevel) p.level();
								com.theslave.worldgen.Enclaves.Tipo tipo;
								try {
									tipo = com.theslave.worldgen.Enclaves.Tipo.valueOf(StringArgumentType.getString(ctx, "tipo").toUpperCase());
								} catch (IllegalArgumentException e) {
									ctx.getSource().sendFailure(Component.literal("Tipos: cueva, superficie, casa, refugio"));
									return 0;
								}
								var chunk = sl.getChunkAt(p.blockPosition());
								com.theslave.worldgen.Enclaves.construir(sl, chunk, new com.theslave.worldgen.Enclaves.Sitio(tipo,
										chunk.getPos().x(), chunk.getPos().z()));
								return 1;
							})))
					.then(Commands.literal("tumba")
							.then(Commands.literal("escena").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								Ritual.escenaDePrueba(p);
								return 1;
							}))
							.then(Commands.literal("resucitar").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								BlockPos pos = p.blockPosition();
								return Ritual.resucitarCercana(p, pos) ? 1 : 0;
							}))));
		});
	}
}
