package com.seba.reverie.dream;

import java.nio.file.Path;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /reverie backup list <player> and /reverie backup restore <player> <n> (n = 1 is the newest). Restoring gives
 * the backed-up stacks back (inventory first, the rest dropped at the player's feet); it never deletes anything.
 */
final class BackupCommands {
    private BackupCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("backup").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("restore").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> restore(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "n"))))));
    }

    private static List<Path> newestFirst(ServerPlayer player) {
        List<Path> files = new java.util.ArrayList<>(DreamBackup.list(player.server, player.getUUID()));
        java.util.Collections.reverse(files);
        return files;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        List<Path> files = newestFirst(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("reverie.command.backup.count", player.getDisplayName(), files.size()), false);
        for (int i = 0; i < Math.min(10, files.size()); i++) {
            int n = i + 1;
            String name = files.get(i).getFileName().toString();
            ctx.getSource().sendSuccess(() -> Component.literal(n + ": " + name), false);
        }
        return files.size();
    }

    private static int restore(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int n) throws CommandSyntaxException {
        if (DreamManager.isDreaming(player)) {
            ctx.getSource().sendFailure(Component.translatable("reverie.command.backup.dreaming", player.getDisplayName()));
            return 0;
        }
        List<Path> files = newestFirst(player);
        if (n > files.size()) {
            ctx.getSource().sendFailure(Component.translatable("reverie.command.backup.none", n));
            return 0;
        }
        DreamSession session;
        try {
            session = DreamBackup.read(player.server, files.get(n - 1));
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("reverie.command.backup.unreadable", files.get(n - 1).getFileName().toString()));
            return 0;
        }
        int given = 0;
        List<ItemStack> stacks = new java.util.ArrayList<>(session.stash().items());
        stacks.add(session.stash().heldItem());
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy) && !copy.isEmpty()) player.drop(copy, false);
            given++;
        }
        int total = given;
        ctx.getSource().sendSuccess(() -> Component.translatable("reverie.command.backup.restored", total, player.getDisplayName()), true);
        return total;
    }
}
