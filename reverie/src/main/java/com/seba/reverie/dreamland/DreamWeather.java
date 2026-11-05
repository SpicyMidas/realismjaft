package com.seba.reverie.dreamland;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dream.DreamSession;
import com.seba.reverie.network.Net;
import com.seba.reverie.network.ShadeEventPayload;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModItems;
import com.seba.reverie.shade.ShadeCrowdManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dream weather, decided per dreamer on the server and drawn by that dreamer's client: Starfall (sometimes one
 * lands and leaves a Lucid Shard in a small glowing crater), Petal Drift, Fog Tide, Upward Rain, The Long Silence.
 * Never in the first or last 15 seconds, one at a time, with cooldowns.
 */
public final class DreamWeather implements DreamManager.Listener {
    public enum Kind {
        STARFALL(10, 600, 4), PETAL_DRIFT(11, 400, 2), FOG_TIDE(12, 600, 2), UPWARD_RAIN(13, 400, 2), LONG_SILENCE(14, 400, 1);

        /** ShadeEventPayload event id. */
        public final int id;
        public final int duration;
        final int weight;

        Kind(int id, int duration, int weight) {
            this.id = id;
            this.duration = duration;
            this.weight = weight;
        }

        public static Kind byId(int id) {
            for (Kind k : values()) if (k.id == id) return k;
            return null;
        }
    }

    private static final int EDGE = 300;

    private static final class State {
        long nextAt;
        Kind active;
        long activeUntil;
        long landAt = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void init() {
        DreamManager.addListener(new DreamWeather());
        NeoForge.EVENT_BUS.addListener(DreamWeather::onTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> STATES.clear());
        com.seba.reverie.dream.DreamCommands.addExtension(root -> root.then(net.minecraft.commands.Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("weather")
                        .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(net.minecraft.commands.Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Kind.values()).map(k -> k.name().toLowerCase(java.util.Locale.ROOT)), b))
                                        .executes(ctx -> {
                                            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            Kind kind;
                                            try {
                                                kind = Kind.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind").toUpperCase(java.util.Locale.ROOT));
                                            } catch (IllegalArgumentException e) {
                                                return 0;
                                            }
                                            return force(target, kind) ? 1 : 0;
                                        }))))));
    }

    @Override
    public void ending(ServerPlayer player, DreamSession session, DreamManager.Reason reason) {
        STATES.remove(player.getUUID());
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        ServerLevel dream = event.getServer().getLevel(DreamManager.DREAMLAND);
        if (dream == null) return;
        try {
            STATES.keySet().removeIf(id -> {
                ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
                return p == null || !DreamManager.isDreaming(p);
            });
            for (ServerPlayer p : dream.players()) {
                if (DreamManager.isDreaming(p)) update(dream, p);
            }
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Dream weather update failed", e);
        }
    }

    private static void update(ServerLevel level, ServerPlayer p) {
        DreamSession session = DreamManager.data(p).session();
        if (session == null) return;
        long now = DreamManager.now(level.getServer());
        State s = STATES.computeIfAbsent(p.getUUID(), k -> {
            State n = new State();
            n.nextAt = now + 600 + p.getRandom().nextInt(900);
            return n;
        });
        if (s.active != null) {
            if (s.active == Kind.STARFALL && s.landAt >= 0 && now >= s.landAt) {
                s.landAt = -1;
                landStar(level, p);
            }
            if (now >= s.activeUntil) s.active = null;
            return;
        }
        if (!ReverieConfig.get(ReverieConfig.WEATHER_EVENTS) || now < s.nextAt) return;
        if (now - session.startTime() < EDGE || session.endTime() - now < EDGE + 800) return;
        start(p, s, pick(p.getRandom()), now);
    }

    private static Kind pick(RandomSource rng) {
        int total = 0;
        for (Kind k : Kind.values()) total += k.weight;
        int r = rng.nextInt(total);
        for (Kind k : Kind.values()) {
            if ((r -= k.weight) < 0) return k;
        }
        return Kind.STARFALL;
    }

    private static void start(ServerPlayer p, State s, Kind kind, long now) {
        s.active = kind;
        s.activeUntil = now + kind.duration;
        s.nextAt = s.activeUntil + 1200 + p.getRandom().nextInt(1200);
        if (kind == Kind.STARFALL && p.getRandom().nextFloat() < 0.35f) s.landAt = now + 100 + p.getRandom().nextInt(300);
        Net.send(p, new ShadeEventPayload(kind.id, kind.duration));
        // The Long Silence can bring The Hush with it.
        if (kind == Kind.LONG_SILENCE && p.getRandom().nextFloat() < 0.3f) ShadeCrowdManager.startEvent(p, ShadeCrowdManager.Event.HUSH);
    }

    /** /reverie debug weather <player> <kind>. */
    public static boolean force(ServerPlayer p, Kind kind) {
        if (!DreamManager.isDreaming(p)) return false;
        long now = DreamManager.now(p.server);
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        start(p, s, kind, now);
        if (kind == Kind.STARFALL) s.landAt = now + 60;
        return true;
    }

    /** A falling star lands on an island nearby: a small glowing crater with a Lucid Shard in it. */
    private static void landStar(ServerLevel level, ServerPlayer p) {
        RandomSource rng = p.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = 18 + rng.nextDouble() * 30;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r);
            int z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (top <= level.getMinBuildHeight() + 4 || Math.abs(top - p.getY()) > 30) continue;
            BlockPos ground = new BlockPos(x, top - 1, z);
            if (!level.getBlockState(ground).isSolidRender(level, ground) || level.getBlockEntity(ground) != null) continue;
            crater(level, ground, rng);
            Vec3 at = Vec3.atCenterOf(ground);
            for (int i = 0; i < 24; i++) {
                double t = i / 24.0;
                level.sendParticles(ParticleTypes.END_ROD, at.x + t * 14, at.y + t * 40, at.z - t * 9, 2, 0.1, 0.1, 0.1, 0.01);
            }
            level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 0.5, at.z, 30, 0.6, 0.3, 0.6, 0.08);
            level.playSound(null, ground, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 2.5f, 0.6f);
            level.playSound(null, ground, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.AMBIENT, 3f, 0.7f);
            ItemEntity shard = new ItemEntity(level, at.x, at.y + 0.2, at.z, new ItemStack(ModItems.LUCID_SHARD.get()));
            shard.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(shard);
            return;
        }
    }

    private static void crater(ServerLevel level, BlockPos center, RandomSource rng) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 2.3) continue;
                BlockPos pos = center.offset(dx, 0, dz);
                if (level.getBlockEntity(pos) != null || level.getBlockEntity(pos.above()) != null) continue;
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                if (d < 1.5) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    if (d > 0.5 && rng.nextInt(3) == 0 && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                        level.setBlock(pos, ModBlocks.LUCID_CRYSTAL_CLUSTER.get().defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
