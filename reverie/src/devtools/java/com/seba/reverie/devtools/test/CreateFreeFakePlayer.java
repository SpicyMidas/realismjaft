package com.seba.reverie.devtools.test;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** A fake player that is never in the player list (no login events). */
final class CreateFreeFakePlayer {
    private CreateFreeFakePlayer() {
    }

    static ServerPlayer get(GameTestHelper h) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.fromString("5e1f5a1e-0000-4000-8000-0000000000f0"), "reverie_core_test"));
    }
}
