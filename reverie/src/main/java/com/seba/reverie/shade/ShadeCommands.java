package com.seba.reverie.shade;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.seba.reverie.dream.DreamCommands;

import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/** /reverie shades clear | count, /reverie debug shadeevent <player> <event>. */
final class ShadeCommands {
    private ShadeCommands() {
    }

    static void init() {
        DreamCommands.addExtension(root -> root
                .then(Commands.literal("shades").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("clear").executes(ctx -> {
                            int n = ShadeCrowdManager.clearAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("reverie.command.shades_cleared", n), true);
                            return n;
                        }))
                        .then(Commands.literal("count").executes(ctx -> {
                            int n = ShadeCrowdManager.count();
                            ctx.getSource().sendSuccess(() -> Component.translatable("reverie.command.shades_count", n), false);
                            return n;
                        })))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("shadeevent")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("event", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        java.util.Arrays.stream(ShadeCrowdManager.Event.values()).map(e -> e.name().toLowerCase(java.util.Locale.ROOT)), b))
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "event").toUpperCase(java.util.Locale.ROOT);
                                                    ShadeCrowdManager.Event event;
                                                    try {
                                                        event = ShadeCrowdManager.Event.valueOf(name);
                                                    } catch (IllegalArgumentException e) {
                                                        ctx.getSource().sendFailure(Component.translatable("reverie.command.unknown_event", name));
                                                        return 0;
                                                    }
                                                    var player = EntityArgument.getPlayer(ctx, "player");
                                                    boolean ok = ShadeCrowdManager.startEvent(player, event);
                                                    if (!ok) ctx.getSource().sendFailure(Component.translatable("reverie.command.not_dreaming", player.getName()));
                                                    return ok ? 1 : 0;
                                                }))))));
    }
}
