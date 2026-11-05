package com.seba.reverie.devtools;

import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Indirection so test classes for optional mods are only loaded when those mods are. */
final class TestRegistrar {
    private TestRegistrar() {
    }

    static void create(RegisterGameTestsEvent event) {
        event.register(com.seba.reverie.devtools.test.CreateGameTests.class);
        event.register(com.seba.reverie.devtools.test.RemoteGameTests.class);
    }

    static void farmersDelight(RegisterGameTestsEvent event) {
        event.register(com.seba.reverie.devtools.test.FdGameTests.class);
    }
}
