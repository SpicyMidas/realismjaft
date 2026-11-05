package com.seba.reverie.devtools;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client half of the dev script. Extra verbs:
 * newworld NAME flat|normal SEED, openworld NAME, look YAW PITCH, use X Y Z [face],
 * key CODE, mouse down|drag|up X Y (GUI-scaled), close, hidegui on|off, gui SCALE.
 */
final class ClientScript {
    private final Deque<String> lines;
    private int wait = 20;
    private boolean waitingForWorld;

    private ClientScript(List<String> lines) {
        this.lines = new ArrayDeque<>(lines);
    }

    static void start(List<String> script) {
        ClientScript s = new ClientScript(script);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> s.tick());
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if ((waitingForWorld || lines.peek() != null && lines.peek().startsWith("openworld")) && ++idleTicks % 100 == 0) {
            ReverieDevTools.LOG.info("[devscript] waiting; screen={} player={}", mc.screen == null ? "none" : mc.screen.getClass().getName(), mc.player != null);
        }
        if (waitingForWorld && mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup) {
            // One-time "experimental settings" prompt for worlds with datapack dimensions: join without a backup.
            for (var child : backup.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button b
                        && b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("selectWorld.backupJoinSkipButton")) {
                    ReverieDevTools.LOG.info("[devscript] confirming the experimental-settings prompt");
                    b.onPress();
                    break;
                }
            }
        }
        if (waitingForWorld) {
            if (mc.player == null || mc.level == null || mc.screen != null || mc.getOverlay() != null) return;
            waitingForWorld = false;
            wait = 60;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        while (!lines.isEmpty() && wait == 0 && !waitingForWorld) {
            String line = lines.poll().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] a = line.split("\\s+");
            String rest = line.contains(" ") ? line.substring(line.indexOf(' ') + 1) : "";
            try {
                run(mc, a, rest);
            } catch (Exception ex) {
                ReverieDevTools.LOG.error("[devscript] failed: {}", line, ex);
            }
        }
    }

    private void run(Minecraft mc, String[] a, String rest) {
        switch (a[0]) {
            case "wait" -> wait = Integer.parseInt(a[1]);
            case "say" -> ReverieDevTools.LOG.info("[devscript] {}", rest);
            case "newworld" -> {
                if (!(mc.screen instanceof TitleScreen)) {
                    lines.addFirst("newworld " + rest);
                    wait = 20;
                    return;
                }
                if (mc.getLevelSource().levelExists(a[1])) {
                    try (var access = mc.getLevelSource().createAccess(a[1])) {
                        access.deleteLevel();
                    } catch (java.io.IOException ex) {
                        ReverieDevTools.LOG.error("[devscript] cannot delete old world {}", a[1], ex);
                    }
                }
                boolean flat = a.length > 2 && a[2].equals("flat");
                long seed = a.length > 3 ? Long.parseLong(a[3]) : 12345L;
                LevelSettings settings = new LevelSettings(a[1], GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(a[1], settings, new WorldOptions(seed, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(flat ? WorldPresets.FLAT : WorldPresets.NORMAL).value().createWorldDimensions(),
                        mc.screen);
                waitingForWorld = true;
            }
            case "openworld" -> {
                if (!(mc.screen instanceof TitleScreen)) {
                    lines.addFirst("openworld " + rest);
                    wait = 20;
                    return;
                }
                mc.createWorldOpenFlows().openWorld(a[1], () -> ReverieDevTools.LOG.error("[devscript] cannot open {}", a[1]));
                waitingForWorld = true;
            }
            case "cmd" -> {
                var server = mc.getSingleplayerServer();
                if (server != null && mc.player != null) {
                    var uuid = mc.player.getUUID();
                    server.execute(() -> {
                        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                        if (sp != null) {
                            ReverieDevTools.LOG.info("[devscript] /{}", rest);
                            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), rest);
                        }
                    });
                } else if (mc.player != null) {
                    mc.player.connection.sendCommand(rest);
                }
            }
            case "screenshot" -> {
                Screenshot.grab(mc.gameDirectory, a[1] + ".png", mc.getMainRenderTarget(),
                        msg -> ReverieDevTools.LOG.info("[devscript] screenshot {}: {}", a[1], msg.getString()));
            }
            case "look" -> {
                if (mc.player != null) {
                    mc.player.setYRot(Float.parseFloat(a[1]));
                    mc.player.setXRot(Float.parseFloat(a[2]));
                    mc.player.yRotO = mc.player.getYRot();
                    mc.player.xRotO = mc.player.getXRot();
                }
            }
            case "use" -> {
                BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                Direction face = a.length > 4 ? Direction.byName(a[4]) : Direction.UP;
                if (face == null) face = Direction.UP;
                if (mc.gameMode != null && mc.player != null) {
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                }
            }
            case "useitem" -> {
                if (mc.gameMode != null && mc.player != null) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            case "key" -> {
                Screen screen = mc.screen;
                int code = Integer.parseInt(a[1]);
                if (screen != null) screen.keyPressed(code, 0, 0);
            }
            case "char" -> {
                Screen screen = mc.screen;
                if (screen != null) screen.charTyped(a[1].charAt(0), 0);
            }
            case "mouse" -> {
                Screen screen = mc.screen;
                if (screen == null) return;
                double x = Double.parseDouble(a[2]);
                double y = Double.parseDouble(a[3]);
                switch (a[1]) {
                    case "down" -> {
                        screen.mouseClicked(x, y, 0);
                        lastX = x;
                        lastY = y;
                    }
                    case "drag" -> {
                        screen.mouseDragged(x, y, 0, x - lastX, y - lastY);
                        screen.mouseMoved(x, y);
                        lastX = x;
                        lastY = y;
                    }
                    case "up" -> screen.mouseReleased(x, y, 0);
                    default -> { }
                }
            }
            case "close" -> mc.setScreen(null);
            case "tpbiome", "tpshape" -> {
                var server = mc.getSingleplayerServer();
                if (server == null || mc.player == null) return;
                var uuid = mc.player.getUUID();
                double back = a.length > 2 ? Double.parseDouble(a[2]) : 30;
                double up = a.length > 3 ? Double.parseDouble(a[3]) : 14;
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                    var dream = server.getLevel(com.seba.reverie.dream.DreamManager.DREAMLAND);
                    if (sp == null || dream == null) return;
                    BlockPos target = null;
                    if (a[0].equals("tpbiome")) {
                        var key = net.minecraft.resources.ResourceKey.create(Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(a[1]));
                        var found = dream.findClosestBiome3d(h -> h.is(key), sp.level() == dream ? sp.blockPosition() : BlockPos.ZERO.atY(120), 3000, 32, 32);
                        if (found != null) target = found.getFirst();
                    } else {
                        var field = com.seba.reverie.dreamland.IslandSearch.field(dream);
                        com.seba.reverie.dreamland.IslandField.Shape shape = null;
                        com.seba.reverie.dreamland.IslandField.Kind kind = null;
                        try {
                            shape = com.seba.reverie.dreamland.IslandField.Shape.valueOf(a[1]);
                        } catch (IllegalArgumentException notShape) {
                            kind = com.seba.reverie.dreamland.IslandField.Kind.valueOf(a[1]);
                        }
                        BlockPos from = sp.level() == dream ? sp.blockPosition() : BlockPos.ZERO;
                        double best = Double.MAX_VALUE;
                        for (int layer = 0; layer < (kind == com.seba.reverie.dreamland.IslandField.Kind.CLOUDREACH ? 4 : 3); layer++)
                            for (int cx = -25; cx <= 25; cx++)
                                for (int cz = -25; cz <= 25; cz++) {
                                    var is = field.island(layer, (from.getX() >> 0) / 96 + cx, (from.getZ() >> 0) / 96 + cz);
                                    if (is == null || shape != null && is.shape != shape || kind != null && (is.kind != kind || is.radius < 22)) continue;
                                    double d = Math.hypot(is.cx - from.getX(), is.cz - from.getZ());
                                    if (d < best) {
                                        best = d;
                                        target = new BlockPos(is.cx, is.cy, is.cz);
                                    }
                                }
                    }
                    if (target == null) {
                        ReverieDevTools.LOG.warn("[devscript] nothing found for {}", rest);
                        return;
                    }
                    double x = target.getX() - back;
                    double z = target.getZ() - back;
                    double y = target.getY() + up;
                    float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - x), target.getZ() - z));
                    float pitch = (float) Math.toDegrees(Math.atan2(up, Math.hypot(back, back)));
                    ReverieDevTools.LOG.info("[devscript] {} -> {}", rest, target.toShortString());
                    sp.teleportTo(dream, x, y, z, yaw, pitch);
                });
            }
            case "save" -> {
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    server.saveEverything(true, true, true);
                    ReverieDevTools.LOG.info("[devscript] world saved");
                });
            }
            case "crash" -> {
                // Simulates killing the game process: no save, no shutdown hooks.
                ReverieDevTools.LOG.info("[devscript] halting the JVM without saving");
                Runtime.getRuntime().halt(3);
            }
            case "loginv" -> {
                var server = mc.getSingleplayerServer();
                if (server != null && mc.player != null) {
                    var uuid = mc.player.getUUID();
                    server.execute(() -> {
                        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                        if (sp == null) return;
                        StringBuilder b = new StringBuilder();
                        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                            var st = sp.getInventory().getItem(i);
                            if (!st.isEmpty()) b.append(i).append('=').append(st.getCount()).append('x')
                                    .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()).append(' ');
                        }
                        ReverieDevTools.LOG.info("[devscript] inventory[{}] dim={} pos={} : {}", rest, sp.level().dimension().location(),
                                sp.blockPosition().toShortString(), b);
                    });
                }
            }
            case "logheld" -> {
                var stack = mc.player.getMainHandItem();
                var fn = net.minecraft.client.renderer.item.ItemProperties.getProperty(stack, net.minecraft.resources.ResourceLocation.parse(a[1]));
                ReverieDevTools.LOG.info("[devscript] held {} components={} property {}={}", stack, stack.getComponents(), a[1],
                        fn == null ? "unregistered" : fn.call(stack, mc.level, mc.player, 0));
            }
            case "sneak" -> mc.options.keyShift.setDown(a[1].equals("on"));
            case "hidegui" -> mc.options.hideGui = a[1].equals("on");
            case "gui" -> {
                mc.options.guiScale().set(Integer.parseInt(a[1]));
                mc.resizeDisplay();
            }
            case "stop" -> {
                ReverieDevTools.LOG.info("[devscript] stopping client");
                mc.stop();
            }
            default -> ReverieDevTools.LOG.warn("[devscript] unknown client verb: {}", a[0]);
        }
    }

    private int idleTicks;
    private double lastX;
    private double lastY;
}
