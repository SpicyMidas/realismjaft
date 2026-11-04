package dev.agentbuddy.client;

import com.google.gson.JsonObject;
import dev.agentbuddy.network.AgentScreenData;
import dev.agentbuddy.network.ToolRequest;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Agent GUI (AGENT_SPEC.md section 6). Every button sends a {@link ToolRequest}; the server runs the tool
 * and answers with fresh {@link AgentScreenData}. Waystones and death return are on {@link WaystoneScreen}.
 */
public class AgentScreen extends Screen implements AgentDataScreen {
    private static final int WIDTH = 220;
    private static final int ROW = 22;
    private static final int SHOWN_IGNORED = 3;
    private AgentScreenData data;
    private EditBox nameBox;
    private EditBox itemBox;

    public AgentScreen(AgentScreenData data) {
        super(Component.literal(data.name()));
        this.data = data;
    }

    @Override
    public UUID agentId() {
        return data.agentId();
    }

    @Override
    public void update(AgentScreenData fresh) {
        boolean ignoredChanged = !fresh.lists().ignored().equals(data.lists().ignored());
        this.data = fresh;
        if (nameBox != null && !nameBox.isFocused()) nameBox.setValue(fresh.name());
        if (ignoredChanged) rebuildWidgets(); // the Unignore buttons follow the list
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return Math.max(30, height / 2 - 100);
    }

    @Override
    protected void init() {
        String keepItem = itemBox == null ? "" : itemBox.getValue();
        int x = left();
        int y = top();
        int half = (WIDTH - 4) / 2;
        int third = (WIDTH - 8) / 3;

        nameBox = new EditBox(font, x, y, WIDTH - 64, 20, Component.literal("Name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(data.name());
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> send("set_name", arg("name", nameBox.getValue())))
                .bounds(x + WIDTH - 60, y, 60, 20).build());

        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Follow"), b -> send("follow", new JsonObject())).bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stay"), b -> send("stay", new JsonObject())).bounds(x + half + 4, y, half, 20).build());

        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Look"), b -> send("look_at", new JsonObject())).bounds(x, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Snapshot"), b -> send("snapshot_container", new JsonObject()))
                .bounds(x + third + 4, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Ignore"), b -> send("ignore_block", new JsonObject()))
                .bounds(x + 2 * (third + 4), y, third, 20).build());

        y += ROW;
        itemBox = new EditBox(font, x, y, WIDTH - 104, 20, Component.literal("Item"));
        itemBox.setMaxLength(48);
        itemBox.setHint(Component.literal("item, e.g. diamond"));
        itemBox.setValue(keepItem);
        addRenderableWidget(itemBox);
        addRenderableWidget(Button.builder(Component.literal("Find"), b -> send("find_item", arg("item", itemBox.getValue())))
                .bounds(x + WIDTH - 100, y, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fetch"), b -> send("fetch_item", arg("item", itemBox.getValue())))
                .bounds(x + WIDTH - 48, y, 48, 20).build());

        y += ROW + 14; // message line
        for (int i = 0; i < Math.min(SHOWN_IGNORED, data.lists().ignored().size()); i++) {
            BlockPos p = data.lists().ignored().get(i);
            addRenderableWidget(Button.builder(Component.literal("Unignore"), b -> {
                JsonObject a = new JsonObject();
                a.addProperty("x", p.getX());
                a.addProperty("y", p.getY());
                a.addProperty("z", p.getZ());
                send("unignore_block", a);
            }).bounds(x + WIDTH - 70, y + 12 + i * ROW, 70, 20).build());
        }

        int last = y + 16 + SHOWN_IGNORED * ROW;
        addRenderableWidget(Button.builder(Component.literal("Waystones..."), b -> minecraft.setScreen(new WaystoneScreen(data, this)))
                .bounds(x, last, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + half + 4, last, half, 20).build());
    }

    private static JsonObject arg(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    private void send(String tool, JsonObject args) {
        PacketDistributor.sendToServer(new ToolRequest(data.agentId(), tool, args.toString()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        g.drawCenteredString(font, data.name(), width / 2, y - 26, 0xFFFFFF);
        g.drawCenteredString(font, "Credits: " + data.credits() + "   Mode: " + (data.following() ? "following" : "staying"),
                width / 2, y - 13, 0xAAAAAA);
        int msgY = y + 4 * ROW + 2;
        if (!data.message().isEmpty()) g.drawCenteredString(font, data.message(), width / 2, msgY, 0xFFFF55);
        int listY = msgY + 12;
        var ignored = data.lists().ignored();
        g.drawString(font, ignored.isEmpty() ? "Ignored: none" : "Ignored:", x, listY, 0xAAAAAA);
        for (int i = 0; i < Math.min(SHOWN_IGNORED, ignored.size()); i++) {
            BlockPos p = ignored.get(i);
            g.drawString(font, p.getX() + ", " + p.getY() + ", " + p.getZ(), x + 6, listY + 18 + i * ROW, 0xFFFFFF);
        }
        if (ignored.size() > SHOWN_IGNORED) {
            g.drawString(font, "+" + (ignored.size() - SHOWN_IGNORED) + " more", x + 60, listY, 0xAAAAAA);
        }
    }

    // Must not pause: in singleplayer a pause screen stops the server, so replies would never arrive.
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
