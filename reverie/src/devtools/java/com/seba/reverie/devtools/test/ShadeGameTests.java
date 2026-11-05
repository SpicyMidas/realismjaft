package com.seba.reverie.devtools.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.devtools.ReverieDevTools;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.registry.ModEntities;
import com.seba.reverie.shade.ShadeCrowdManager;
import com.seba.reverie.shade.ShadeEntity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 6 acceptance with two real joined dreamers: each gets a crowd at range, approaching makes a shade vanish,
 * events run, shades never serialize, and nothing is left after waking or logging out.
 */
@PrefixGameTestTemplate(false)
public final class ShadeGameTests {
    private static final UUID A = UUID.fromString("5e1f5a1e-0000-4000-8000-0000000005a1");
    private static final UUID B = UUID.fromString("5e1f5a1e-0000-4000-8000-0000000005b2");

    private ShadeGameTests() {
    }

    private static List<ShadeEntity> shadesOf(ServerLevel dream, ServerPlayer owner) {
        List<ShadeEntity> out = new ArrayList<>();
        for (Entity e : dream.getAllEntities()) {
            if (e instanceof ShadeEntity s && (owner == null || s.ownerId() == owner.getId())) out.add(s);
        }
        return out;
    }

    private static ServerPlayer dreamer(GameTestHelper h, UUID id, String name) {
        ServerPlayer p = DreamFlowGameTests.joinAs(h, id, name);
        if (DreamManager.isDreaming(p)) DreamManager.wake(p, DreamManager.Reason.COMMAND); // left over from an aborted run
        DreamManager.resetCooldown(p);
        h.assertTrue(DreamManager.startDream(p, null), name + " could not start a dream");
        return p;
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 1400, batch = "reverie_shades")
    public static void twoDreamersCrowdsAndCleanup(GameTestHelper h) {
        if (DreamTestSupport.skipWithoutDreamland(h)) return;
        var server = h.getLevel().getServer();
        ServerLevel dream = server.getLevel(DreamManager.DREAMLAND);
        final ServerPlayer[] p = new ServerPlayer[2];
        final ShadeEntity[] target = new ShadeEntity[1];
        int flee = ReverieConfig.get(ReverieConfig.SHADE_FLEE_RADIUS);
        int perDreamer = ReverieConfig.get(ReverieConfig.MAX_SHADES_PER_DREAMER);
        h.startSequence()
                .thenExecute(() -> {
                    p[0] = dreamer(h, A, "shade_dreamer_a");
                    p[1] = dreamer(h, B, "shade_dreamer_b");
                })
                .thenIdle(240)
                .thenExecute(() -> {
                    int total = 0;
                    for (ServerPlayer pl : p) {
                        List<ShadeEntity> mine = shadesOf(dream, pl);
                        total += mine.size();
                        h.assertTrue(mine.size() >= 8, pl.getGameProfile().getName() + " has only " + mine.size() + " shades");
                        h.assertTrue(mine.size() <= perDreamer + 4, pl.getGameProfile().getName() + " has " + mine.size() + " shades (cap " + perDreamer + ")");
                        for (ShadeEntity s : mine) {
                            double d = s.position().distanceTo(pl.position());
                            if (!s.fading()) h.assertTrue(d >= 23 && d <= 90, "shade at " + String.format("%.1f", d) + " from its dreamer");
                            h.assertFalse(s.save(new CompoundTag()), "a shade would be saved to disk");
                        }
                    }
                    h.assertFalse(ModEntities.SHADE.get().canSerialize(), "shade type serializes");
                    h.assertTrue(shadesOf(dream, null).size() == total, "shades owned by nobody");
                    ReverieDevTools.log("shades: A=" + shadesOf(dream, p[0]).size() + " B=" + shadesOf(dream, p[1]).size() + " tracked=" + ShadeCrowdManager.count());
                    // Walk toward one: put the dreamer just inside the flee radius of a grounded shade.
                    for (ShadeEntity s : shadesOf(dream, p[0])) {
                        if (!s.fading() && s.variant() != ShadeEntity.Variant.HANGING) {
                            target[0] = s;
                            break;
                        }
                    }
                    h.assertTrue(target[0] != null, "no grounded shade to approach");
                    Vec3 from = target[0].position();
                    Vec3 dir = p[0].position().subtract(from).multiply(1, 0, 1).normalize();
                    // At the shade's own height: the flee radius is a 3D distance and shades stand on other islands too.
                    Vec3 at = from.add(dir.scale(flee - 4));
                    p[0].teleportTo(dream, at.x, from.y, at.z, p[0].getYRot(), p[0].getXRot());
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(target[0].isRemoved() || target[0].fading(), "the approached shade did not vanish");
                    // Events on the other dreamer.
                    h.assertTrue(ShadeCrowdManager.startEvent(p[1], ShadeCrowdManager.Event.HUSH), "Hush did not start");
                    for (ShadeEntity s : shadesOf(dream, p[1])) h.assertTrue(s.fading(), "a shade stayed during the Hush");
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(shadesOf(dream, p[1]).isEmpty(), "shades still present during the Hush: " + shadesOf(dream, p[1]).size());
                    h.assertTrue(shadesOf(dream, p[0]).size() >= 4, "the other dreamer's crowd was affected by the Hush");
                    // Behind You on A: appears and vanishes within half a second.
                    ShadeCrowdManager.startEvent(p[0], ShadeCrowdManager.Event.BEHIND_YOU);
                })
                .thenIdle(14)
                .thenExecute(() -> {
                    for (ShadeEntity s : shadesOf(dream, p[0])) {
                        h.assertTrue(s.position().distanceTo(p[0].position()) > 12 || s.fading(), "Behind You shade lingered");
                    }
                    // Waking clears that dreamer's crowd at once; the other keeps theirs.
                    DreamManager.wake(p[0], DreamManager.Reason.COMMAND);
                    h.assertTrue(shadesOf(dream, p[0]).isEmpty(), "shades left after waking: " + shadesOf(dream, p[0]).size());
                })
                .thenIdle(420) // B's crowd returns after the Hush
                .thenExecute(() -> {
                    h.assertTrue(shadesOf(dream, p[1]).size() >= 8, "crowd did not return after the Hush: " + shadesOf(dream, p[1]).size());
                    // Logging out while dreaming clears the crowd.
                    server.getPlayerList().remove(p[1]);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    h.assertTrue(shadesOf(dream, null).isEmpty(), "shades left after logout: " + shadesOf(dream, null).size());
                    h.assertTrue(ShadeCrowdManager.count() == 0, "crowd manager still tracks " + ShadeCrowdManager.count());
                    // Back in: the dream resumes and so does the crowd; then wake and leave.
                    p[1] = DreamFlowGameTests.joinAs(h, B, "shade_dreamer_b");
                    h.assertTrue(DreamManager.isDreaming(p[1]), "B's dream did not survive the relog");
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(shadesOf(dream, p[1]).size() >= 8, "no crowd after relog: " + shadesOf(dream, p[1]).size());
                    DreamManager.wake(p[1], DreamManager.Reason.COMMAND);
                    h.assertTrue(shadesOf(dream, null).isEmpty(), "shades left after the last dreamer woke");
                    server.getPlayerList().remove(p[1]);
                    server.getPlayerList().remove(p[0]);
                    ReverieDevTools.log("PASS twoDreamersCrowdsAndCleanup");
                })
                .thenSucceed();
    }
}
