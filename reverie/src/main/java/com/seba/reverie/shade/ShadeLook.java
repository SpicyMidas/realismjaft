package com.seba.reverie.shade;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Where the dreamer is looking. The client installs a {@link Viewer} (its camera); the server uses player rotation. */
public final class ShadeLook {
    public interface Viewer {
        /** Camera position, or null when there is no camera yet. */
        Vec3 eye();

        Vec3 look();
    }

    public static volatile Viewer viewer;

    private ShadeLook() {
    }

    /** Roughly on the local player's screen (Everyone Turns flips before you can see the face). */
    public static boolean inView(ShadeEntity shade) {
        Viewer v = viewer;
        if (v == null || v.eye() == null) return false;
        Vec3 to = headOf(shade).subtract(v.eye());
        return to.lengthSqr() > 1e-4 && v.look().dot(to.normalize()) > 0.5;
    }

    /** The crosshair passes within a couple of blocks of the shade's head: the pinprick eyes show. */
    public static boolean staredAt(ShadeEntity shade) {
        Viewer v = viewer;
        if (v == null || v.eye() == null) return false;
        Vec3 to = headOf(shade).subtract(v.eye());
        double along = to.dot(v.look());
        if (along <= 0) return false;
        double off = to.subtract(v.look().scale(along)).length();
        return off < Math.max(1.5, along * 0.04);
    }

    public static Vec3 headOf(ShadeEntity shade) {
        return shade.position().add(0, shade.variant().headHeight, 0);
    }

    /** Server-side check for a real player. */
    public static boolean looksAt(Entity viewer, Vec3 point, double cosine) {
        Vec3 look = viewer.getViewVector(1f);
        Vec3 to = point.subtract(viewer.getEyePosition()).normalize();
        return look.dot(to) > cosine;
    }
}
