package com.seba.reverie.dream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /reverie wake [player] | dream <player> | home <player> | debug dreamstate <player> (+ extensions). */
public final class DreamCommands {
    public interface Extension {
        void register(com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root);
    }

    private static final java.util.List<Extension> EXTENSIONS = new java.util.ArrayList<>();

    private DreamCommands() {
    }

    public static void addExtension(Extension e) {
        EXTENSIONS.add(e);
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> register(e.getDispatcher()));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("reverie")
                .then(Commands.literal("wake")
                        .executes(ctx -> wake(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(ctx -> wake(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("dream").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> dream(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("home").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> home(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("dreamstate").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> debug(ctx, EntityArgument.getPlayer(ctx, "player"))))));
        for (Extension e : EXTENSIONS) e.register(root);
        dispatcher.register(root);
    }

    private static int wake(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (!DreamManager.isDreaming(player)) {
            ctx.getSource().sendFailure(Component.translatable("reverie.command.not_dreaming", player.getDisplayName()));
            return 0;
        }
        DreamManager.wake(player, DreamManager.Reason.COMMAND);
        ctx.getSource().sendSuccess(() -> Component.translatable("reverie.command.woke", player.getDisplayName()), true);
        return 1;
    }

    private static int dream(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        boolean ok = DreamManager.startDream(player, null);
        if (ok) ctx.getSource().sendSuccess(() -> Component.translatable("reverie.command.dreaming", player.getDisplayName()), true);
        else ctx.getSource().sendFailure(Component.translatable("reverie.command.cannot_dream", player.getDisplayName()));
        return ok ? 1 : 0;
    }

    private static int home(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        ServerLevel dream = ctx.getSource().getServer().getLevel(DreamManager.DREAMLAND);
        if (dream == null) {
            ctx.getSource().sendFailure(Component.literal("Dreamland is missing"));
            return 0;
        }
        BlockPos home = HomeIsland.findOrBuild(dream, player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("reverie.command.home", player.getDisplayName(),
                home.getX(), home.getY(), home.getZ()), false);
        if (ctx.getSource().getEntity() instanceof ServerPlayer self) {
            self.teleportTo(dream, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, self.getYRot(), self.getXRot());
        }
        return 1;
    }

    private static int debug(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        DreamData data = DreamManager.data(player);
        DreamSession s = data.session();
        long now = DreamManager.now(ctx.getSource().getServer());
        String text = s == null
                ? String.format("%s: awake, cooldown %d ticks, dreams %d, backups %d", player.getName().getString(),
                Math.max(0, data.cooldownUntil() - now), data.dreamsCompleted(), DreamBackup.list(ctx.getSource().getServer(), player.getUUID()).size())
                : String.format("%s: dreaming, %d ticks left, %d stashed stacks, held slot %d (%s), bed %s, snapshot %d blocks",
                player.getName().getString(), s.endTime() - now,
                s.stash().items().stream().filter(i -> !i.isEmpty()).count(), s.stash().heldSlot(),
                s.stash().heldItem().getHoverName().getString(), s.ret().bed().map(BlockPos::toShortString).orElse("-"),
                s.snapshot().size());
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    @SuppressWarnings("unused")
    private static Vec3 center(BlockPos p) {
        return Vec3.atBottomCenterOf(p);
    }
}
