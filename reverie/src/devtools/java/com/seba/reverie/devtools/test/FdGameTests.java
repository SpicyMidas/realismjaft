package com.seba.reverie.devtools.test;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.fd.FdContent;
import com.seba.reverie.content.melatonia.Lacing;
import com.seba.reverie.content.melatonia.MelatoniaCropBlock;
import com.seba.reverie.devtools.ReverieDevTools;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorwing.farmersdelight.common.block.PieBlock;
import vectorwing.farmersdelight.common.block.RichSoilBlock;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/** Farmer's Delight integration. Registered only when FD is loaded. */
@PrefixGameTestTemplate(false)
public final class FdGameTests {
    private FdGameTests() {
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void recipesLoad(GameTestHelper h) {
        var recipes = h.getLevel().getServer().getRecipeManager();
        for (String id : new String[]{"cooking/melatonia_extract", "cooking/concentrated_melatonia_extract", "cooking/lullaby_stew",
                "cooking/dozing_dumplings", "cooking/melatonia_syrup"}) {
            var r = recipes.byKey(Reverie.id(id));
            h.assertTrue(r.isPresent(), "missing recipe " + id);
            h.assertTrue(r.get().value().getType() == ModRecipeTypes.COOKING.get(), id + " is not a cooking pot recipe");
        }
        for (String id : new String[]{"cutting/wild_melatonia", "cutting/dreamy_pie"}) {
            var r = recipes.byKey(Reverie.id(id));
            h.assertTrue(r.isPresent() && r.get().value().getType() == ModRecipeTypes.CUTTING.get(), "missing cutting recipe " + id);
        }
        for (String id : new String[]{"dreamy_pie", "dreamy_pie_from_slices", "night_bloom_roll"}) {
            h.assertTrue(recipes.byKey(Reverie.id(id)).isPresent(), "missing recipe " + id);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void dishesLaceAndSyrupLaces(GameTestHelper h) {
        for (var item : new ItemStack[]{new ItemStack(FdContent.LULLABY_STEW.get()), new ItemStack(FdContent.DOZING_DUMPLINGS.get()),
                new ItemStack(FdContent.DREAMY_PIE_SLICE.get()), new ItemStack(FdContent.NIGHT_BLOOM_ROLL.get())}) {
            ItemStack laced = Lacing.lace(item, 1, null);
            h.assertFalse(laced.isEmpty(), item.getHoverName().getString() + " cannot be laced");
            var food = item.getFoodProperties(null);
            h.assertTrue(food != null && food.effects().stream().anyMatch(e -> e.effect().getEffect().is(ModEffects.DIZZINESS)),
                    item.getHoverName().getString() + " has no Dizziness");
        }
        h.assertTrue(Lacing.agentLevel(new ItemStack(FdContent.MELATONIA_SYRUP.get())) == 1, "syrup is not a normal-strength lacing agent");
        var stew = new ItemStack(FdContent.LULLABY_STEW.get()).getFoodProperties(null);
        h.assertTrue(stew.effects().stream().anyMatch(e -> e.effect().getEffect().is(vectorwing.farmersdelight.common.registry.ModEffects.COMFORT)),
                "Lullaby Stew gives no Comfort");
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty", skyAccess = true)
    public static void richSoilGrowsMelatonia(GameTestHelper h) {
        BlockPos soil = new BlockPos(1, 1, 1);
        BlockPos crop = soil.above();
        h.setBlock(soil, vectorwing.farmersdelight.common.registry.ModBlocks.RICH_SOIL_FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        BlockState young = ModBlocks.MELATONIA.get().defaultBlockState();
        h.assertTrue(young.canSurvive(h.getLevel(), h.absolutePos(crop)), "Melatonia cannot be planted on Rich Soil Farmland");
        h.setBlock(crop, young);
        boolean boosted = RichSoilBlock.boostPlant(h.getBlockState(crop), h.absolutePos(crop), h.getLevel());
        h.assertTrue(boosted, "Rich Soil boost did not apply");
        h.assertTrue(h.getBlockState(crop).getValue(MelatoniaCropBlock.AGE) > 0, "boost did not age the crop");
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void dreamyPieSlices(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, FdContent.DREAMY_PIE.get());
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(vectorwing.farmersdelight.common.registry.ModItems.IRON_KNIFE.get()));
        BlockPos abs = h.absolutePos(pos);
        var state = h.getBlockState(pos);
        state.useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false));
        h.assertTrue(h.getBlockState(pos).getValue(PieBlock.BITES) == 1, "knife did not cut a slice");
        boolean gotSlice = player.getInventory().contains(new ItemStack(FdContent.DREAMY_PIE_SLICE.get()))
                || !h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(2),
                        e -> e.getItem().is(FdContent.DREAMY_PIE_SLICE.get())).isEmpty();
        h.assertTrue(gotSlice, "no Dreamy Pie slice came out");
        ReverieDevTools.log("PASS dreamyPieSlices");
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void knifeCutsMelatoniaCake(GameTestHelper h) {
        var player = com.seba.reverie.devtools.test.CreateFreeFakePlayer.get(h);
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(pos, com.seba.reverie.registry.ModBlocks.MELATONIA_CAKE.get());
        ItemStack knife = new ItemStack(vectorwing.farmersdelight.common.registry.ModItems.IRON_KNIFE.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, knife);
        var hit = new net.minecraft.world.phys.BlockHitResult(h.absoluteVec(pos.getCenter()), net.minecraft.core.Direction.UP, h.absolutePos(pos), false);
        var result = h.getBlockState(pos).useItemOn(knife, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(result.consumesAction(), "the knife did not cut");
        h.assertTrue(h.getBlockState(pos).getValue(com.seba.reverie.content.melatonia.MelatoniaCakeBlock.BITES) == 1, "no slice was taken");
        h.assertItemEntityPresent(com.seba.reverie.registry.ModItems.MELATONIA_CAKE_SLICE.get(), pos, 2.0);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.succeed();
    }
}
