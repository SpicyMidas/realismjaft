package com.seba.reverie.devtools.test;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.compat.create.CreateContent;
import com.seba.reverie.compat.create.CreateNetwork;
import com.seba.reverie.compat.create.gearshift.GateLayout;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlock;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 1 acceptance: real Create kinetics through the Tuff Gearshifts. Registered only when Create is loaded. */
@PrefixGameTestTemplate(false)
public final class CreateGameTests {
    private static final BlockPos MOTOR = new BlockPos(0, 1, 1);
    private static final BlockPos BOX = new BlockPos(1, 1, 1);
    private static final BlockPos SHAFT = new BlockPos(2, 1, 1);
    private static final BlockPos BELOW = new BlockPos(1, 0, 1);

    private CreateGameTests() {
    }

    private static void build(GameTestHelper h, TuffGearshiftBlock block) {
        h.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.setBlock(BOX, block.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(SHAFT, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
    }

    /** Not in the player list, so no login events reach Create (its mock-player sync crashes the test server). */
    static net.minecraft.server.level.ServerPlayer fakePlayer(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5e1f5a1e-0000-4000-8000-00000000ce11"), "reverie_test"));
    }

    private static TuffGearshiftBlockEntity box(GameTestHelper h) {
        return h.getBlockEntity(BOX);
    }

    private static float shaftSpeed(GameTestHelper h) {
        return ((KineticBlockEntity) h.getBlockEntity(SHAFT)).getSpeed();
    }

    private static void expect(GameTestHelper h, String what, float expected) {
        float in = box(h).getSpeed();
        float out = shaftSpeed(h);
        h.assertTrue(Math.abs(out - expected) < 1e-3, what + ": in=" + in + " out=" + out + " expected " + expected);
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 400)
    public static void regularRatiosRedstoneAndMemory(GameTestHelper h) {
        build(h, CreateContent.TUFF_GEARSHIFT.get());
        h.startSequence()
                .thenIdle(3)
                .thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(MOTOR)).generatedSpeed.setValue(64))
                .thenIdle(5)
                .thenExecute(() -> expect(h, "N", 0))
                .thenExecute(() -> box(h).select(1)).thenIdle(4).thenExecute(() -> expect(h, "1", 64 * (float) ReverieConfig.gearRatio(1) * Math.signum(box(h).getSpeed())))
                .thenExecute(() -> box(h).select(2)).thenIdle(4).thenExecute(() -> expect(h, "2", box(h).getSpeed() * (float) ReverieConfig.gearRatio(2)))
                .thenExecute(() -> box(h).select(3)).thenIdle(4).thenExecute(() -> expect(h, "3", box(h).getSpeed() * (float) ReverieConfig.gearRatio(3)))
                .thenExecute(() -> box(h).select(4)).thenIdle(4).thenExecute(() -> expect(h, "4", box(h).getSpeed() * (float) ReverieConfig.gearRatio(4)))
                .thenExecute(() -> h.assertFalse(box(h).select(5), "regular gearshift accepted gear 5"))
                .thenExecute(() -> box(h).select(GateLayout.R)).thenIdle(4).thenExecute(() -> expect(h, "R", -box(h).getSpeed()))
                .thenExecute(() -> box(h).select(2)).thenIdle(4)
                // Redstone forces R and remembers gear 2.
                .thenExecute(() -> h.setBlock(BELOW, Blocks.REDSTONE_BLOCK)).thenIdle(4)
                .thenExecute(() -> {
                    h.assertTrue(box(h).effectiveGear() == GateLayout.R && box(h).selectedGear() == 2, "redstone did not force R");
                    expect(h, "powered R", -box(h).getSpeed());
                    var player = fakePlayer(h);
                    player.moveTo(h.absoluteVec(BOX.above().getCenter()));
                    var result = CreateNetwork.applySelection(player, h.absolutePos(BOX), 3);
                    h.assertTrue(result == CreateNetwork.Selection.LOCKED, "locked gearshift accepted a change: " + result);
                })
                .thenExecute(() -> h.setBlock(BELOW, Blocks.AIR)).thenIdle(4)
                .thenExecute(() -> {
                    h.assertTrue(box(h).effectiveGear() == 2, "did not return to the remembered gear");
                    expect(h, "back to 2", box(h).getSpeed() * (float) ReverieConfig.gearRatio(2));
                    // A gear that would run faster than Create allows is refused (64 rpm x4 = 256 is fine, 128 x4 is not).
                    ((CreativeMotorBlockEntity) h.getBlockEntity(MOTOR)).generatedSpeed.setValue(128);
                })
                .thenIdle(4)
                // Save/reload keeps the gear.
                .thenExecute(() -> {
                    var registries = h.getLevel().registryAccess();
                    CompoundTag tag = box(h).saveWithFullMetadata(registries);
                    BlockState state = h.getBlockState(BOX);
                    var copy = new TuffGearshiftBlockEntity(CreateContent.GEARSHIFT_BE.get(), h.absolutePos(BOX), state);
                    copy.loadWithComponents(tag, registries);
                    h.assertTrue(copy.selectedGear() == 2 && Math.abs(copy.appliedRatio() - (float) ReverieConfig.gearRatio(2)) < 1e-4,
                            "NBT round trip lost the gear");
                    var player = fakePlayer(h);
                    player.moveTo(h.absoluteVec(BOX.above().getCenter()));
                    var fast = CreateNetwork.applySelection(player, h.absolutePos(BOX), 4);
                    h.assertTrue(fast == CreateNetwork.Selection.TOO_FAST, "an over-speed gear was accepted: " + fast);
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 300)
    public static void sportTopGearsAndValidation(GameTestHelper h) {
        build(h, CreateContent.SPORT_TUFF_GEARSHIFT.get());
        h.startSequence()
                .thenIdle(3)
                .thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(MOTOR)).generatedSpeed.setValue(32))
                .thenIdle(5)
                .thenExecute(() -> box(h).select(5)).thenIdle(4).thenExecute(() -> expect(h, "5", box(h).getSpeed() * (float) ReverieConfig.gearRatio(5)))
                .thenExecute(() -> box(h).select(6)).thenIdle(4).thenExecute(() -> expect(h, "6", box(h).getSpeed() * (float) ReverieConfig.gearRatio(6)))
                .thenExecute(() -> box(h).select(GateLayout.N)).thenIdle(4).thenExecute(() -> expect(h, "N", 0))
                .thenExecute(() -> {
                    var player = fakePlayer(h);
                    player.moveTo(h.absoluteVec(BOX.above().getCenter()));
                    h.assertTrue(CreateNetwork.applySelection(player, h.absolutePos(BOX), 9) == CreateNetwork.Selection.INVALID_GEAR, "gear 9 accepted");
                    h.assertTrue(CreateNetwork.applySelection(player, h.absolutePos(BOX), -3) == CreateNetwork.Selection.INVALID_GEAR, "gear -3 accepted");
                    h.assertTrue(CreateNetwork.applySelection(player, h.absolutePos(MOTOR), 1) == CreateNetwork.Selection.NO_GEARSHIFT, "motor accepted");
                    h.assertTrue(CreateNetwork.applySelection(player, h.absolutePos(BOX), 3) == CreateNetwork.Selection.APPLIED, "valid change rejected");
                    h.assertTrue(CreateNetwork.applySelection(player, h.absolutePos(BOX), 3) == CreateNetwork.Selection.UNCHANGED, "no-op bumped state");
                    player.moveTo(player.getX() + 40, player.getY(), player.getZ());
                    h.assertTrue(CreateNetwork.applySelection(player, h.absolutePos(BOX), 4) == CreateNetwork.Selection.TOO_FAR, "out of range accepted");
                })
                .thenIdle(4)
                .thenExecute(() -> expect(h, "3 via payload path", box(h).getSpeed() * (float) ReverieConfig.gearRatio(3)))
                .thenSucceed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 200)
    public static void wrenchRotationKeepsWorking(GameTestHelper h) {
        build(h, CreateContent.TUFF_GEARSHIFT.get());
        h.startSequence()
                .thenIdle(3)
                .thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(MOTOR)).generatedSpeed.setValue(16))
                .thenExecute(() -> box(h).select(3)).thenIdle(5)
                .thenExecute(() -> {
                    // Rotate the axis the way the wrench does, then back.
                    BlockState rotated = h.getBlockState(BOX).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
                    KineticBlockEntity.switchToBlockState(h.getLevel(), h.absolutePos(BOX), rotated);
                })
                .thenIdle(4)
                .thenExecute(() -> h.assertTrue(Math.abs(shaftSpeed(h)) < 1e-3, "rotated gearshift still drives the shaft"))
                .thenExecute(() -> KineticBlockEntity.switchToBlockState(h.getLevel(), h.absolutePos(BOX),
                        h.getBlockState(BOX).setValue(BlockStateProperties.AXIS, Direction.Axis.X)))
                .thenIdle(4)
                .thenExecute(() -> expect(h, "3 after rotating back", box(h).getSpeed() * (float) ReverieConfig.gearRatio(3)))
                .thenExecute(() -> h.destroyBlock(BOX)).thenIdle(3)
                .thenExecute(() -> h.assertTrue(Math.abs(shaftSpeed(h)) < 1e-3, "shaft still turning after the gearshift was broken"))
                .thenSucceed();
    }
}
