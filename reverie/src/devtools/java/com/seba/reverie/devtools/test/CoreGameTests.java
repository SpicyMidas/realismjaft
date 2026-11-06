package com.seba.reverie.devtools.test;

import java.util.List;

import com.seba.reverie.compat.create.gearshift.GateLayout;
import com.seba.reverie.compat.create.gearshift.GearstickAnimator;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Pure-logic checks that need no optional mod (GateLayout has no Create imports). */
@PrefixGameTestTemplate(false)
public final class CoreGameTests {
    private CoreGameTests() {
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void gatePathsFollowTheRail(GameTestHelper h) {
        for (GateLayout layout : new GateLayout[]{GateLayout.REGULAR, GateLayout.SPORT}) {
            for (int from : layout.gears()) {
                for (int to : layout.gears()) {
                    List<float[]> path = layout.path(layout.slotX(from), layout.slotY(from), to);
                    float[] end = path.get(path.size() - 1);
                    h.assertTrue(Math.abs(end[0] - layout.slotX(to)) < 1e-4 && Math.abs(end[1] - layout.slotY(to)) < 1e-4,
                            "path " + from + "->" + to + " does not end in the slot");
                    for (int i = 1; i < path.size(); i++) {
                        float[] a = path.get(i - 1);
                        float[] b = path.get(i);
                        boolean horizontal = Math.abs(a[1] - b[1]) < 1e-4;
                        boolean vertical = Math.abs(a[0] - b[0]) < 1e-4;
                        h.assertTrue(horizontal || vertical, "diagonal segment in " + from + "->" + to);
                        if (horizontal && Math.abs(a[0] - b[0]) > 1e-4) {
                            h.assertTrue(Math.abs(a[1]) < 1e-4, "horizontal move off the rail in " + from + "->" + to);
                        }
                    }
                    h.assertTrue(layout.gearAt(end[0], end[1]) == to, "gearAt mismatch for " + to);
                }
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void gateDragCannotCutCorners(GameTestHelper h) {
        GateLayout layout = GateLayout.SPORT;
        float[] pos = {layout.slotX(1), layout.slotY(1)};
        // Pull straight toward gear 6 (bottom right): must travel down to the rail, along it, then down.
        boolean wall = layout.drag(pos, layout.slotX(6), layout.slotY(6));
        h.assertTrue(layout.gearAt(pos[0], pos[1]) == 6, "drag 1->6 ended at " + pos[0] + "," + pos[1]);
        h.assertFalse(wall, "unexpected wall after reaching the target");
        // Cutting the corner diagonally (from 1 straight toward just past 4) still follows the gate into 4.
        float[] diag = {GateLayout.REGULAR.slotX(1), GateLayout.REGULAR.slotY(1)};
        GateLayout.REGULAR.drag(diag, 1.12f, 0.97f);
        h.assertTrue(GateLayout.REGULAR.gearAt(diag[0], diag[1]) == 4, "diagonal drag 1->4 ended at " + diag[0] + "," + diag[1]);
        // A refused (too fast) gear keeps the knob short of its slot end.
        float[] held = {GateLayout.REGULAR.slotX(1), GateLayout.REGULAR.slotY(1)};
        GateLayout.REGULAR.drag(held, 1f, 1f, gear -> gear == 4);
        h.assertTrue(GateLayout.REGULAR.gearAt(held[0], held[1]) == -1 && held[1] > 0.5f, "refused gear 4 was reached");
        // R has no bottom slot: pushing down from R is a wall.
        pos[0] = layout.slotX(GateLayout.R);
        pos[1] = layout.slotY(GateLayout.R);
        layout.drag(pos, layout.slotX(GateLayout.R), 0);
        boolean blocked = layout.drag(pos, layout.slotX(GateLayout.R), 1f);
        h.assertTrue(blocked && pos[1] <= 1e-3, "R column must not have a bottom slot");
        h.succeed();
    }

    @GameTest(templateNamespace = "reverie", template = "empty")
    public static void animatorArrivesAndSettles(GameTestHelper h) {
        GearstickAnimator anim = new GearstickAnimator(GateLayout.REGULAR, GateLayout.N);
        anim.moveTo(4, false);
        for (int i = 0; i < 20; i++) anim.tick();
        h.assertTrue(!anim.moving() && Math.abs(anim.x(1) - 1) < 1e-3 && Math.abs(anim.y(1) - 1) < 1e-3, "did not settle in gear 4");
        anim.moveTo(GateLayout.R, true);
        for (int i = 0; i < 3; i++) anim.tick();
        h.assertTrue(!anim.moving(), "forced R must arrive in 3 ticks");
        h.succeed();
    }
}
