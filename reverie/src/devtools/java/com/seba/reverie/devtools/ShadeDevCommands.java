package com.seba.reverie.devtools;

import java.lang.reflect.Field;

import com.seba.reverie.registry.ModEntities;
import com.seba.reverie.shade.ShadeCrowdManager;
import com.seba.reverie.shade.ShadeEntity;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /reveriedev shadelineup (every variant in a row, for close-up screenshots) and /reveriedev shadedump. */
final class ShadeDevCommands {
    private ShadeDevCommands() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> e.getDispatcher().register(Commands.literal("reveriedev")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("shadelineup").executes(ctx -> lineup(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("shadedump").executes(ctx -> dump(ctx.getSource().getLevel())))
                .then(Commands.literal("buildstairs").executes(ctx -> {
                    // Runs the Impossible Stairs builder on the block under the player.
                    var p = ctx.getSource().getPlayerOrException();
                    try {
                        var m = Class.forName("com.seba.reverie.dreamland.IslandDecorator").getDeclaredMethod("impossibleStairs",
                                net.minecraft.world.level.WorldGenLevel.class, net.minecraft.core.BlockPos.class);
                        m.setAccessible(true);
                        m.invoke(null, p.serverLevel(), p.blockPosition().below());
                    } catch (ReflectiveOperationException ex) {
                        throw new IllegalStateException(ex);
                    }
                    return 1;
                }))));
    }

    private static int lineup(ServerPlayer p) {
        try {
            return lineup(p, ShadeEntity.class.getDeclaredField("managed"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int lineup(ServerPlayer p, Field managed) throws ReflectiveOperationException {
        ServerLevel level = p.serverLevel();
        managed.setAccessible(true);
        Vec3 look = p.getViewVector(1f).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        Vec3 center = p.position().add(look.scale(9));
        ShadeEntity.Variant[] all = ShadeEntity.Variant.values();
        for (int i = 0; i < all.length; i++) {
            ShadeEntity s = ModEntities.SHADE.get().create(level);
            if (s == null) return 0;
            managed.setBoolean(s, true);
            s.setVariant(all[i]);
            s.setOwner(p);
            Vec3 at = center.add(side.scale((i - (all.length - 1) / 2.0) * 2.6));
            float yaw = (float) (Mth.atan2(p.getZ() - at.z, p.getX() - at.x) * (180 / Math.PI)) - 90f;
            s.moveTo(at.x, p.getY(), at.z, yaw, 0);
            level.addFreshEntity(s);
        }
        return all.length;
    }

    private static int dump(ServerLevel level) {
        int n = 0;
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof ShadeEntity s)) continue;
            n++;
            ReverieDevTools.LOG.info("[devscript] shade id={} {} at {} fading={} ticks={} tracked={} ticking={}", s.getId(), s.variant(),
                    s.blockPosition().toShortString(), s.fading(), s.tickCount, ShadeCrowdManager.tracked(s.getId()),
                    level.isPositionEntityTicking(s.blockPosition()));
        }
        ReverieDevTools.LOG.info("[devscript] shadedump {} total, {} tracked by crowds", n, ShadeCrowdManager.count());
        return n;
    }
}
