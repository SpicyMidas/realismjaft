package com.seba.reverie.content.melatonia;

import com.seba.reverie.registry.ModComponents;
import com.seba.reverie.registry.ModEffects;
import com.seba.reverie.registry.ModItems;
import com.seba.reverie.registry.ModTags;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import org.joml.Vector3f;

/**
 * "Lace any food with Melatonia". The lacing is a data component; its effects are applied when the food is
 * eaten. (Rewriting the stack's FOOD component instead would make laced stacks stop stacking after a save:
 * deserialised food effects never compare equal.)
 */
public final class Lacing {
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62f, 0.45f, 0.95f), 0.9f);

    private Lacing() {
    }

    /** Lacing strength of an agent (extract = 1, concentrated = 2), 0 if the stack is not an agent. */
    public static int agentLevel(ItemStack stack) {
        if (!stack.is(ModTags.Items.LACING_AGENTS)) return 0;
        return stack.is(ModItems.CONCENTRATED_MELATONIA_EXTRACT.get()) ? 2 : 1;
    }

    public static boolean isLaceableFood(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD) && agentLevel(stack) == 0;
    }

    public static int level(ItemStack stack) {
        Laced laced = stack.get(ModComponents.LACED.get());
        return laced == null ? 0 : laced.level();
    }

    /** One laced copy of {@code food}, or empty if it is already laced at least this strongly. */
    public static ItemStack lace(ItemStack food, int level) {
        if (!isLaceableFood(food) || level < 1 || level(food) >= level) return ItemStack.EMPTY;
        ItemStack out = food.copyWithCount(1);
        out.set(ModComponents.LACED.get(), new Laced(Math.min(level, 2)));
        return out;
    }

    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        int level = level(event.getItem());
        if (level <= 0 || !event.getItem().has(DataComponents.FOOD)) return;
        entity.addEffect(new MobEffectInstance(ModEffects.DIZZINESS, level >= 2 ? 800 : 600));
        if (level >= 2) entity.addEffect(new MobEffectInstance(ModEffects.SLEEPYNESS, 2400));
        if (entity.level() instanceof ServerLevel server) {
            server.sendParticles(VIOLET, entity.getX(), entity.getEyeY() - 0.2, entity.getZ(), 10, 0.25, 0.2, 0.25, 0.02);
        }
    }
}
