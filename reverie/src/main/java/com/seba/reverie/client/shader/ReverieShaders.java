package com.seba.reverie.client.shader;

import java.io.IOException;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.seba.reverie.Reverie;

import net.minecraft.Util;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * Reverie's core shaders ({@code assets/reverie/shaders/core/}). A shader that fails to load is logged and left null,
 * and while a shader pack (Iris / Oculus, detected by reflection) draws the world they are not used either: callers
 * then draw their plain vertex-colour fallback.
 */
public final class ReverieShaders {
    @Nullable
    private static ShaderInstance aurora;

    private static int irisState; // 0 unresolved, 1 api found, 2 no shader mod, 3 api broken
    private static Object irisApi;
    private static Method shaderPackInUse;
    private static long nextCheck = Long.MIN_VALUE;
    private static boolean packActive;

    private ReverieShaders() {
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Reverie.id("aurora"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> aurora = s);
        } catch (IOException | RuntimeException e) {
            aurora = null;
            Reverie.LOGGER.warn("Reverie aurora shader unavailable, using the plain aurora: {}", e.toString());
        }
    }

    /** The aurora shader, or null when it is missing or a shader pack is in use. */
    @Nullable
    public static ShaderInstance aurora() {
        return aurora != null && !shaderPackActive() ? aurora : null;
    }

    /** True while an Iris/Oculus shader pack renders the world (asked at most once a second). */
    public static boolean shaderPackActive() {
        long now = Util.getMillis();
        if (now < nextCheck) return packActive;
        nextCheck = now + 1000L;
        if (irisState == 0) {
            if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) {
                irisState = 2;
            } else {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = api.getMethod("getInstance").invoke(null);
                    shaderPackInUse = api.getMethod("isShaderPackInUse");
                    irisState = 1;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    irisState = 3;
                }
            }
        }
        if (irisState == 1) {
            try {
                packActive = Boolean.TRUE.equals(shaderPackInUse.invoke(irisApi));
            } catch (ReflectiveOperationException | RuntimeException e) {
                irisState = 3;
            }
        }
        if (irisState == 3) packActive = true; // a shader mod we cannot ask: the plain look is always safe
        return packActive;
    }
}
