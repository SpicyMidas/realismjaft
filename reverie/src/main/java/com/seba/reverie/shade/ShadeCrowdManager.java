package com.seba.reverie.shade;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dream.DreamSession;
import com.seba.reverie.dreamland.DreamlandBiomeKeys;
import com.seba.reverie.network.Net;
import com.seba.reverie.network.ShadeEventPayload;
import com.seba.reverie.registry.ModEntities;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps a crowd of Shades around every dreamer: 28-52 blocks away, in loose clusters, all watching. Come within
 * shadeFleeRadius and one vanishes; a replacement appears further off. Stand still and the ones you are not
 * looking at blink a little closer (never inside 24). Also runs the rare events and the Farewell.
 */
public final class ShadeCrowdManager implements DreamManager.Listener {
    public enum Event { HUSH, EVERYONE_TURNS, BEHIND_YOU, GRAVITY_HICCUP, COUNTING, MIMIC }

    private static final int MIMIC_DELAY = 60;
    private static final int MIMIC_COPY = 420;
    private static final int MIMIC_MAX = 760;

    public static final int HUSH = 1;
    public static final int TURNS = 2;
    public static final int BEHIND = 3;
    public static final int HICCUP = 4;
    public static final int COUNTING = 5;
    public static final int FAREWELL = 6;

    private static final int MIN_DIST = 28;
    private static final int MAX_DIST = 52;
    private static final int FAREWELL_TICKS = 800;

    private static final class Crowd {
        final List<Integer> ids = new ArrayList<>();
        final EnumMap<Event, Long> cooldowns = new EnumMap<>(Event.class);
        final Map<Integer, Vec3> countingHomes = new HashMap<>();
        Vec3 lastPos = Vec3.ZERO;
        Event event;
        int eventTicks;
        int hushTicks;
        int awayTicks;
        int behindId = -1;
        int behindTicks;
        int mimicId = -1;
        int mimicAge;
        Vec3 mimicOrigin;
        Vec3 playerOrigin;
        final java.util.ArrayDeque<double[]> mimicHistory = new java.util.ArrayDeque<>();
        boolean farewellSent;
    }

    private static final Map<UUID, Crowd> CROWDS = new HashMap<>();
    private static int serverTick;

    public static void init() {
        ShadeCrowdManager self = new ShadeCrowdManager();
        DreamManager.addListener(self);
        NeoForge.EVENT_BUS.addListener(ShadeCrowdManager::onTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> clearAll(e.getServer()));
        ShadeCommands.init();
    }

    @Override
    public void started(ServerPlayer player, DreamSession session) {
        CROWDS.computeIfAbsent(player.getUUID(), k -> new Crowd());
    }

    @Override
    public void resumed(ServerPlayer player, DreamSession session) {
        CROWDS.computeIfAbsent(player.getUUID(), k -> new Crowd());
    }

    @Override
    public void ending(ServerPlayer player, DreamSession session, DreamManager.Reason reason) {
        Crowd c = CROWDS.remove(player.getUUID());
        if (c != null) discardAll(player.server, c);
    }

    public static int clearAll(MinecraftServer server) {
        int n = 0;
        for (Crowd c : CROWDS.values()) n += discardAll(server, c);
        CROWDS.clear();
        ServerLevel dream = server.getLevel(DreamManager.DREAMLAND);
        if (dream != null) {
            for (Entity e : dream.getAllEntities()) {
                if (e instanceof ShadeEntity) {
                    e.discard();
                    n++;
                }
            }
        }
        return n;
    }

    private static int discardAll(MinecraftServer server, Crowd c) {
        ServerLevel dream = server.getLevel(DreamManager.DREAMLAND);
        int n = 0;
        if (dream != null) {
            for (int id : c.ids) {
                Entity e = dream.getEntity(id);
                if (e != null) {
                    e.discard();
                    n++;
                }
            }
            Entity behind = dream.getEntity(c.behindId);
            if (behind != null) behind.discard();
            Entity mimic = dream.getEntity(c.mimicId);
            if (mimic != null) mimic.discard();
            c.mimicId = -1;
        }
        c.ids.clear();
        return n;
    }

    public static int count() {
        int n = 0;
        for (Crowd c : CROWDS.values()) n += c.ids.size();
        return n;
    }

    private static void onTick(ServerTickEvent.Post event) {
        serverTick++;
        MinecraftServer server = event.getServer();
        ServerLevel dream = server.getLevel(DreamManager.DREAMLAND);
        if (dream == null || CROWDS.isEmpty() && dream.players().isEmpty()) return;
        try {
            behindYouTick(dream);
            mimicTick(dream);
            if (serverTick % 10 != 0) return;
            for (Iterator<Map.Entry<UUID, Crowd>> it = CROWDS.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
                if (p == null || !DreamManager.isDreaming(p) || p.level() != dream) {
                    discardAll(server, entry.getValue());
                    it.remove();
                }
            }
            int budget = ReverieConfig.get(ReverieConfig.MAX_SHADES_PER_SERVER) - count();
            for (ServerPlayer p : dream.players()) {
                if (!DreamManager.isDreaming(p)) continue;
                Crowd c = CROWDS.computeIfAbsent(p.getUUID(), k -> new Crowd());
                budget -= update(dream, p, c, Math.max(0, budget));
            }
            // Anything the crowds do not own (no dreamer, lost track, stuck fading) is discarded.
            if (serverTick % 40 == 0) sweep(dream);
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Shade crowd update failed", e);
        }
    }

    /** True if a crowd owns this entity id (also the Behind You shade). */
    public static boolean tracked(int id) {
        for (Crowd c : CROWDS.values()) if (c.behindId == id || c.mimicId == id || c.ids.contains(id)) return true;
        return false;
    }

    private static void sweep(ServerLevel dream) {
        long now = dream.getGameTime();
        List<Entity> stray = new ArrayList<>();
        for (Entity e : dream.getAllEntities()) {
            if (!(e instanceof ShadeEntity s)) continue;
            boolean stuck = s.fading() && now - s.fadeGameTime > ShadeEntity.FADE_TICKS + 20;
            if (stuck || !tracked(s.getId()) || !(dream.getEntity(s.ownerId()) instanceof ServerPlayer)) stray.add(s);
        }
        for (Entity e : stray) e.discard();
    }

    /** Returns the number of shades spawned. */
    private static int update(ServerLevel level, ServerPlayer p, Crowd c, int budget) {
        DreamSession session = DreamManager.data(p).session();
        if (session == null) return 0;
        long now = DreamManager.now(level.getServer());
        long left = session.endTime() - now;
        long elapsed = now - session.startTime();
        RandomSource rng = p.getRandom();
        List<ShadeEntity> shades = new ArrayList<>();
        for (Iterator<Integer> it = c.ids.iterator(); it.hasNext(); ) {
            Entity e = level.getEntity(it.next());
            if (e instanceof ShadeEntity s && s.isAlive()) shades.add(s);
            else it.remove();
        }
        boolean farewell = ReverieConfig.get(ReverieConfig.EVENT_FAREWELL) && left <= FAREWELL_TICKS;
        if (farewell && !c.farewellSent) {
            c.farewellSent = true;
            if (c.event != null) endEvent(level, c, shades);
            c.hushTicks = 0;
            c.awayTicks = 0;
            Net.send(p, new ShadeEventPayload(FAREWELL, (int) Math.max(1, left)));
        }
        double flee = ReverieConfig.get(ReverieConfig.SHADE_FLEE_RADIUS);
        Vec3 pos = p.position();
        boolean still = pos.distanceToSqr(c.lastPos) < 0.15 * 0.15;
        c.lastPos = pos;
        if (c.awayTicks > 0 && (c.awayTicks -= 10) <= 0) for (ShadeEntity s : shades) if (s.mode() == ShadeEntity.AWAY) s.setMode(ShadeEntity.FACE);
        if (c.event != null && (c.eventTicks -= 10) <= 0) endEvent(level, c, shades);

        for (ShadeEntity s : shades) {
            if (s.fading()) continue;
            double d = s.position().distanceTo(pos);
            if (farewell) {
                // Farewell: one by one they turn their backs and walk off into the distance.
                if (s.mode() != ShadeEntity.AWAY && rng.nextInt(12) == 0) {
                    s.setMode(ShadeEntity.AWAY);
                    Vec3 away = s.position().subtract(pos).multiply(1, 0, 1).normalize().scale(0.07);
                    s.setDrift(away);
                }
                if (d > 78) s.fadeOut();
                continue;
            }
            if (d < flee || d > 90) {
                s.fadeOut();
                continue;
            }
            if (still && c.event != Event.COUNTING && d > 27 && rng.nextInt(25) == 0 && !ShadeLook.looksAt(p, s.position().add(0, 1, 0), 0.4)) {
                // Blinking closer while you are not looking. Never inside 24.
                Vec3 toward = pos.subtract(s.position()).multiply(1, 0, 1).normalize().scale(1 + rng.nextInt(3));
                Vec3 next = s.position().add(toward);
                if (next.distanceTo(pos) >= 24) s.setPos(next.x, groundY(level, next, s.position().y), next.z);
            }
        }

        int spawned = 0;
        if (!farewell && c.hushTicks <= 0 && c.event != Event.COUNTING) {
            int target = targetCount(level, p);
            int alive = (int) shades.stream().filter(s -> !s.fading()).count();
            int toSpawn = Math.min(Math.min(4, target - alive), budget);
            for (int i = 0; i < toSpawn; i++) {
                ShadeEntity s = spawn(level, p, c, shades, rng, alive < target / 2 ? MIN_DIST : 30);
                if (s != null) {
                    shades.add(s);
                    spawned++;
                    if (c.awayTicks > 0) s.setMode(ShadeEntity.AWAY);
                    if (c.event == Event.EVERYONE_TURNS) s.setMode(ShadeEntity.TURNS);
                }
            }
        }
        if (c.hushTicks > 0) {
            c.hushTicks -= 10;
            if (c.hushTicks <= 0) c.awayTicks = 100; // they come back facing away
        }
        if (!farewell) maybeEvent(level, p, c, shades, elapsed, left, rng);
        return spawned;
    }

    private static int targetCount(ServerLevel level, ServerPlayer p) {
        double n = ReverieConfig.get(ReverieConfig.SHADE_COUNT);
        if (level.getBiome(p.blockPosition()).is(DreamlandBiomeKeys.HOLLOW_SPIRES)) n *= 1.6;
        return (int) Math.min(n, ReverieConfig.get(ReverieConfig.MAX_SHADES_PER_DREAMER));
    }

    private static double groundY(ServerLevel level, Vec3 at, double fallback) {
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        if (!level.isLoaded(new net.minecraft.core.BlockPos(x, 0, z))) return fallback;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return top > level.getMinBuildHeight() + 4 && Math.abs(top - fallback) < 8 ? top : fallback;
    }

    private static ShadeEntity spawn(ServerLevel level, ServerPlayer p, Crowd c, List<ShadeEntity> shades, RandomSource rng, double minDist) {
        Vec3 pos = p.position();
        for (int attempt = 0; attempt < 14; attempt++) {
            Vec3 at;
            if (!shades.isEmpty() && rng.nextFloat() < 0.6f) {
                ShadeEntity near = shades.get(rng.nextInt(shades.size()));
                double a = rng.nextDouble() * Math.PI * 2;
                double r = 2.5 + rng.nextDouble() * 3.5;
                at = near.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            } else {
                double a = rng.nextDouble() * Math.PI * 2;
                double r = minDist + rng.nextDouble() * (MAX_DIST - minDist);
                at = pos.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            double horizontal = Math.hypot(at.x - pos.x, at.z - pos.z);
            if (horizontal < minDist || horizontal > MAX_DIST) continue;
            int x = Mth.floor(at.x);
            int z = Mth.floor(at.z);
            if (!level.isLoaded(new net.minecraft.core.BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            ShadeEntity.Variant variant;
            double y;
            if (top > level.getMinBuildHeight() + 4 && Math.abs(top - pos.y) < 45) {
                y = top;
                variant = pickGrounded(rng);
            } else if (rng.nextFloat() < 0.35f) {
                y = pos.y - 8 + rng.nextDouble() * 26;
                variant = ShadeEntity.Variant.HANGING;
            } else {
                continue;
            }
            Vec3 spot = new Vec3(x + 0.5, y, z + 0.5);
            boolean crowded = false;
            for (ShadeEntity s : shades) {
                if (s.position().distanceToSqr(spot) < 2.5 * 2.5) {
                    crowded = true;
                    break;
                }
            }
            if (crowded) continue;
            ShadeEntity s = ModEntities.SHADE.get().create(level);
            if (s == null) return null;
            s.managed = true;
            s.setVariant(variant);
            s.setOwner(p);
            float yaw = (float) (Mth.atan2(pos.z - spot.z, pos.x - spot.x) * (180 / Math.PI)) - 90f;
            s.moveTo(spot.x, spot.y, spot.z, yaw, 0);
            if (!level.addFreshEntity(s)) return null;
            c.ids.add(s.getId());
            return s;
        }
        return null;
    }

    private static ShadeEntity.Variant pickGrounded(RandomSource rng) {
        int r = rng.nextInt(100);
        if (r < 40) return ShadeEntity.Variant.ONLOOKER;
        if (r < 54) return ShadeEntity.Variant.TALL_ONE;
        if (r < 70) return ShadeEntity.Variant.STOOPED_ONE;
        if (r < 81) return ShadeEntity.Variant.WIDE_ARMED;
        if (r < 92) return ShadeEntity.Variant.HEADLESS;
        return ShadeEntity.Variant.DOORFRAME;
    }

    // ------------------------------------------------------------------ rare events

    private static boolean enabled(Event e) {
        return switch (e) {
            case HUSH -> ReverieConfig.get(ReverieConfig.EVENT_HUSH);
            case EVERYONE_TURNS -> ReverieConfig.get(ReverieConfig.EVENT_EVERYONE_TURNS);
            case BEHIND_YOU -> ReverieConfig.get(ReverieConfig.EVENT_BEHIND_YOU);
            case GRAVITY_HICCUP -> ReverieConfig.get(ReverieConfig.EVENT_GRAVITY_HICCUP);
            case COUNTING -> ReverieConfig.get(ReverieConfig.EVENT_COUNTING);
            case MIMIC -> ReverieConfig.get(ReverieConfig.EVENT_MIMIC);
        };
    }

    private static void maybeEvent(ServerLevel level, ServerPlayer p, Crowd c, List<ShadeEntity> shades, long elapsed, long left, RandomSource rng) {
        if (!ReverieConfig.get(ReverieConfig.ENABLE_RARE_EVENTS) || c.event != null || c.hushTicks > 0 || c.awayTicks > 0) return;
        if (elapsed < 400 || left < 300 + FAREWELL_TICKS || rng.nextInt(90) != 0) return;
        Event[] all = Event.values();
        Event e = all[rng.nextInt(all.length)];
        long now = level.getGameTime();
        if (!enabled(e) || c.cooldowns.getOrDefault(e, 0L) > now) return;
        startEvent(level, p, c, shades, e, rng);
        c.cooldowns.put(e, now + 2400 + rng.nextInt(2400));
    }

    /** Starts an event now (also used by /reverie debug shadeevent). */
    public static boolean startEvent(ServerPlayer p, Event e) {
        ServerLevel level = p.serverLevel();
        Crowd c = CROWDS.get(p.getUUID());
        if (c == null || !DreamManager.isDreaming(p)) return false;
        List<ShadeEntity> shades = new ArrayList<>();
        for (int id : c.ids) if (level.getEntity(id) instanceof ShadeEntity s) shades.add(s);
        startEvent(level, p, c, shades, e, p.getRandom());
        return true;
    }

    private static void startEvent(ServerLevel level, ServerPlayer p, Crowd c, List<ShadeEntity> shades, Event e, RandomSource rng) {
        switch (e) {
            case HUSH -> {
                // Everyone vanishes at once; the world goes quiet; then they return, facing away.
                for (ShadeEntity s : shades) s.fadeOut();
                c.hushTicks = 240 + rng.nextInt(160);
                c.event = e;
                c.eventTicks = c.hushTicks + 100;
                Net.send(p, new ShadeEventPayload(HUSH, c.hushTicks));
            }
            case EVERYONE_TURNS -> {
                for (ShadeEntity s : shades) s.setMode(ShadeEntity.TURNS);
                c.event = e;
                c.eventTicks = 400;
                Net.send(p, new ShadeEventPayload(TURNS, 400));
            }
            case BEHIND_YOU -> {
                Vec3 look = p.getViewVector(1f).multiply(1, 0, 1).normalize();
                if (look.lengthSqr() < 1e-4) return;
                double dist = 7 + rng.nextDouble() * 2;
                Vec3 at = p.position().subtract(look.scale(dist));
                ShadeEntity s = ModEntities.SHADE.get().create(level);
                if (s == null) return;
                s.managed = true;
                s.setVariant(ShadeEntity.Variant.ONLOOKER);
                s.setOwner(p);
                s.setInstant();
                float yaw = (float) (Mth.atan2(p.getZ() - at.z, p.getX() - at.x) * (180 / Math.PI)) - 90f;
                s.moveTo(at.x, groundY(level, at, p.getY()), at.z, yaw, 0);
                if (level.addFreshEntity(s)) {
                    c.behindId = s.getId();
                    c.behindTicks = 10;
                    c.event = e;
                    c.eventTicks = 20;
                }
            }
            case GRAVITY_HICCUP -> {
                // Safe only with ground right below.
                var below = p.blockPosition().below();
                boolean ground = p.onGround() || !level.getBlockState(below).isAir() || !level.getBlockState(below.below()).isAir();
                if (!ground) return;
                p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 0, false, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 180, 0, false, false, false));
                c.event = e;
                c.eventTicks = 120;
                level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.8f, 1.4f);
                Net.send(p, new ShadeEventPayload(HICCUP, 100));
            }
            case COUNTING -> {
                // A perfect grid in front of the dreamer, everyone pointing the same way along the rows.
                Vec3 look = p.getViewVector(1f).multiply(1, 0, 1);
                look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
                Vec3 side = new Vec3(-look.z, 0, look.x);
                Vec3 center = p.position().add(look.scale(38));
                double y = groundY(level, center, p.getY());
                int n = shades.size();
                int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n * 2.0)));
                int rows = Math.max(1, (n + cols - 1) / cols);
                float yaw = (float) (Mth.atan2(side.z, side.x) * (180 / Math.PI)) - 90f;
                for (int i = 0; i < n; i++) {
                    ShadeEntity s = shades.get(i);
                    if (s.fading()) continue;
                    Vec3 at = center.add(side.scale((i % cols - (cols - 1) / 2.0) * 2.5)).add(look.scale((i / cols - (rows - 1) / 2.0) * 2.5));
                    c.countingHomes.put(s.getId(), s.position());
                    s.setPos(at.x, y, at.z);
                    s.setFixedYaw(yaw);
                    s.setMode(ShadeEntity.FIXED);
                }
                c.event = e;
                c.eventTicks = 140;
                Net.send(p, new ShadeEventPayload(COUNTING, 140));
            }
            case MIMIC -> startMimic(level, p, c, rng);
        }
    }

    /** Mimic: a silhouette in your own shape, about 30 blocks away, standing on ground at about your height. */
    private static void startMimic(ServerLevel level, ServerPlayer p, Crowd c, RandomSource rng) {
        if (c.mimicId >= 0) return;
        for (int attempt = 0; attempt < 20; attempt++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = 24 + rng.nextDouble() * 12;
            Vec3 at = p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            int x = Mth.floor(at.x);
            int z = Mth.floor(at.z);
            if (!level.isLoaded(new net.minecraft.core.BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (top <= level.getMinBuildHeight() + 4 || Math.abs(top - p.getY()) > 12) continue;
            ShadeEntity s = ModEntities.SHADE.get().create(level);
            if (s == null) return;
            s.managed = true;
            s.setVariant(ShadeEntity.Variant.MIMIC);
            s.setOwner(p);
            s.setMode(ShadeEntity.FIXED);
            s.setFixedYaw(p.getYRot());
            s.moveTo(x + 0.5, top, z + 0.5, p.getYRot(), 0);
            if (!level.addFreshEntity(s)) return;
            c.mimicId = s.getId();
            c.mimicAge = 0;
            c.mimicOrigin = s.position();
            c.playerOrigin = p.position();
            c.mimicHistory.clear();
            c.event = Event.MIMIC;
            c.eventTicks = MIMIC_MAX;
            return;
        }
    }

    /** Copies the dreamer's movement three seconds late, then walks off the edge of its island. Every tick. */
    private static void mimicTick(ServerLevel level) {
        for (var entry : CROWDS.entrySet()) {
            Crowd c = entry.getValue();
            if (c.mimicId < 0) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (!(level.getEntity(c.mimicId) instanceof ShadeEntity s) || p == null || ++c.mimicAge > MIMIC_MAX) {
                if (level.getEntity(c.mimicId) instanceof ShadeEntity gone) gone.discard();
                c.mimicId = -1;
                if (c.event == Event.MIMIC) c.event = null;
                continue;
            }
            if (s.fading()) continue;
            c.mimicHistory.addLast(new double[]{p.getX(), p.getY(), p.getZ(), p.getYRot()});
            if (c.mimicAge <= MIMIC_COPY) {
                if (c.mimicHistory.size() <= MIMIC_DELAY) continue;
                double[] then = c.mimicHistory.removeFirst();
                Vec3 target = c.mimicOrigin.add(then[0] - c.playerOrigin.x, 0, then[2] - c.playerOrigin.z);
                double y = groundY(level, target, s.getY());
                s.setPos(target.x, y, target.z);
                s.setFixedYaw((float) then[3]);
            } else {
                // Done copying: it turns away and walks straight on until the ground ends.
                if (c.mimicAge == MIMIC_COPY + 1) s.setFixedYaw(s.fixedYaw() + 90 + p.getRandom().nextInt(180));
                float yaw = s.fixedYaw() * Mth.DEG_TO_RAD;
                Vec3 next = s.position().add(-Mth.sin(yaw) * 0.12, 0, Mth.cos(yaw) * 0.12);
                int x = Mth.floor(next.x);
                int z = Mth.floor(next.z);
                int top = level.isLoaded(new net.minecraft.core.BlockPos(x, 0, z))
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : level.getMinBuildHeight();
                if (top < s.getY() - 2) {
                    s.setDrift(new Vec3(0, -0.35, 0)); // over the edge
                    if (c.mimicAge % 20 == 0 || s.getY() < c.mimicOrigin.y - 12) s.fadeOut();
                } else {
                    s.setPos(next.x, Math.abs(top - s.getY()) < 2 ? top : s.getY(), next.z);
                }
            }
        }
    }

    private static void endEvent(ServerLevel level, Crowd c, List<ShadeEntity> shades) {
        for (ShadeEntity s : shades) {
            if (s.mode() == ShadeEntity.TURNS || s.mode() == ShadeEntity.FIXED) s.setMode(ShadeEntity.FACE);
            Vec3 home = c.countingHomes.get(s.getId());
            if (home != null) s.setPos(home.x, home.y, home.z);
        }
        c.countingHomes.clear();
        c.event = null;
    }

    /** Behind You: half a second, gone the moment you turn to look. Checked every tick. */
    private static void behindYouTick(ServerLevel level) {
        for (var entry : CROWDS.entrySet()) {
            Crowd c = entry.getValue();
            if (c.behindId < 0) continue;
            Entity e = level.getEntity(c.behindId);
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(entry.getKey());
            boolean seen = p != null && e != null && ShadeLook.looksAt(p, e.position().add(0, 1, 0), 0.3);
            if (e == null || p == null || seen || --c.behindTicks <= 0) {
                if (e != null) e.discard();
                c.behindId = -1;
            }
        }
    }
}
