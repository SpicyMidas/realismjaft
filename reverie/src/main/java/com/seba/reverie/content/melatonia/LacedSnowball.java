package com.seba.reverie.content.melatonia;

import com.seba.reverie.registry.ModEntities;
import com.seba.reverie.registry.ModItems;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/** A snowball rolled in Melatonia: no damage, but whoever it hits gets a shorter dose of the laced effects. */
public class LacedSnowball extends ThrowableItemProjectile {
    /** Fraction of the eaten dose. */
    static final float DOSE = 0.3f;

    public LacedSnowball(EntityType<? extends LacedSnowball> type, Level level) {
        super(type, level);
    }

    public LacedSnowball(Level level, LivingEntity shooter) {
        super(ModEntities.LACED_SNOWBALL.get(), shooter, level);
    }

    public LacedSnowball(Level level, double x, double y, double z) {
        super(ModEntities.LACED_SNOWBALL.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.LACED_SNOWBALL.get();
    }

    private int lacing() {
        return Math.max(1, Lacing.level(getItem()));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id != 3) {
            super.handleEntityEvent(id);
            return;
        }
        var violet = new DustParticleOptions(lacing() >= 2 ? new Vector3f(0.45f, 0.22f, 0.72f) : new Vector3f(0.72f, 0.56f, 0.95f), 1f);
        for (int i = 0; i < 8; i++) {
            level().addParticle(i % 2 == 0 ? violet : new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(),
                    (random.nextDouble() - 0.5) * 0.08, random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.08);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide && result.getEntity() instanceof LivingEntity target) {
            target.hurt(damageSources().thrown(this, getOwner()), 0f);
            Lacing.poison(target, lacing(), DOSE);
            Collapse.fromLacedHit(target, this);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, (byte) 3);
            discard();
        }
    }
}
