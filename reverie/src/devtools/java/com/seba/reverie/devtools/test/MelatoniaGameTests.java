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

    @GameTest(templateNamespace = "reverie", template = "empty", skyAccess = true, timeoutTicks = 200)
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
        ItemStack laced = Lacing.lace(new ItemStack(Items.BREAD), 2);
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
}
