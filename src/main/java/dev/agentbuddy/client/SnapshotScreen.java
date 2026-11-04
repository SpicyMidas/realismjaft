package dev.agentbuddy.client;

import dev.agentbuddy.brain.reply.Names;
import dev.agentbuddy.network.ContainerSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The read-only "photo" of a container (AGENT_SPEC.md section 4, snapshot_container). Nothing can be taken. */
public class SnapshotScreen extends Screen {
    private static final int COLS = 9;
    private static final int SLOT = 18;
    private final ContainerSnapshot snap;

    public SnapshotScreen(ContainerSnapshot snap) {
        super(Component.literal(Names.capitalized(snap.containerType()) + " at " + snap.pos().getX() + ", "
                + snap.pos().getY() + ", " + snap.pos().getZ() + " (snapshot)"));
        this.snap = snap;
    }

    private int left() {
        return (width - COLS * SLOT) / 2;
    }

    private int top() {
        return (height - rows() * SLOT) / 2;
    }

    private int rows() {
        return Math.max(1, (snap.stacks().size() + COLS - 1) / COLS);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 50, top() + rows() * SLOT + 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, top() - 14, 0xFFFFFF);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < snap.stacks().size(); i++) {
            int x = left() + (i % COLS) * SLOT;
            int y = top() + (i / COLS) * SLOT;
            g.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0xFF373737);
            g.fill(x + 1, y + 1, x + SLOT - 2, y + SLOT - 2, 0xFF8B8B8B);
            ItemStack s = snap.stacks().get(i);
            if (s.isEmpty()) continue;
            g.renderItem(s, x + 1, y + 1);
            g.renderItemDecorations(font, s, x + 1, y + 1);
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) hovered = s;
        }
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
