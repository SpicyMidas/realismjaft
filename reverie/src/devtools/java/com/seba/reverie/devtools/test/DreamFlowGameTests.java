package com.seba.reverie.devtools.test;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.registry.ModComponents;
import com.seba.reverie.registry.ModEffects;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 4 end to end with a real joined player: sleep at noon in a real bed -> real dimension change into
 * Dreamland -> log out (player file saved) -> log in (file loaded) -> resume -> wake back in the bed.
 * Only registered without Create (Create's login sync cannot talk to a mock connection).
 */
@PrefixGameTestTemplate(false)
public final class DreamFlowGameTests {
    private static final UUID ID = UUID.fromString("5e1f5a1e-0000-4000-8000-00000000f10e");

    private DreamFlowGameTests() {
    }

    private static ServerPlayer join(GameTestHelper h) {
        return joinAs(h, ID, "flow_dreamer");
    }

    static ServerPlayer joinAs(GameTestHelper h, UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        var server = h.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, h.getLevel(), profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        return server.getPlayerList().getPlayer(id);
    }

    private static void placeBed(GameTestHelper h, BlockPos foot) {
        BlockState footState = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT);
        h.setBlock(foot, footState);
        h.setBlock(foot.east(), footState.setValue(BedBlock.PART, BedPart.HEAD));
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 600, skyAccess = true, batch = "reverie_time_dream")
    public static void realSleepDreamRelogWake(GameTestHelper h) {
        if (DreamTestSupport.skipWithoutDreamland(h)) return;
        var server = h.getLevel().getServer();
        boolean daylight = server.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        BlockPos foot = new BlockPos(0, 1, 1);
        BlockPos head = foot.east();
        placeBed(h, foot);
        final ServerPlayer[] p = new ServerPlayer[1];
        final List<ItemStack>[] before = new List[1];
        final long[] dayTimeAtSleep = new long[1];
        h.startSequence()
                .thenExecute(() -> {
                    server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
                    h.getLevel().setDayTime(6000); // noon
                    p[0] = join(h);
                    DreamManager.resetCooldown(p[0]); // the player file remembers the cooldown of earlier runs
                    p[0].teleportTo(h.getLevel(), h.absolutePos(foot).getX() + 0.5, h.absolutePos(foot).getY() + 1, h.absolutePos(foot).getZ() - 0.5, 0, 0);
                    before[0] = DreamGameTests.fillInventory(p[0]);
                    p[0].addEffect(new MobEffectInstance(ModEffects.SLEEPYNESS, 4800));
                    var result = p[0].startSleepInBed(h.absolutePos(head));
                    h.assertTrue(result.right().isPresent(), "could not sleep at noon with Sleepyness: " + result.left());
                    dayTimeAtSleep[0] = h.getLevel().getDayTime();
                })
                .thenExecute(() -> {
                    // Mock connections are not ticked by the server; tick the player like the network would.
                    for (int i = 0; i < 101 && !DreamManager.isDreaming(p[0]); i++) p[0].doTick();
                    h.assertTrue(DreamManager.isDreaming(p[0]), "did not fall into a dream after sleeping");
                    h.assertTrue(p[0].level().dimension().equals(DreamManager.DREAMLAND), "not in Dreamland but " + p[0].level().dimension());
                    h.assertTrue(p[0].getInventory().getItem(0).has(ModComponents.DREAM_ORIGIN.get()), "held item not carried");
                    h.assertTrue(DreamManager.data(p[0]).session().ret().bed().isPresent(), "bed not remembered");
                })
                .thenIdle(2)
                .thenExecute(() -> h.assertTrue(Math.abs(h.getLevel().getDayTime() - dayTimeAtSleep[0]) < 200,
                        "world time skipped: " + dayTimeAtSleep[0] + " -> " + h.getLevel().getDayTime()))
                .thenExecute(() -> {
                    // Log out (saves the player file) and back in (loads it).
                    server.getPlayerList().remove(p[0]);
                    p[0] = join(h);
                    h.assertTrue(DreamManager.isDreaming(p[0]), "dream lost across relog");
                    h.assertTrue(p[0].level().dimension().equals(DreamManager.DREAMLAND), "relogged outside Dreamland");
                    h.assertTrue(p[0].getInventory().getItem(1).isEmpty(), "dream inventory not kept across relog");
                })
                .thenExecute(() -> {
                    // Falling below the void line wakes you.
                    var dream = server.getLevel(DreamManager.DREAMLAND);
                    p[0].teleportTo(dream, p[0].getX(), dream.getMinBuildHeight() + 1, p[0].getZ(), 0, 0);
                    p[0].doTick();
                    h.assertFalse(DreamManager.isDreaming(p[0]), "falling into the void did not wake");
                    h.assertTrue(p[0].level().dimension().equals(h.getLevel().dimension()), "did not return to the bed's dimension");
                    h.assertTrue(p[0].position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(head))) < 4, "woke far from the bed");
                    DreamGameTests.assertRestored(h, p[0], before[0], 0);
                })
                .thenExecute(() -> {
                    // At night: a sleepy sleeper dreams and the night is NOT skipped.
                    h.assertTrue(p[0].isSleeping(), "no wake-up pose in the bed");
                    for (int i = 0; i < 35 && p[0].isSleeping(); i++) p[0].doTick();
                    h.assertFalse(p[0].isSleeping(), "wake-up pose never ended");
                    DreamManager.resetCooldown(p[0]);
                    h.getLevel().setDayTime(18000);
                    p[0].teleportTo(h.getLevel(), h.absolutePos(foot).getX() + 0.5, h.absolutePos(foot).getY() + 1, h.absolutePos(foot).getZ() - 0.5, 0, 0);
                    p[0].addEffect(new MobEffectInstance(ModEffects.SLEEPYNESS, 4800));
                    var result = p[0].startSleepInBed(h.absolutePos(head));
                    h.assertTrue(result.right().isPresent(), "could not sleep at night: " + result.left());
                    dayTimeAtSleep[0] = h.getLevel().getDayTime();
                    for (int i = 0; i < 101 && !DreamManager.isDreaming(p[0]); i++) p[0].doTick();
                    h.assertTrue(DreamManager.isDreaming(p[0]), "no dream at night");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    long now = h.getLevel().getDayTime();
                    h.assertTrue(now - dayTimeAtSleep[0] < 200 && now % 24000 > 12000, "the night was skipped: " + dayTimeAtSleep[0] + " -> " + now);
                    DreamManager.wake(p[0], DreamManager.Reason.COMMAND);
                    h.assertFalse(DreamManager.isDreaming(p[0]), "night dream did not end");
                })
                .thenExecute(() -> {
                    // A dream whose time ran out while logged out (or the server crashed) wakes on login.
                    p[0].getInventory().clearContent();
                    before[0] = DreamGameTests.fillInventory(p[0]);
                    p[0].getInventory().selected = 4; // empty hand
                    h.assertTrue(DreamManager.startDream(p[0], null), "command dream did not start");
                    var data = DreamManager.data(p[0]);
                    server.getPlayerList().remove(p[0]);
                    h.getLevel().setDayTime(h.getLevel().getDayTime()); // keep clocks; expiry uses game time
                })
                .thenExecute(() -> {
                    // Simulate the dream having ended long ago by rejoining after editing the saved session.
                    var tag = server.getPlayerList().getPlayer(ID);
                    h.assertTrue(tag == null, "player still online");
                })
                .thenExecute(() -> {
                    p[0] = join(h);
                    var data = DreamManager.data(p[0]);
                    h.assertTrue(data.dreaming(), "session lost");
                    expireSession(p[0]);
                    DreamManager.onLogin(p[0]);
                    p[0].doTick();
                    h.assertFalse(DreamManager.isDreaming(p[0]), "expired dream did not wake on login");
                    DreamGameTests.assertRestored(h, p[0], before[0], -1);
                    h.assertTrue(ItemStack.matches(p[0].getInventory().getItem(0), before[0].get(0)), "sword lost");
                    server.getPlayerList().remove(p[0]);
                    server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylight, server);
                    com.seba.reverie.devtools.ReverieDevTools.log("PASS realSleepDreamRelogWake");
                })
                .thenSucceed();
    }

    /** Pretend the dream ended already (as if the player was offline past its end). */
    private static void expireSession(ServerPlayer player) {
        try {
            var data = DreamManager.data(player);
            var f = data.getClass().getDeclaredField("session");
            f.setAccessible(true);
            var s = (com.seba.reverie.dream.DreamSession) f.get(data);
            f.set(data, s.withEnd(DreamManager.now(player.server) - 1));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
