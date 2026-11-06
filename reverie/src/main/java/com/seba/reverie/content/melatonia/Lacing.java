package com.seba.reverie.content.melatonia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.seba.reverie.registry.ModComponents;
import com.seba.reverie.registry.ModEffects;
import com.seba.reverie.registry.ModItems;
import com.seba.reverie.registry.ModTags;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/**
 * "Lace any food with Melatonia". The lacing is a data component ({@link Laced}); its effects are applied when the
 * food is eaten, and they are much worse than the dishes' own: Dizziness at a higher level, nausea and slowness, and
 * with Concentrated Extract Sleepyness, weakness and a moment of black. Foods that carry it well
 * (reverie:lacing_bonus_foods: cakes, pies, cookies, soups, stews) also give one gift (two with Concentrated):
 * Night Vision, Speed or Jump Boost for up to three minutes, or a 15 second glimpse of Lucid Sight.
 * Nobody sees that a food is laced except whoever laced it, or someone who holds it in hand for 30 seconds.
 * (Rewriting the stack's FOOD component instead would make laced stacks stop stacking after a save: deserialised
 * food effects never compare equal.)
 */
public final class Lacing {
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62f, 0.45f, 0.95f), 0.9f);
    /** Ticks of holding a laced stack before you notice. */
    public static final int NOTICE_TICKS = 30 * 20;
    private static final Map<UUID, Holding> HOLDING = new HashMap<>();

    private record Holding(ItemStack stack, int ticks) {
    }

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

    /** True if {@code player} knows this stack is laced. */
    public static boolean knownTo(ItemStack stack, UUID player) {
        Laced laced = stack.get(ModComponents.LACED.get());
        return laced != null && laced.knownTo(player);
    }

    /** One laced copy of {@code food}, or empty if it is already laced at least this strongly. The lacer knows. */
    public static ItemStack lace(ItemStack food, int level, @Nullable UUID lacer) {
        if (!isLaceableFood(food) || level < 1 || level(food) >= level) return ItemStack.EMPTY;
        ItemStack out = food.copyWithCount(1);
        Laced before = food.get(ModComponents.LACED.get());
        Laced laced = new Laced(Math.min(level, 2), before == null ? List.of() : before.knownBy());
        out.set(ModComponents.LACED.get(), lacer == null ? laced : laced.withKnown(lacer));
        return out;
    }

    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        ItemStack food = event.getItem();
        int level = level(food);
        if (level <= 0 || !food.has(DataComponents.FOOD)) return;
        poison(entity, level, 1f);
        if (food.is(ModTags.Items.LACING_BONUS_FOODS)) gifts(entity, level, entity.getRandom());
        if (entity.level() instanceof ServerLevel server) {
            server.sendParticles(VIOLET, entity.getX(), entity.getEyeY() - 0.2, entity.getZ(), 14, 0.25, 0.2, 0.25, 0.02);
        }
    }

    /** The bad part. {@code scale} shortens it for a projectile hit. */
    public static void poison(LivingEntity entity, int level, float scale) {
        boolean strong = level >= 2;
        entity.addEffect(new MobEffectInstance(ModEffects.DIZZINESS, ticks(strong ? 3600 : 2400, scale), strong ? 2 : 1));
        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks(strong ? 300 : 200, scale)));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks(strong ? 900 : 600, scale), strong ? 1 : 0));
        if (strong) {
            entity.addEffect(new MobEffectInstance(ModEffects.SLEEPYNESS, ticks(4800, scale)));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks(600, scale)));
            entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks(60, scale)));
        }
    }

    private static int ticks(int base, float scale) {
        return Math.max(20, Math.round(base * scale));
    }

    /** The good part, only from foods that carry Melatonia well: one gift, two with Concentrated Extract. */
    static void gifts(LivingEntity entity, int level, RandomSource random) {
        List<Integer> pool = new ArrayList<>(List.of(0, 1, 2, 3));
        int count = level >= 2 ? 2 : 1;
        for (int n = 0; n < count && !pool.isEmpty(); n++) {
            int pick = pool.remove(random.nextInt(pool.size()));
            // 1.5 to 3 minutes; Lucid Sight is only a 15 second glimpse.
            int minutes = 1800 + random.nextInt(1801);
            switch (pick) {
                case 0 -> give(entity, MobEffects.NIGHT_VISION, minutes, 0);
                case 1 -> give(entity, MobEffects.MOVEMENT_SPEED, minutes, level >= 2 ? 1 : 0);
                case 2 -> give(entity, MobEffects.JUMP, minutes, level >= 2 ? 1 : 0);
                default -> give(entity, ModEffects.LUCID_SIGHT, 300, 0);
            }
        }
    }

    private static void give(LivingEntity entity, Holder<MobEffect> effect, int ticks, int amplifier) {
        entity.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    /** Holding a laced stack in hand for 30 seconds: you notice. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (level(held) == 0 || knownTo(held, id)) {
            held = player.getItemInHand(InteractionHand.OFF_HAND);
            if (level(held) == 0 || knownTo(held, id)) {
                HOLDING.remove(id);
                return;
            }
        }
        Holding was = HOLDING.get(id);
        int ticks = was != null && was.stack() == held ? was.ticks() + 1 : 1;
        if (ticks < NOTICE_TICKS) {
            HOLDING.put(id, new Holding(held, ticks));
            return;
        }
        HOLDING.remove(id);
        held.set(ModComponents.LACED.get(), held.get(ModComponents.LACED.get()).withKnown(id));
        player.displayClientMessage(Component.translatable("reverie.laced.noticed").withColor(tagColor(level(held))), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.3f, 0.6f);
    }

    public static void forget(Player player) {
        HOLDING.remove(player.getUUID());
    }

    /** Colour of the "(Laced)" tag: lilac for Extract, a deeper purple for Concentrated. */
    public static int tagColor(int level) {
        return level >= 2 ? 0x7A3DB8 : 0xC4A0EE;
    }
}
