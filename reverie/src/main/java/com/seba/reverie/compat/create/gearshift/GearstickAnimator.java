package com.seba.reverie.compat.create.gearshift;

import java.util.List;

/**
 * Cosmetic stick motion in gate space, driven from the synced effective gear. Used by the block renderer
 * (and by the GUI for its spring-back), never for logic. Pure math; sounds are reported through {@link Listener}.
 */
public final class GearstickAnimator {
    public enum Cue { RAIL_TICK, COMMIT, REVERSE_SNAP, NEUTRAL_CLICK, REFUSE }

    public interface Listener {
        void cue(Cue cue, int gear);
    }

    private final GateLayout layout;
    private Listener listener = (c, g) -> { };

    private float x;
    private float y;
    private float prevX;
    private float prevY;

    private List<float[]> path;
    private float pathLength;
    private int moveTick;
    private int moveDuration;
    private int targetGear;
    private boolean forced;
    private float lastRailCue;

    private int settleTicks;
    private int vibrateTicks;
    private int refuseTicks;
    private int shown = -1;

    public GearstickAnimator(GateLayout layout, int gear) {
        this.layout = layout;
        snapTo(gear);
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? (c, g) -> { } : listener;
    }

    public int targetGear() {
        return targetGear;
    }

    public boolean moving() {
        return path != null;
    }

    public void snapTo(int gear) {
        if (!layout.isValid(gear)) gear = GateLayout.N;
        x = prevX = layout.slotX(gear);
        y = prevY = layout.slotY(gear);
        path = null;
        targetGear = gear;
        shown = gear;
    }

    /** Put the knob at an arbitrary gate point (e.g. where the GUI drag let go) with no committed slot. */
    public void place(float px, float py) {
        x = prevX = px;
        y = prevY = py;
        path = null;
        settleTicks = 0;
        shown = -1;
    }

    /** Start travelling along the gate to {@code gear}. {@code forced} = redstone snap to R. */
    public void moveTo(int gear, boolean forced) {
        if (!layout.isValid(gear)) gear = GateLayout.N;
        if (gear == targetGear && (path != null || shown == gear)) return;
        targetGear = gear;
        this.forced = forced;
        path = layout.path(x, y, gear);
        pathLength = GateLayout.length(path);
        moveTick = 0;
        moveDuration = forced ? 3 : Math.max(8, Math.min(12, Math.round(6 + pathLength * 2)));
        lastRailCue = 0;
        settleTicks = 0;
    }

    public void refuse() {
        refuseTicks = 6;
        listener.cue(Cue.REFUSE, targetGear);
    }

    public void tick() {
        prevX = x;
        prevY = y;
        if (refuseTicks > 0) refuseTicks--;
        if (vibrateTicks > 0) vibrateTicks--;
        if (path != null) {
            moveTick++;
            float t = Math.min(1f, moveTick / (float) moveDuration);
            float eased = forced ? 1 - (1 - t) * (1 - t) * (1 - t) : easeInOut(t);
            float s = eased * pathLength;
            float[] p = GateLayout.along(path, s);
            boolean onRail = Math.abs(p[1]) < 0.02f;
            if (onRail && !forced && Math.abs(s - lastRailCue) > 0.45f) {
                lastRailCue = s;
                listener.cue(Cue.RAIL_TICK, targetGear);
            }
            x = p[0];
            y = p[1];
            if (t >= 1f) {
                path = null;
                shown = targetGear;
                settleTicks = 2;
                if (forced) vibrateTicks = 4;
                listener.cue(targetGear == GateLayout.R ? (forced ? Cue.REVERSE_SNAP : Cue.COMMIT)
                        : targetGear == GateLayout.N ? Cue.NEUTRAL_CLICK : Cue.COMMIT, targetGear);
            }
        } else if (settleTicks > 0) {
            settleTicks--;
            // Tiny overshoot past the slot end, then back: the "clunk".
            float over = settleTicks == 1 ? 0.07f : 0f;
            float sx = layout.slotX(targetGear);
            float sy = layout.slotY(targetGear);
            x = sx;
            y = sy + Math.signum(sy) * over;
            if (sy == 0) x = sx + (settleTicks == 1 ? 0.04f : 0f);
        }
    }

    public float x(float partial) {
        return prevX + (x - prevX) * partial;
    }

    public float y(float partial) {
        return prevY + (y - prevY) * partial;
    }

    /** Extra angular shake in degrees (redstone snap vibration or refusal), signed, time-based. */
    public float shake(float time) {
        float amp = 0;
        if (vibrateTicks > 0) amp += 2.5f * vibrateTicks / 4f;
        if (refuseTicks > 0) amp += 3.5f * refuseTicks / 6f;
        return amp == 0 ? 0 : amp * (float) Math.sin(time * 2.6f);
    }

    private static float easeInOut(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }
}
