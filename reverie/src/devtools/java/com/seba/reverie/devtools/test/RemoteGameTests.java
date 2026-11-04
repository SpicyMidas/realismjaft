package com.seba.reverie.devtools.test;

import java.util.Set;

import com.seba.reverie.compat.create.CreateContent;
import com.seba.reverie.compat.create.remote.LatchRemoteItem;
import com.seba.reverie.compat.create.remote.LatchRemoteServer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 2 acceptance: the Latch Remote drives a real Redstone Link receiver. */
@PrefixGameTestTemplate(false)
public final class RemoteGameTests {
    private static final BlockPos LINK = new BlockPos(1, 2, 1);

    private RemoteGameTests() {
    }

    private static boolean powered(GameTestHelper h) {
        return h.getBlockState(LINK).getValue(RedstoneLinkBlock.POWERED);
    }

    private static int networkSize(GameTestHelper h) {
        LinkBehaviour lb = BlockEntityBehaviour.get(h.getLevel(), h.absolutePos(LINK), LinkBehaviour.TYPE);
        Set<IRedstoneLinkable> net = Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(h.getLevel()).get(lb.getNetworkKey());
        return net == null ? 0 : net.size();
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 300)
    public static void latchRemoteDrivesReceiver(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        h.setBlock(LINK, AllBlocks.REDSTONE_LINK.getDefaultState()
                .setValue(RedstoneLinkBlock.FACING, Direction.UP).setValue(RedstoneLinkBlock.RECEIVER, true));
        ServerPlayer player = CreateGameTests.fakePlayer(h);
        ItemStack remote = new ItemStack(CreateContent.LATCH_REMOTE.get());
        LatchRemoteItem.setFrequencyItems(remote, new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD));
        h.startSequence()
                .thenExecute(() -> {
                    // Create only builds the link's behaviour on the first NBT read (structures/saves always have one).
                    var be = h.getLevel().getBlockEntity(h.absolutePos(LINK));
                    var registries = h.getLevel().registryAccess();
                    be.loadWithComponents(be.saveWithFullMetadata(registries), registries);
                })
                .thenWaitUntil(() -> h.assertTrue(BlockEntityBehaviour.get(h.getLevel(), h.absolutePos(LINK), LinkBehaviour.TYPE) != null,
                        "redstone link not initialised: " + h.getBlockState(LINK) + " be=" + h.getLevel().getBlockEntity(h.absolutePos(LINK))))
                .thenExecute(() -> {
                    LinkBehaviour lb = BlockEntityBehaviour.get(h.getLevel(), h.absolutePos(LINK), LinkBehaviour.TYPE);
                    lb.setFrequency(true, new ItemStack(Items.DIAMOND));
                    lb.setFrequency(false, new ItemStack(Items.EMERALD));
                    player.getInventory().clearContent();
                    player.moveTo(h.absoluteVec(new BlockPos(1, 2, 3).getCenter()));
                    player.getInventory().setItem(0, remote);
                    LatchRemoteItem.toggle(remote, player);
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(powered(h), "receiver not powered after ON"))
                .thenExecute(() -> LatchRemoteItem.toggle(remote, player))
                .thenIdle(3)
                .thenExecute(() -> h.assertFalse(powered(h), "receiver still powered after OFF"))
                .thenExecute(() -> {
                    int before = LatchRemoteServer.activeLinks();
                    for (int i = 0; i < 301; i++) LatchRemoteItem.toggle(remote, player); // ends ON
                    h.assertTrue(LatchRemoteItem.isActive(remote), "odd toggles should end ON");
                    h.assertTrue(LatchRemoteServer.activeLinks() == before + 1, "double registration: " + LatchRemoteServer.activeLinks());
                    h.assertTrue(networkSize(h) == 2, "network should hold receiver + one remote, has " + networkSize(h));
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(powered(h), "not powered after many toggles"))
                // Dropped / stored: stops. Back in the inventory: resumes (it is still ON).
                .thenExecute(() -> {
                    player.getInventory().setItem(0, ItemStack.EMPTY);
                    LatchRemoteServer.refresh(player);
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertFalse(powered(h), "still powered after the remote left the inventory"))
                .thenExecute(() -> {
                    player.getInventory().setItem(5, remote);
                    LatchRemoteServer.refresh(player);
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(powered(h), "did not resume when carried again"))
                // A copied stack (same UUID) must not register twice.
                .thenExecute(() -> {
                    player.getInventory().setItem(6, remote.copy());
                    LatchRemoteServer.refresh(player);
                    h.assertTrue(networkSize(h) == 2, "duplicate stack registered twice: " + networkSize(h));
                    player.getInventory().setItem(6, ItemStack.EMPTY);
                })
                // Out of Create's link range: no signal.
                .thenExecute(() -> {
                    player.moveTo(player.getX() + 600, player.getY(), player.getZ());
                    LatchRemoteServer.refresh(player);
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertFalse(powered(h), "powered from outside link range"))
                .thenExecute(() -> {
                    player.moveTo(h.absoluteVec(new BlockPos(1, 2, 3).getCenter()));
                    LatchRemoteServer.refresh(player);
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(powered(h), "did not recover in range"))
                // Wrong frequency: no signal.
                .thenExecute(() -> {
                    LatchRemoteItem.setFrequencyItems(remote, new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.EMERALD));
                    LatchRemoteServer.refresh(player);
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertFalse(powered(h), "powered on the wrong frequency"))
                .thenExecute(() -> {
                    player.getInventory().clearContent();
                    LatchRemoteServer.refresh(player);
                })
                .thenSucceed();
    }
}
