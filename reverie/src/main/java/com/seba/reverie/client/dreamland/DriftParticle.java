package com.seba.reverie.client.dreamland;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Petals, pages and raindrops that keep their spawn velocity (up, down or sideways) with a little flutter. */
public class DriftParticle extends TextureSheetParticle {
    public enum Kind { PETAL, PAGE, RAINDROP, Z, SNOW, BUBBLE }

    private final Kind kind;
    private final float spin;
    private final float phase;

    protected DriftParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0;
        this.friction = 1;
        this.hasPhysics = false;
        this.lifetime = switch (kind) {
            case PETAL -> 90 + random.nextInt(70);
            case PAGE -> 120 + random.nextInt(80);
            case RAINDROP -> 18 + random.nextInt(14);
            case Z -> 40 + random.nextInt(20);
            case SNOW -> 110 + random.nextInt(80);
            case BUBBLE -> 40 + random.nextInt(40);
        };
        this.quadSize = switch (kind) {
            case PETAL -> 0.16f + random.nextFloat() * 0.08f;
            case PAGE -> 0.22f + random.nextFloat() * 0.08f;
            case RAINDROP -> 0.14f;
            case Z -> 0.12f + random.nextFloat() * 0.04f;
            case SNOW -> 0.05f + random.nextFloat() * 0.05f;
            case BUBBLE -> 0.06f + random.nextFloat() * 0.08f;
        };
        this.spin = kind == Kind.RAINDROP || kind == Kind.Z || kind == Kind.BUBBLE ? 0 : (random.nextFloat() - 0.5f) * 0.2f;
        if (kind == Kind.Z) this.roll = 0;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.alpha = 0;
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        if (kind != Kind.RAINDROP) {
            // Flutter: a slow sideways sway.
            float s = Mth.sin(age * 0.12f + phase) * 0.012f;
            x += s;
            z += Mth.cos(age * 0.1f + phase) * 0.01f;
        }
        float in = Math.min(1, age / 8f);
        float out = Math.min(1, (lifetime - age) / 12f);
        alpha = Math.min(in, out) * (kind == Kind.RAINDROP ? 0.7f : kind == Kind.BUBBLE ? 0.8f : 0.95f);
    }

    @Override
    protected int getLightColor(float partialTick) {
        // Snow catches the aurora light; bubbles shine like the reef.
        return kind == Kind.SNOW || kind == Kind.BUBBLE ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Kind kind) {
        return (type, level, x, y, z, xd, yd, zd) -> {
            DriftParticle p = new DriftParticle(level, x, y, z, xd, yd, zd, kind);
            p.pickSprite(sprites);
            return p;
        };
    }
}
