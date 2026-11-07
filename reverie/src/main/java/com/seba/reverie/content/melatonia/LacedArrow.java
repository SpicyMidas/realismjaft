package com.seba.reverie.content.melatonia;

import javax.annotation.Nullable;

import com.seba.reverie.registry.ModEntities;
import com.seba.reverie.registry.ModItems;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** An arrow dipped in Melatonia: normal arrow damage, then a dose of the laced effects. Leaves a violet trail. */
public class LacedArrow extends AbstractArrow {
    static final float DOSE = 0.5f;
    private static final EntityDataAccessor<Integer> LACING = SynchedEntityData.defineId(LacedArrow.class, EntityDataSerializers.INT);

    public LacedArrow(EntityType<? extends LacedArrow> type, Level level) {
        super(type, level);
    }

    public LacedArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.LACED_ARROW.get(), owner, level, pickup, weapon);
        entityData.set(LACING, Math.max(1, Lacing.level(pickup)));
    }

    public LacedArrow(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.LACED_ARROW.get(), x, y, z, level, pickup, weapon);
        entityData.set(LACING, Math.max(1, Lacing.level(pickup)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LACING, 1);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        ItemStack stack = new ItemStack(ModItems.LACED_ARROW.get());
        stack.set(com.seba.reverie.registry.ModComponents.LACED.get(), new Laced(entityData.get(LACING)));
        return stack;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !inGround && tickCount % 2 == 0) {
            boolean strong = entityData.get(LACING) >= 2;
            level().addParticle(new DustParticleOptions(strong ? new Vector3f(0.45f, 0.22f, 0.72f) : new Vector3f(0.72f, 0.56f, 0.95f), 0.8f),
                    getRandomX(0.4), getRandomY(), getRandomZ(0.4), 0, 0, 0);
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        Lacing.poison(target, entityData.get(LACING), DOSE);
        Collapse.fromLacedHit(target, this);
    }
}
