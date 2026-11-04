package dev.agentbuddy.client;

import com.google.gson.JsonObject;
import dev.agentbuddy.network.AgentScreenData;
import dev.agentbuddy.network.ToolRequest;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Agent GUI (AGENT_SPEC.md section 6). Every button sends a {@link ToolRequest}; the server runs the tool
 * and answers with fresh {@link AgentScreenData}. Waystones, ignore list and container search are added in M4/M5.
 */
public class AgentScreen extends Screen {
    private static final int WIDTH = 200;
    private AgentScreenData data;
    private EditBox nameBox;

    public AgentScreen(AgentScreenData data) {
        super(Component.literal(data.name()));
        this.data = data;
    }

    public UUID agentId() {
        return data.agentId();
    }

    public void update(AgentScreenData fresh) {
        this.data = fresh;
        if (nameBox != null && !nameBox.isFocused()) nameBox.setValue(fresh.name());
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = height / 2 - 50;
        nameBox = new EditBox(font, x, y, WIDTH - 64, 20, Component.literal("Name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(data.name());
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> {
            JsonObject args = new JsonObject();
            args.addProperty("name", nameBox.getValue());
            send("set_name", args);
        }).bounds(x + WIDTH - 60, y, 60, 20).build());

        int half = (WIDTH - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Follow"), b -> send("follow", new JsonObject()))
                .bounds(x, y + 26, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stay"), b -> send("stay", new JsonObject()))
                .bounds(x + half + 4, y + 26, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("What am I looking at?"), b -> send("look_at", new JsonObject()))
                .bounds(x, y + 52, WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x, y + 100, WIDTH, 20).build());
    }

    private void send(String tool, JsonObject args) {
        PacketDistributor.sendToServer(new ToolRequest(data.agentId(), tool, args.toString()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = (width - WIDTH) / 2;
        int y = height / 2 - 50;
        g.drawCenteredString(font, data.name(), width / 2, y - 34, 0xFFFFFF);
        g.drawCenteredString(font, "Credits: " + data.credits() + "   Mode: " + (data.following() ? "following" : "staying"),
                width / 2, y - 20, 0xAAAAAA);
        if (!data.message().isEmpty()) g.drawCenteredString(font, data.message(), width / 2, y + 80, 0xFFFF55);
    }

    // Must not pause: in singleplayer a pause screen stops the server, so replies would never arrive.
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
