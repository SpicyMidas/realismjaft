package com.seba.reverie.devtools.test;

import java.util.List;
import java.util.UUID;

import com.seba.reverie.devtools.ReverieDevTools;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dream.DreamSession;
import com.seba.reverie.dream.HomeIsland;
import com.seba.reverie.dreamland.IslandField;
import com.seba.reverie.dreamland.IslandSearch;
import com.seba.reverie.dreamland.LoneDoors;
import com.seba.reverie.dreamland.MemoryIsland;
import com.seba.reverie.dreamland.block.LoneDoorBlock;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 5 mechanics on the real Dreamland (dedicated server only; see DreamTestSupport). */
@PrefixGameTestTemplate(false)
public final class DreamlandGameTests {
    private DreamlandGameTests() {
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 200)
    public static void homeIslandIsSafeAndStable(GameTestHelper h) {
        if (DreamTestSupport.skipWithoutDreamland(h)) return;
        ServerLevel dream = h.getLevel().getServer().getLevel(DreamManager.DREAMLAND);
        int onIslands = 0;
        for (int i = 0; i < 6; i++) {
            UUID id = new UUID(0x5E1F5A1E00000000L + i * 7919L, 0x8000000000000000L + i);
            BlockPos a = HomeIsland.findOrBuild(dream, id);
            BlockPos b = HomeIsland.findOrBuild(dream, id);
            h.assertTrue(a.equals(b), "home moved between dreams: " + a + " vs " + b);
            h.assertTrue(HomeIsland.isSafeStand(dream, a), "home is not a safe stand: " + a);
            if (!dream.getBiome(a).is(com.seba.reverie.dreamland.DreamlandBiomeKeys.VOID_BETWEEN)) onIslands++;
        }
        ReverieDevTools.log("homes on real islands: " + onIslands + "/6");
        ReverieDevTools.log("PASS homeIslandIsSafeAndStable");
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 300)
    public static void loneDoorAndMemoryIsland(GameTestHelper h) {
        if (DreamTestSupport.skipWithoutDreamland(h)) return;
        var server = h.getLevel().getServer();
        ServerLevel dream = server.getLevel(DreamManager.DREAMLAND);
        IslandField field = IslandSearch.field(dream);
        h.assertTrue(field != null, "no island field");
        var player = DreamFlowGameTests.joinAs(h, UUID.fromString("5e1f5a1e-0000-4000-8000-00000000d00e"), "door_walker");
        try {
            BlockPos home = HomeIsland.findOrBuild(dream, player.getUUID());
            player.teleportTo(dream, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
            // A Lone Door right here: walking through it lands you elsewhere, on solid ground.
            BlockPos door = home.offset(2, 0, 0);
            dream.setBlock(door.below(), ModBlocks.LUCID_STONE.get().defaultBlockState(), 3);
            dream.setBlock(door, ModBlocks.LONE_DOOR.get().defaultBlockState().setValue(LoneDoorBlock.FACING, Direction.NORTH)
                    .setValue(LoneDoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
            dream.setBlock(door.above(), ModBlocks.LONE_DOOR.get().defaultBlockState().setValue(LoneDoorBlock.FACING, Direction.NORTH)
                    .setValue(LoneDoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            boolean moved = false;
            for (int attempt = 0; attempt < 8 && !moved; attempt++) {
                player.teleportTo(dream, door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 0, 0);
                LoneDoors.clear();
                LoneDoors.walkThrough(player, door);
                moved = player.blockPosition().distManhattan(door) > 3;
            }
            h.assertTrue(moved, "the Lone Door did not take the player anywhere");
            h.assertTrue(HomeIsland.isSafeStand(dream, player.blockPosition()), "Lone Door landed on an unsafe spot " + player.blockPosition());
            ReverieDevTools.log("lone door: " + door.toShortString() + " -> " + player.blockPosition().toShortString());

            // A door at the top of a Staircase to Nowhere leads back to its island.
            IslandField.Island stairs = null;
            for (int cx = -12; cx <= 12 && stairs == null; cx++)
                for (int cz = -12; cz <= 12 && stairs == null; cz++)
                    for (int layer = 0; layer < 3 && stairs == null; layer++) {
                        var is = field.island(layer, cx, cz);
                        if (is != null && is.shape == IslandField.Shape.STAIRS) stairs = is;
                    }
            if (stairs != null) {
                double t = stairs.radius * 0.8 + stairs.stairLength - 0.5;
                BlockPos top = new BlockPos((int) Math.floor(stairs.cx + Math.cos(stairs.stairAngle) * t),
                        stairs.cy + 2 + (int) Math.floor(t - stairs.radius * 0.8), (int) Math.floor(stairs.cz + Math.sin(stairs.stairAngle) * t));
                BlockPos back = IslandSearch.skyDoorReturn(dream, top);
                h.assertTrue(back != null && Math.hypot(back.getX() - stairs.cx, back.getZ() - stairs.cz) < stairs.radius,
                        "the sky door does not lead back to its island: " + back);
                ReverieDevTools.log("sky door at " + top.toShortString() + " returns to " + back.toShortString());
            }

            // Memory Island from a small bedroom snapshot: inert, scrambled, with the doubled bed in the middle.
            var snapshot = List.of(
                    new DreamSession.SnapshotBlock(new BlockPos(1, 0, 0), Blocks.BOOKSHELF.defaultBlockState()),
                    new DreamSession.SnapshotBlock(new BlockPos(-2, 0, 1), Blocks.OAK_PLANKS.defaultBlockState()),
                    new DreamSession.SnapshotBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_LAMP.defaultBlockState()),
                    new DreamSession.SnapshotBlock(new BlockPos(0, 0, -3), Blocks.OAK_DOOR.defaultBlockState()),
                    new DreamSession.SnapshotBlock(new BlockPos(3, 2, -1), Blocks.LEVER.defaultBlockState()));
            BlockPos origin = home.offset(18, 3, 0);
            MemoryIsland.build(dream, origin, snapshot, 1234L);
            h.assertTrue(dream.getBlockState(origin.offset(0, 1, 0)).is(ModBlocks.DREAMWOOD_PLANKS.get())
                    || dream.getBlockState(origin.offset(-1, 1, 0)).is(ModBlocks.DREAMWOOD_PLANKS.get()), "the doubled bed is missing");
            h.assertTrue(dream.getBlockState(origin.below()).is(ModBlocks.LUCID_STONE.get()), "memory island has no base");
            int levers = 0;
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-10, -3, -10), origin.offset(10, 13, 10))) {
                if (dream.getBlockState(p).is(Blocks.LEVER)) levers++;
            }
            h.assertTrue(levers == 0, "a lever (signal source) survived into the inert memory");
            ReverieDevTools.log("PASS loneDoorAndMemoryIsland");
        } finally {
            server.getPlayerList().remove(player);
        }
        h.succeed();
    }
}
