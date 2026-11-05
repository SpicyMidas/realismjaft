package com.seba.reverie.devtools.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.seba.reverie.dream.DreamBackup;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dream.DreamSession;
import com.seba.reverie.registry.ModComponents;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 4: the dream session can never lose the original inventory. Runs in every mod combination with a fake
 * player whose dimension change is stubbed (the real teleport is covered by {@link DreamFlowGameTests}).
 */
@PrefixGameTestTemplate(false)
public final class DreamGameTests {
    private DreamGameTests() {
    }

    static List<ItemStack> fillInventory(ServerPlayer p) {
        var inv = p.getInventory();
        inv.clearContent();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Nightlight"));
        sword.setDamageValue(17);
        inv.setItem(0, sword);
        inv.setItem(1, new ItemStack(Items.BREAD, 12));
        inv.setItem(5, new ItemStack(Items.DIRT, 64));
        inv.setItem(20, new ItemStack(Items.ENDER_PEARL, 3));
        inv.setItem(35, new ItemStack(Items.TORCH, 40));
        inv.setItem(36, new ItemStack(Items.IRON_BOOTS));
        inv.setItem(38, new ItemStack(Items.IRON_CHESTPLATE));
        inv.setItem(39, new ItemStack(Items.TURTLE_HELMET));
        inv.setItem(40, new ItemStack(Items.SHIELD));
        inv.selected = 0;
        List<ItemStack> copy = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) copy.add(inv.getItem(i).copy());
        return copy;
    }

    static void assertRestored(GameTestHelper h, ServerPlayer p, List<ItemStack> before, int heldSlot) {
        var inv = p.getInventory();
        for (int i = 0; i < before.size(); i++) {
            ItemStack now = inv.getItem(i);
            if (i == heldSlot) {
                h.assertFalse(ItemStack.isSameItem(now, before.get(i)) && !now.isEmpty() && now.has(ModComponents.DREAM_ORIGIN.get()),
                        "the held item came back from the dream");
                continue;
            }
            if (!ItemStack.matches(now, before.get(i))) {
                // A keepsake may have landed in an empty slot; anything else is a loss.
                h.assertTrue(before.get(i).isEmpty(), "slot " + i + " was " + before.get(i) + " now " + now);
            }
        }
    }

    private static FakePlayer freshPlayer(GameTestHelper h, UUID id) {
        // FakePlayer is invulnerable by default; dreams must be tested against real (lethal) damage.
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(id, "dreamer_" + id.toString().substring(0, 6))) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        p.moveTo(h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        return p;
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 100)
    public static void sleepLogoutLoginWake(GameTestHelper h) {
        if (DreamTestSupport.skipWithoutDreamland(h)) return;
        DreamManager.Teleporter real = DreamManager.teleporter;
        DreamManager.teleporter = (p, level, pos, y, x) -> p.moveTo(pos); // fake players cannot change dimension
        try {
            UUID id = UUID.fromString("5e1f5a1e-0000-4000-8000-00000000d001");
            FakePlayer player = freshPlayer(h, id);
            List<ItemStack> before = fillInventory(player);
            player.addEffect(new MobEffectInstance(ModEffects.SLEEPYNESS, 4800));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 1));
            player.experienceLevel = 7;
            player.experienceProgress = 0.25f;
            player.setHealth(9f);

            h.assertTrue(DreamManager.startDream(player, null), "dream did not start");
            var inv = player.getInventory();
            h.assertTrue(inv.getItem(0).is(Items.DIAMOND_SWORD) && inv.getItem(0).has(ModComponents.DREAM_ORIGIN.get()),
                    "the held item is not in the dream hand");
            for (int i = 1; i < inv.getContainerSize(); i++) h.assertTrue(inv.getItem(i).isEmpty(), "dream slot " + i + " not empty");
            h.assertTrue(player.experienceLevel == 0 && player.getActiveEffects().isEmpty() && player.getHealth() == player.getMaxHealth(),
                    "dream rules not applied");
            h.assertTrue(player.getAttribute(Attributes.GRAVITY).getValue() < 0.07, "no dream gravity");

            // The backup on disk holds the original inventory (minus the item taken into the dream).
            var backups = DreamBackup.list(h.getLevel().getServer(), id);
            h.assertFalse(backups.isEmpty(), "no backup file");
            try {
                DreamSession fromDisk = DreamBackup.read(h.getLevel().getServer(), backups.get(backups.size() - 1));
                h.assertTrue(ItemStack.matches(fromDisk.stash().items().get(38), before.get(38)), "backup lost the chestplate");
                h.assertTrue(ItemStack.matches(fromDisk.stash().heldItem(), before.get(0)), "backup lost the held item");
            } catch (java.io.IOException e) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("backup unreadable: " + e);
            }

            // Pick something up in the dream.
            inv.setItem(3, new ItemStack(Items.AMETHYST_SHARD, 5));

            // "Log out": save the player; "log in": load it into a brand new player object.
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            FakePlayer reloaded = freshPlayer(h, id);
            reloaded.load(saved);
            h.assertTrue(DreamManager.isDreaming(reloaded), "dream session lost on relog");
            DreamManager.onLogin(reloaded);
            h.assertTrue(DreamManager.isDreaming(reloaded), "relog with time left should resume");

            DreamManager.wake(reloaded, DreamManager.Reason.COMMAND);
            h.assertFalse(DreamManager.isDreaming(reloaded), "still dreaming after wake");
            assertRestored(h, reloaded, before, 0);
            h.assertTrue(reloaded.experienceLevel == 7 && Math.abs(reloaded.experienceProgress - 0.25f) < 1e-4, "XP not restored");
            h.assertTrue(reloaded.hasEffect(MobEffects.MOVEMENT_SPEED) && !reloaded.hasEffect(ModEffects.SLEEPYNESS), "effects not restored");
            h.assertTrue(Math.abs(reloaded.getHealth() - 9f) < 1e-4, "health not restored");
            h.assertTrue(reloaded.getAttribute(Attributes.GRAVITY).getValue() > 0.079, "dream gravity left over");
            h.assertTrue(reloaded.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "no waking slowness");
            h.assertTrue(DreamManager.onCooldown(reloaded), "no dream cooldown");
            long amethyst = countItems(reloaded, Items.AMETHYST_SHARD);
            h.assertTrue(amethyst == 0 || amethyst == 5, "keepsake count wrong: " + amethyst);
            h.assertTrue(countItems(reloaded, Items.DIAMOND_SWORD) == 0, "the dream sword came back");
            com.seba.reverie.devtools.ReverieDevTools.log("PASS sleepLogoutLoginWake");
            h.succeed();
        } finally {
            DreamManager.teleporter = real;
        }
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 100)
    public static void lethalDamageAndKillWake(GameTestHelper h) {
        if (DreamTestSupport.skipWithoutDreamland(h)) return;
        DreamManager.Teleporter real = DreamManager.teleporter;
        DreamManager.teleporter = (p, level, pos, y, x) -> p.moveTo(pos);
        try {
            FakePlayer player = freshPlayer(h, UUID.fromString("5e1f5a1e-0000-4000-8000-00000000d002"));
            List<ItemStack> before = fillInventory(player);
            player.getInventory().selected = 2; // empty hand: nothing is taken into the dream
            h.assertTrue(DreamManager.startDream(player, null), "dream did not start");
            player.hurt(h.getLevel().damageSources().fellOutOfWorld(), 1000f);
            h.assertTrue(player.isAlive(), "dreamer died from lethal damage");
            DreamManager.tick(player); // the scheduled wake runs on the next player tick
            h.assertFalse(DreamManager.isDreaming(player), "lethal damage did not wake");
            assertRestored(h, player, before, -1);
            h.assertTrue(ItemStack.matches(player.getInventory().getItem(0), before.get(0)), "sword lost when nothing was held");

            // /kill while dreaming must wake, not leave a deathless limbo.
            player.removeAllEffects();
            DreamManager.data(player); // cooldown does not apply to command dreams
            h.assertTrue(DreamManager.startDream(player, null), "second dream did not start");
            player.kill();
            h.assertTrue(player.isAlive(), "/kill killed a dreamer");
            DreamManager.tick(player);
            h.assertFalse(DreamManager.isDreaming(player), "/kill did not wake");
            assertRestored(h, player, before, -1);
            com.seba.reverie.devtools.ReverieDevTools.log("PASS lethalDamageAndKillWake");
            h.succeed();
        } finally {
            DreamManager.teleporter = real;
        }
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void keepsakesAreCappedAndSkipTheHeldItem(GameTestHelper h) {
        var random = net.minecraft.util.RandomSource.create(42);
        List<ItemStack> dream = new ArrayList<>();
        for (int i = 0; i < 36; i++) dream.add(new ItemStack(Items.COBBLESTONE, 1 + i));
        ItemStack held = new ItemStack(Items.NETHERITE_SWORD);
        held.set(ModComponents.DREAM_ORIGIN.get(), net.minecraft.util.Unit.INSTANCE);
        dream.add(held);
        int total = 0;
        for (int round = 0; round < 400; round++) {
            var kept = com.seba.reverie.dream.Keepsakes.roll(dream, random, false);
            h.assertTrue(kept.size() <= 1, "more than maxKeepsakes kept: " + kept.size());
            for (ItemStack k : kept) h.assertFalse(k.is(Items.NETHERITE_SWORD), "held item kept");
            total += kept.size();
        }
        // 36 stacks at 5% each: practically every dream keeps one; mostly fine either way, just not never.
        h.assertTrue(total > 0, "keepsakes never happen");
        List<ItemStack> lonely = List.of(new ItemStack(Items.COBBLESTONE));
        int kept = 0;
        for (int round = 0; round < 2000; round++) kept += com.seba.reverie.dream.Keepsakes.roll(lonely, random, false).size();
        h.assertTrue(kept > 40 && kept < 180, "single-stack keepsake rate off (expected ~5%): " + kept + "/2000");
        h.succeed();
    }

    static long countItems(ServerPlayer p, net.minecraft.world.item.Item item) {
        long n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    @SuppressWarnings("unused")
    private static BlockPos origin() {
        return BlockPos.ZERO;
    }
}
