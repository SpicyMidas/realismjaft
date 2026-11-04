package com.seba.reverie.devtools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev-only automation: runs a small line script (-Dreverie.dev.script=file) so headless test runs can
 * execute commands, take screenshots and stop by themselves. Lines:
 * <pre>
 *   wait N            pause N ticks
 *   cmd COMMAND       server: console command / client: command as the player (op via integrated server)
 *   say TEXT          log a marker line ("[devscript] TEXT")
 *   screenshot NAME   client only
 *   ...               client-only verbs are handled by ClientScript
 *   stop              end the run
 * </pre>
 */
@Mod("reveriedev")
public final class ReverieDevTools {
    static final Logger LOG = LogUtils.getLogger();

    public ReverieDevTools(IEventBus modBus) {
        modBus.addListener(ReverieDevTools::registerTests);
        List<String> script = loadScript();
        if (script == null) return;
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientScript.start(script);
        } else {
            ServerScript runner = new ServerScript(script);
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> runner.server = e.getServer());
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> runner.tick());
        }
    }

    private static void registerTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        event.register(com.seba.reverie.devtools.test.CoreGameTests.class);
        event.register(com.seba.reverie.devtools.test.MelatoniaGameTests.class);
        // Optional-mod tests reference those mods' classes, so they are only registered when present.
        if (com.seba.reverie.compat.Compat.create()) TestRegistrar.create(event);
    }

    static List<String> loadScript() {
        String file = System.getProperty("reverie.dev.script");
        if (file == null || file.isBlank()) return null;
        try {
            List<String> lines = Files.readAllLines(Path.of(file));
            LOG.info("[devscript] loaded {} lines from {}", lines.size(), file);
            return lines;
        } catch (IOException e) {
            LOG.error("[devscript] cannot read {}", file, e);
            return null;
        }
    }

    static final class ServerScript {
        private final Deque<String> lines;
        MinecraftServer server;
        private int wait = 40;

        ServerScript(List<String> lines) {
            this.lines = new ArrayDeque<>(lines);
        }

        void tick() {
            if (server == null) return;
            if (wait > 0) {
                wait--;
                return;
            }
            while (!lines.isEmpty() && wait == 0) {
                String line = lines.poll().trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String verb = line.contains(" ") ? line.substring(0, line.indexOf(' ')) : line;
                String rest = line.contains(" ") ? line.substring(line.indexOf(' ') + 1) : "";
                switch (verb) {
                    case "wait" -> wait = Integer.parseInt(rest.trim());
                    case "cmd" -> {
                        LOG.info("[devscript] /{}", rest);
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), rest);
                    }
                    case "say" -> LOG.info("[devscript] {}", rest);
                    case "stop" -> {
                        LOG.info("[devscript] stopping server");
                        server.halt(false);
                        return;
                    }
                    default -> LOG.warn("[devscript] unknown server verb: {}", line);
                }
            }
        }
    }
}
