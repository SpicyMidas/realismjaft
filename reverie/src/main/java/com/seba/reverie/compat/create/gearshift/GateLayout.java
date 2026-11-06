package com.seba.reverie.compat.create.gearshift;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The H-pattern gate, shared by the GUI and the block renderer so they can never disagree.
 * <p>
 * Gate space: x is the column index (0 = gears 1/2, 1 = gears 3/4, ..., last = R), y is -1 for the top row
 * (odd gears and R), 0 for the neutral rail and +1 for the bottom row (even gears). N rests on the rail in the
 * middle. The knob may only move along the rail or up/down a column, never diagonally.
 * Pure math: no Minecraft or Create classes.
 */
public final class GateLayout {
    public static final int N = 0;
    public static final int R = 7;
    public static final GateLayout REGULAR = new GateLayout(4);
    public static final GateLayout SPORT = new GateLayout(6);

    private static final float EPS = 1e-4f;
    /** How far into its slot the knob gets for a gear the screen refuses (too fast). */
    private static final float BLOCKED_REACH = 0.7f;

    public final int forwardGears;
    public final int columns;
    public final float restX;

    private GateLayout(int forwardGears) {
        this.forwardGears = forwardGears;
        this.columns = forwardGears / 2 + 1;
        this.restX = (columns - 1) / 2f;
    }

    public static GateLayout of(int forwardGears) {
        return forwardGears > 4 ? SPORT : REGULAR;
    }

    public boolean isValid(int gear) {
        return gear == N || gear == R || gear >= 1 && gear <= forwardGears;
    }

    /** Gears in display order for keyboard shortcuts and labels. */
    public int[] gears() {
        int[] g = new int[forwardGears + 2];
        g[0] = R;
        g[1] = N;
        for (int i = 1; i <= forwardGears; i++) g[i + 1] = i;
        return g;
    }

    public float slotX(int gear) {
        if (gear == N) return restX;
        if (gear == R) return columns - 1;
        return (gear - 1) / 2;
    }

    public float slotY(int gear) {
        if (gear == N) return 0;
        if (gear == R) return -1;
        return gear % 2 == 1 ? -1 : 1;
    }

    /** True if column {@code c} has a slot in direction {@code dir} (-1 up, +1 down). */
    public boolean hasSlot(int column, int dir) {
        if (column < 0 || column >= columns) return false;
        if (column == columns - 1) return dir < 0; // R: top only
        int gear = column * 2 + (dir < 0 ? 1 : 2);
        return gear <= forwardGears;
    }

    public int gearAtSlot(int column, int dir) {
        if (!hasSlot(column, dir)) return -1;
        if (column == columns - 1) return R;
        return column * 2 + (dir < 0 ? 1 : 2);
    }

    /** Gear whose slot end the point sits in, N on the rail, -1 in between. */
    public int gearAt(float x, float y) {
        if (Math.abs(y) < 0.2f) return N;
        if (Math.abs(y) < 0.86f) return -1;
        int column = Math.round(x);
        if (Math.abs(x - column) > 0.05f) return -1;
        return gearAtSlot(column, y < 0 ? -1 : 1);
    }

    public static String label(int gear) {
        if (gear == N) return "N";
        if (gear == R) return "R";
        return Integer.toString(gear);
    }

    /** Polyline from (x, y) to the slot of {@code gear} that follows the gate: out of the column, along the rail, in. */
    public List<float[]> path(float x, float y, int gear) {
        float tx = slotX(gear);
        float ty = slotY(gear);
        List<float[]> pts = new ArrayList<>();
        pts.add(new float[]{x, y});
        boolean inColumn = Math.abs(y) > EPS;
        boolean sameColumn = Math.abs(x - tx) < EPS;
        if (inColumn && sameColumn && (ty == 0 || Math.signum(ty) == Math.signum(y))) {
            pts.add(new float[]{tx, ty});
            return pts;
        }
        if (inColumn) pts.add(new float[]{x, 0});
        if (Math.abs(x - tx) > EPS) pts.add(new float[]{tx, 0});
        if (Math.abs(ty) > EPS) pts.add(new float[]{tx, ty});
        if (pts.size() == 1) pts.add(new float[]{tx, ty});
        return pts;
    }

    public static float length(List<float[]> pts) {
        float len = 0;
        for (int i = 1; i < pts.size(); i++) {
            len += Math.abs(pts.get(i)[0] - pts.get(i - 1)[0]) + Math.abs(pts.get(i)[1] - pts.get(i - 1)[1]);
        }
        return len;
    }

    /** Point at arc length {@code s} along an axis-aligned polyline. */
    public static float[] along(List<float[]> pts, float s) {
        for (int i = 1; i < pts.size(); i++) {
            float[] a = pts.get(i - 1);
            float[] b = pts.get(i);
            float seg = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
            if (s <= seg || i == pts.size() - 1) {
                float t = seg < EPS ? 1 : Math.min(1, Math.max(0, s / seg));
                return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
            }
            s -= seg;
        }
        float[] last = pts.get(pts.size() - 1);
        return new float[]{last[0], last[1]};
    }

    /**
     * Moves the knob from {@code pos} to the point of the gate nearest the pointer at (mx, my), routed along the gate
     * (out of the column, along the rail, into the next one), so cutting a corner diagonally simply follows the mouse.
     * The screen draws the knob gliding along that route. Slots of gears in {@code blocked} end short of their gear.
     * Returns true if the knob is pressed against a wall (pointer far from the gate).
     */
    public boolean drag(float[] pos, float mx, float my, IntPredicate blocked) {
        float bestX = clamp(mx, 0, columns - 1);
        float bestY = 0;
        float bestScore = score(pos, bestX, 0, mx, my);
        for (int c = 0; c < columns; c++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                if (!hasSlot(c, dir)) continue;
                float reach = blocked.test(gearAtSlot(c, dir)) ? BLOCKED_REACH : 1f;
                float y = dir < 0 ? clamp(my, -reach, 0) : clamp(my, 0, reach);
                float score = score(pos, c, y, mx, my);
                if (score < bestScore - EPS) {
                    bestScore = score;
                    bestX = c;
                    bestY = y;
                }
            }
        }
        pos[0] = bestX;
        pos[1] = Math.abs(bestY) < EPS ? 0 : bestY;
        float dx = mx - pos[0];
        float dy = my - pos[1];
        return dx * dx + dy * dy > 0.45f * 0.45f;
    }

    public boolean drag(float[] pos, float mx, float my) {
        return drag(pos, mx, my, gear -> false);
    }

    /** Distance from the pointer, with a little of the route length so near-ties keep the shorter move. */
    private float score(float[] pos, float x, float y, float mx, float my) {
        float d = (float) Math.sqrt((mx - x) * (mx - x) + (my - y) * (my - y));
        return d + 0.1f * length(path(pos[0], pos[1], x, y));
    }

    /** Route between two points on the gate. */
    private static List<float[]> path(float x, float y, float tx, float ty) {
        List<float[]> pts = new ArrayList<>();
        pts.add(new float[]{x, y});
        boolean sameColumn = Math.abs(x - tx) < EPS;
        if (!sameColumn || (Math.abs(y) > EPS && Math.abs(ty) > EPS && Math.signum(ty) != Math.signum(y))) {
            if (Math.abs(y) > EPS) pts.add(new float[]{x, 0});
            if (Math.abs(ty) > EPS) pts.add(new float[]{tx, 0});
        }
        pts.add(new float[]{tx, ty});
        return pts;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
