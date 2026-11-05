package com.seba.reverie.devtools.test;

import com.seba.reverie.devtools.ReverieDevTools;
import com.seba.reverie.dream.DreamManager;

import net.minecraft.gametest.framework.GameTestHelper;

final class DreamTestSupport {
    private DreamTestSupport() {
    }

    /**
     * The headless GameTestServer bakes an empty dimension registry, so datapack dimensions (Dreamland) do not
     * exist there. These tests run for real with /test runall on a dedicated server (tools/devscripts/server_tests.txt).
     */
    static boolean skipWithoutDreamland(GameTestHelper h) {
        if (h.getLevel().getServer().getLevel(DreamManager.DREAMLAND) != null) return false;
        ReverieDevTools.log("skipped (no Dreamland in the gametest server): run /test runall on a dedicated server");
        h.succeed();
        return true;
    }
}
