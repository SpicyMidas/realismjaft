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

/** Waystone list (Go / Remove), "Set here", and "Back to death spot". Every button is a {@link ToolRequest}. */
public class WaystoneScreen extends Screen implements AgentDataScreen {
    private static final int WIDTH = 220;
    private static final int ROW = 22;
    private final Screen parent;
    private AgentScreenData data;
    private EditBox nameBox;

    public WaystoneScreen(AgentScreenData data, Screen parent) {
        super(Component.literal("Waystones"));
        this.data = data;
        this.parent = parent;
    }

    @Override
    public UUID agentId() {
        return data.agentId();
    }

    @Override
    public void update(AgentScreenData fresh) {
        boolean listChanged = !fresh.lists().equals(data.lists());
        data = fresh;
        if (parent instanceof AgentScreen a) a.update(fresh);
        if (listChanged) rebuildWidgets();
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return Math.max(30, height / 2 - 100);
    }

    @Override
    protected void init() {
        String keep = nameBox == null ? "" : nameBox.getValue();
        int x = left();
        int y = top();
        for (int i = 0; i < data.lists().waystones().size(); i++) {
            String name = data.lists().waystones().get(i).name();
            int ry = y + i * ROW;
            addRenderableWidget(Button.builder(Component.literal("Go"), b -> send("teleport_waystone", name))
                    .bounds(x + WIDTH - 74, ry, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("X"), b -> send("remove_waystone", name))
                    .bounds(x + WIDTH - 30, ry, 30, 20).build());
        }
        int y2 = y + Math.max(1, data.lists().waystones().size()) * ROW + 4;
        nameBox = new EditBox(font, x, y2, WIDTH - 84, 20, Component.literal("Name"));
        nameBox.setMaxLength(24);
        nameBox.setHint(Component.literal("new waystone name"));
        nameBox.setValue(keep);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Set here"), b -> send("set_waystone", nameBox.getValue()))
                .bounds(x + WIDTH - 80, y2, 80, 20).build());
        Button death = Button.builder(Component.literal("Back to death spot (10 credits)"), b -> send("death_return", null))
                .bounds(x, y2 + ROW + 14, WIDTH, 20).build();
        death.active = data.lists().canReturnToDeath();
        addRenderableWidget(death);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent))
                .bounds(x, y2 + 2 * ROW + 14, WIDTH, 20).build());
    }

    private void send(String tool, String name) {
        JsonObject args = new JsonObject();
        if (name != null) args.addProperty("name", name);
        PacketDistributor.sendToServer(new ToolRequest(data.agentId(), tool, args.toString()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        g.drawCenteredString(font, "Waystones (" + data.lists().waystones().size() + "/5)   Credits: " + data.credits(),
                width / 2, y - 14, 0xFFFFFF);
        if (data.lists().waystones().isEmpty()) g.drawString(font, "No waystones yet.", x, y + 6, 0xAAAAAA);
        for (int i = 0; i < data.lists().waystones().size(); i++) {
            AgentScreenData.WaystoneInfo w = data.lists().waystones().get(i);
            String where = w.distance() < 0 ? "other dimension" : w.distance() == 1 ? "1 block" : w.distance() + " blocks";
            g.drawString(font, w.name() + "  (" + where + ")", x, y + i * ROW + 6, 0xFFFFFF);
        }
        int y2 = y + Math.max(1, data.lists().waystones().size()) * ROW + 4;
        if (!data.message().isEmpty()) g.drawCenteredString(font, data.message(), width / 2, y2 + ROW + 2, 0xFFFF55);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
