package com.seba.reverie.devtools.test;

import java.util.List;

import com.seba.reverie.content.melatonia.BottleReturningShapelessRecipe;
import com.seba.reverie.content.melatonia.Lacing;
import com.seba.reverie.content.melatonia.LacingRecipe;
import com.seba.reverie.content.melatonia.MelatoniaCropBlock;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModEffects;
import com.seba.reverie.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 3 acceptance: crop, loot, extracts, lacing, Dizziness. */
@PrefixGameTestTemplate(false)
public final class MelatoniaGameTests {
    private MelatoniaGameTests() {
    }

    private static CraftingInput grid(ItemStack... items) {
        return CraftingInput.of(3, 3, List.of(
                items.length > 0 ? items[0] : ItemStack.EMPTY, items.length > 1 ? items[1] : ItemStack.EMPTY,
                items.length > 2 ? items[2] : ItemStack.EMPTY, items.length > 3 ? items[3] : ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
    }

    @GameTest(templateNamespace = "reverie", template = "empty", skyAccess = true, timeoutTicks = 200, batch = "reverie_time_crop")
    public static void cropGrowsFasterAtNight(GameTestHelper h) {
        BlockPos soil = new BlockPos(1, 0, 1);
        BlockPos crop = soil.above();
        h.setBlock(soil, Blocks.FARMLAND);
        h.setBlock(crop, ModBlocks.MELATONIA.get().defaultBlockState());
        h.startSequence()
                .thenExecute(() -> h.getLevel().setDayTime(18000))
                .thenIdle(2)
                .thenExecute(() -> h.assertTrue(MelatoniaCropBlock.nightMultiplier(h.getLevel(), h.absolutePos(crop)) >= 2.5f, "no night boost"))
                .thenExecute(() -> h.getLevel().setDayTime(6000))
                .thenIdle(2)
                .thenExecute(() -> h.assertTrue(MelatoniaCropBlock.nightMultiplier(h.getLevel(), h.absolutePos(crop)) <= 1f, "boosted at noon"))
                .thenExecute(() -> {
                    // Growth never stops, even in daylight: enough random ticks always mature it.
                    for (int i = 0; i < 4000 && !ModBlocks.MELATONIA.get().isMaxAge(h.getBlockState(crop)); i++) {
                        h.getBlockState(crop).randomTick(h.getLevel(), h.absolutePos(crop), h.getLevel().random);
                    }
                    h.assertTrue(ModBlocks.MELATONIA.get().isMaxAge(h.getBlockState(crop)), "crop never matured");
                    h.assertTrue(h.getBlockState(crop).getValue(MelatoniaCropBlock.AGE) == 3, "max age is not 3");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void cropAndWildLoot(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        BlockState mature = ModBlocks.MELATONIA.get().getStateForAge(3);
        BlockState young = ModBlocks.MELATONIA.get().getStateForAge(1);
        for (int i = 0; i < 20; i++) {
            List<ItemStack> drops = Block.getDrops(mature, h.getLevel(), pos, null);
            int petals = drops.stream().filter(s -> s.is(ModItems.MELATONIA_PETALS.get())).mapToInt(ItemStack::getCount).sum();
            int seeds = drops.stream().filter(s -> s.is(ModItems.MELATONIA_SEEDS.get())).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(petals >= 1 && petals <= 2 && seeds >= 1 && seeds <= 2, "mature drops " + drops);
            List<ItemStack> youngDrops = Block.getDrops(young, h.getLevel(), pos, null);
            h.assertTrue(youngDrops.size() == 1 && youngDrops.get(0).is(ModItems.MELATONIA_SEEDS.get()) && youngDrops.get(0).getCount() == 1,
                    "immature drops " + youngDrops);
            List<ItemStack> wild = Block.getDrops(ModBlocks.WILD_MELATONIA.get().defaultBlockState(), h.getLevel(), pos, null);
            h.assertTrue(wild.stream().anyMatch(s -> s.is(ModItems.MELATONIA_SEEDS.get())), "wild melatonia dropped no seeds");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void lacingRules(GameTestHelper h) {
        LacingRecipe recipe = new LacingRecipe(CraftingBookCategory.MISC);
        var registries = h.getLevel().registryAccess();
        ItemStack bread = new ItemStack(Items.BREAD, 5);
        ItemStack steak = new ItemStack(Items.COOKED_BEEF);
        ItemStack extract = new ItemStack(ModItems.MELATONIA_EXTRACT.get());
        ItemStack strong = new ItemStack(ModItems.CONCENTRATED_MELATONIA_EXTRACT.get());

        CraftingInput in = grid(bread, extract);
        h.assertTrue(recipe.matches(in, h.getLevel()), "bread + extract did not match");
        ItemStack laced = recipe.assemble(in, registries);
        h.assertTrue(laced.is(Items.BREAD) && laced.getCount() == 1 && Lacing.level(laced) == 1, "bad laced bread " + laced);
        var rest = recipe.getRemainingItems(in);
        h.assertTrue(rest.get(1).is(Items.GLASS_BOTTLE) && rest.get(0).isEmpty(), "bottle not returned: " + rest);

        h.assertTrue(Lacing.level(recipe.assemble(grid(extract, steak), registries)) == 1, "steak not laced");
        h.assertTrue(Lacing.level(recipe.assemble(grid(steak, strong), registries)) == 2, "concentrated is not strong");
        h.assertFalse(recipe.matches(grid(laced, extract.copy()), h.getLevel()), "laced twice at the same level");
        h.assertTrue(Lacing.level(recipe.assemble(grid(laced, strong), registries)) == 2, "level 1 did not upgrade to 2");
        h.assertFalse(recipe.matches(grid(bread, steak, extract), h.getLevel()), "two foods matched");
        h.assertFalse(recipe.matches(grid(extract, extract.copy()), h.getLevel()), "extract laced itself");
        h.assertFalse(recipe.matches(grid(new ItemStack(Items.STICK), extract), h.getLevel()), "non-food matched");
        h.assertFalse(recipe.matches(grid(bread), h.getLevel()), "food alone matched");
        // Stew stacks to 1 and converts to a bowl: still works and keeps that.
        ItemStack stew = recipe.assemble(grid(new ItemStack(ModItems.SLEEPY_STEW.get()), extract), registries);
        h.assertTrue(Lacing.level(stew) == 1 && stew.get(net.minecraft.core.component.DataComponents.FOOD).usingConvertsTo().isPresent(), "stew lost its bowl");

        // Laced stacks stack with identical laced stacks, also after a save/load round trip.
        ItemStack other = recipe.assemble(grid(new ItemStack(Items.BREAD), extract), registries);
        h.assertTrue(ItemStack.isSameItemSameComponents(laced, other), "fresh laced breads do not stack");
        Tag saved = laced.save(registries);
        ItemStack loaded = ItemStack.parseOptional(registries, (net.minecraft.nbt.CompoundTag) saved);
        h.assertTrue(ItemStack.isSameItemSameComponents(loaded, other), "laced bread stops stacking after a reload");
        h.assertFalse(ItemStack.isSameItemSameComponents(laced, new ItemStack(Items.BREAD)), "laced stacks with plain bread");
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void concentrateReturnsTwoBottles(GameTestHelper h) {
        var holder = h.getLevel().getRecipeManager().byKey(com.seba.reverie.Reverie.id("concentrated_melatonia_extract")).orElse(null);
        h.assertTrue(holder != null && holder.value() instanceof BottleReturningShapelessRecipe, "concentrate recipe missing");
        var recipe = (BottleReturningShapelessRecipe) holder.value();
        ItemStack e = new ItemStack(ModItems.MELATONIA_EXTRACT.get());
        CraftingInput in = grid(e, e.copy(), e.copy(), new ItemStack(Items.GLOWSTONE_DUST));
        h.assertTrue(recipe.matches(in, h.getLevel()), "concentrate recipe does not match");
        long bottles = recipe.getRemainingItems(in).stream().filter(s -> s.is(Items.GLASS_BOTTLE)).count();
        h.assertTrue(bottles == 2, "returned " + bottles + " bottles");
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void eatingLacedFoodAndMilk(GameTestHelper h) {
        var player = CreateFreeFakePlayer.get(h);
        player.removeAllEffects();
        ItemStack laced = Lacing.lace(new ItemStack(Items.BREAD), 2, null);
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, laced, 0, ItemStack.EMPTY));
        h.assertTrue(player.hasEffect(ModEffects.DIZZINESS) && player.hasEffect(ModEffects.SLEEPYNESS), "strong lacing did not apply effects");
        player.removeEffectsCuredBy(EffectCures.MILK);
        h.assertFalse(player.hasEffect(ModEffects.DIZZINESS), "milk did not clear Dizziness");
        // The extract's own food effect.
        var food = new ItemStack(ModItems.MELATONIA_EXTRACT.get()).get(net.minecraft.core.component.DataComponents.FOOD);
        h.assertTrue(food.effects().stream().anyMatch(e -> e.effect().getEffect().is(ModEffects.DIZZINESS)), "extract has no Dizziness");
        h.assertTrue(food.usingConvertsTo().map(s -> s.is(Items.GLASS_BOTTLE)).orElse(false), "extract does not return a bottle");
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void lacingIsSecretUntilNoticed(GameTestHelper h) {
        var player = CreateFreeFakePlayer.get(h);
        java.util.UUID other = java.util.UUID.randomUUID();
        ItemStack mine = Lacing.lace(new ItemStack(Items.BREAD), 1, player.getUUID());
        h.assertTrue(Lacing.knownTo(mine, player.getUUID()), "the lacer does not know");
        h.assertFalse(Lacing.knownTo(mine, other), "someone else knows");
        // Old saves stored only the level.
        var old = com.seba.reverie.content.melatonia.Laced.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive(2)).getOrThrow();
        h.assertTrue(old.topLevel() == 2 && old.layers().get(0).knownBy().isEmpty(), "old lacing did not load");
        // Someone else's laced pie: unknown until held 30 seconds.
        ItemStack pie = Lacing.lace(new ItemStack(Items.PUMPKIN_PIE), 1, other);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pie);
        for (int i = 0; i < Lacing.NOTICE_TICKS - 1; i++) NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        h.assertFalse(Lacing.knownTo(player.getMainHandItem(), player.getUUID()), "noticed too early");
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        h.assertTrue(Lacing.knownTo(player.getMainHandItem(), player.getUUID()), "not noticed after 30 seconds");
        // A pie carries it well: the bad effects and one gift.
        player.removeAllEffects();
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, player.getMainHandItem(), 0, ItemStack.EMPTY));
        h.assertTrue(player.getEffect(ModEffects.DIZZINESS) != null && player.getEffect(ModEffects.DIZZINESS).getAmplifier() >= 1,
                "laced food is not severe");
        boolean gift = player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION) || player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP) || player.hasEffect(ModEffects.LUCID_SIGHT);
        h.assertTrue(gift, "a laced pie gave no gift");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.removeAllEffects();
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void melatoniaCakeHasFourSlices(GameTestHelper h) {
        var player = CreateFreeFakePlayer.get(h);
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(pos, ModBlocks.MELATONIA_CAKE.get());
        player.removeAllEffects();
        for (int slice = 0; slice < 4; slice++) {
            h.assertBlockPresent(ModBlocks.MELATONIA_CAKE.get(), pos);
            player.getFoodData().setFoodLevel(2);
            var hit = new net.minecraft.world.phys.BlockHitResult(h.absoluteVec(pos.getCenter()), net.minecraft.core.Direction.UP, h.absolutePos(pos), false);
            var result = h.getBlockState(pos).useWithoutItem(h.getLevel(), player, hit);
            h.assertTrue(result.consumesAction(), "slice " + (slice + 1) + " was not eaten");
        }
        h.assertBlockNotPresent(ModBlocks.MELATONIA_CAKE.get(), pos);
        h.assertTrue(player.hasEffect(ModEffects.DIZZINESS), "the cake gave no Dizziness");
        h.assertTrue(player.getFoodData().getFoodLevel() > 2, "the cake did not feed");
        player.removeAllEffects();
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 100)
    public static void lacedSnowballDosesWhatItHits(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        for (String id : new String[]{"laced_snowball", "laced_snowball_concentrated", "laced_arrow", "laced_arrow_concentrated"}) {
            var holder = recipes.byKey(com.seba.reverie.Reverie.id(id)).orElse(null);
            h.assertTrue(holder != null && holder.value() instanceof BottleReturningShapelessRecipe, "missing recipe " + id);
            ItemStack out = holder.value().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(out.getCount() == 8 && Lacing.level(out) == (id.endsWith("concentrated") ? 2 : 1), id + " makes the wrong thing: " + out);
        }
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        var pig = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new BlockPos(1, 2, 1));
        h.startSequence()
                .thenIdle(10)   // let the pig settle on the stone first
                .thenExecute(() -> {
                    var ball = new com.seba.reverie.content.melatonia.LacedSnowball(h.getLevel(), pig.getX(), pig.getY() + 1.6, pig.getZ());
                    ball.setItem(new ItemStack(ModItems.LACED_SNOWBALL.get()));
                    ball.setDeltaMovement(0, -0.5, 0);
                    h.getLevel().addFreshEntity(ball);
                })
                .thenWaitUntil(() -> h.assertTrue(pig.hasEffect(ModEffects.DIZZINESS), "the pig was not dosed"))
                .thenSucceed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void lacedLayersStackWorstFirst(GameTestHelper h) {
        var player = CreateFreeFakePlayer.get(h);
        java.util.UUID me = player.getUUID();
        ItemStack stack = new ItemStack(Items.BREAD, 3);                       // 3 plain
        ItemStack extract = Lacing.lace(new ItemStack(Items.BREAD), 1, me);
        extract.setCount(3);                                                   // 3 with Extract
        ItemStack conc = Lacing.lace(new ItemStack(Items.BREAD), 2, me);
        conc.setCount(3);                                                      // 3 with Concentrated
        h.assertTrue(com.seba.reverie.content.melatonia.LacedStacks.sameExceptLacing(stack, extract), "laced bread is not 'the same food'");
        com.seba.reverie.content.melatonia.LacedStacks.joinInto(stack, extract, 3);
        com.seba.reverie.content.melatonia.LacedStacks.joinInto(stack, conc, 3);
        h.assertTrue(stack.getCount() == 9 && extract.isEmpty() && conc.isEmpty(), "stacks did not join: " + stack);
        var layers = com.seba.reverie.content.melatonia.LacedStacks.concrete(stack);
        h.assertTrue(layers.size() == 2 && layers.get(0).level() == 2 && layers.get(0).count() == 3
                && layers.get(1).level() == 1 && layers.get(1).count() == 3, "not worst first: " + layers);
        // Splitting off 4 takes the top: 3 Concentrated + 1 Extract; the 5 left are 2 Extract + 3 plain.
        ItemStack top = stack.split(4);
        var a = com.seba.reverie.content.melatonia.LacedStacks.concrete(top);
        var b = com.seba.reverie.content.melatonia.LacedStacks.concrete(stack);
        h.assertTrue(a.size() == 2 && a.get(0).count() == 3 && a.get(1).count() == 1, "split-off part wrong: " + a);
        h.assertTrue(b.size() == 1 && b.get(0).count() == 2 && b.get(0).level() == 1 && stack.getCount() == 5, "remaining part wrong: " + b);
        // Eating one from the top pops it.
        player.removeAllEffects();
        ItemStack before = top.copy();
        top.shrink(1);
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, before, 0, top));
        var c = com.seba.reverie.content.melatonia.LacedStacks.concrete(top);
        h.assertTrue(c.get(0).count() == 2 && c.get(0).level() == 2, "eating did not pop the top: " + c);
        h.assertTrue(player.getEffect(ModEffects.SLEEPYNESS) != null, "the Concentrated one was not the one eaten");
        // A plain stack once the laced ones are gone has no component at all (it stacks with plain bread again).
        ItemStack plain = stack.split(5);
        plain.shrink(2);
        com.seba.reverie.content.melatonia.LacedStacks.set(plain, com.seba.reverie.content.melatonia.LacedStacks.drop(
                com.seba.reverie.content.melatonia.LacedStacks.concrete(plain), 2));
        h.assertTrue(ItemStack.isSameItemSameComponents(plain, new ItemStack(Items.BREAD)), "a fully plain stack kept the component");
        // Uniform laced stacks still stack the vanilla way (equal components).
        ItemStack u1 = Lacing.lace(new ItemStack(Items.COOKIE), 1, me);
        ItemStack u2 = Lacing.lace(new ItemStack(Items.COOKIE), 1, me);
        h.assertTrue(ItemStack.isSameItemSameComponents(u1, u2), "two equal laced cookies do not stack");
        // From the crafting grid nobody is named yet: whoever takes it knows.
        ItemStack crafted = Lacing.lace(new ItemStack(Items.APPLE), 1, null);
        h.assertTrue(Lacing.knownTo(crafted, java.util.UUID.randomUUID()), "the crafting result should show it is laced");
        com.seba.reverie.content.melatonia.LacedStacks.claimCrafted(crafted, me);
        h.assertTrue(Lacing.knownTo(crafted, me) && !Lacing.knownTo(crafted, java.util.UUID.randomUUID()), "the crafter was not claimed");
        // Old saves: a bare level and {level, known_by}.
        var old = com.seba.reverie.content.melatonia.Laced.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"level\":2}")).getOrThrow();
        h.assertTrue(old.topLevel() == 2, "old {level} lacing did not load");
        player.removeAllEffects();
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty", timeoutTicks = 100)
    public static void lacedHitKnocksMobsOut(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        var pig = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new BlockPos(1, 2, 1));
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> com.seba.reverie.content.melatonia.Collapse.fromLacedHit(pig, null))
                .thenIdle(3)
                .thenExecute(() -> {
                    var out = pig.getEffect(ModEffects.OUT_COLD);
                    h.assertTrue(out != null && out.getDuration() > 500, "the pig was not knocked out for ~30 s");
                    h.assertTrue(pig.hasPose(net.minecraft.world.entity.Pose.SLEEPING), "the pig is not lying down");
                    h.assertTrue(pig.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) <= 1e-4,
                            "the pig can still walk");
                    pig.removeEffect(ModEffects.OUT_COLD);
                })
                .thenIdle(2)
                .thenExecute(() -> h.assertFalse(pig.hasPose(net.minecraft.world.entity.Pose.SLEEPING), "the pig did not get up"))
                .thenSucceed();
    }
}
