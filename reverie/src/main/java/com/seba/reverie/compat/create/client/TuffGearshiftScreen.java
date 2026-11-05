package com.seba.reverie.compat.create.client;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.create.CreateNetwork.SelectGearPayload;
import com.seba.reverie.compat.create.gearshift.GateLayout;
import com.seba.reverie.compat.create.gearshift.GearstickAnimator;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Car-style gate plate with a draggable knob. The knob is constrained by {@link GateLayout#drag}; reaching a slot
 * end commits that gear with one payload, releasing on the rail commits N, releasing mid-column springs back.
 * All coordinates are in a fixed logical panel space scaled to the GUI, so it is resolution independent.
 */
public class TuffGearshiftScreen extends Screen {
    private static final ResourceLocation TEX = Reverie.id("textures/gui/gearshift.png");
    private static final int W = 232;
    private static final int H = 132;
    private static final int PLATE_X = 8;
    private static final int PLATE_Y = 20;
    private static final int PLATE_W = 144;
    private static final int PLATE_H = 100;
    private static final int GATE_MARGIN = 24;
    private static final int ROW = 30;

    private final BlockPos pos;
    private final ClientLevel openedIn;
    private GateLayout layout = GateLayout.REGULAR;
    private GearstickAnimator knob;

    private boolean dragging;
    private final float[] dragPos = new float[2];
    private int committed;
    private int ticksSinceCommit = 100;
    private float wallShake;
    private int refuseFlash;
    private float lastRailTick;
    private float left;
    private float top;
    private float scale = 1f;
    private int ticks;
    private int tooFastFlash;
    /** What is drawn: glides along the gate toward the drag point or the animator, frame by frame. */
    private final float[] shown = new float[2];
    private boolean shownSet;
    private long lastFrame;

    public TuffGearshiftScreen(BlockPos pos) {
        super(Component.translatable("reverie.gearshift.screen.title"));
        this.pos = pos.immutable();
        this.openedIn = Minecraft.getInstance().level;
    }

    @Override
    protected void init() {
        TuffGearshiftBlockEntity be = blockEntity();
        if (be == null) {
            onClose();
            return;
        }
        if (knob == null) {
            layout = be.layout();
            committed = be.isLocked() ? GateLayout.R : be.selectedGear();
            knob = new GearstickAnimator(layout, committed);
            be.muted = true;
        }
        scale = Math.min(1.6f, Math.min((width - 8) / (float) W, (height - 8) / (float) H));
        if (scale > 1f) scale = (float) Math.floor(scale * 2) / 2f;
        left = (width - W * scale) / 2f;
        top = (height - H * scale) / 2f;
    }

    private TuffGearshiftBlockEntity blockEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != openedIn) return null;
        return mc.level.getBlockEntity(pos) instanceof TuffGearshiftBlockEntity be ? be : null;
    }

    @Override
    public void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        TuffGearshiftBlockEntity be = blockEntity();
        if (be == null || mc.player == null || !mc.player.isAlive()
                || mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > 8.0 * 8.0) {
            onClose();
            return;
        }
        ticksSinceCommit++;
        if (refuseFlash > 0) refuseFlash--;
        if (tooFastFlash > 0) tooFastFlash--;
        wallShake *= 0.7f;
        if (!dragging) {
            int want = be.isLocked() ? GateLayout.R : committed;
            // Reconcile with the server if it did not take our selection.
            if (!be.isLocked() && ticksSinceCommit > 15 && be.selectedGear() != committed) want = committed = be.selectedGear();
            if (knob.targetGear() != want || !knob.moving() && knobOff(want)) knob.moveTo(want, be.isLocked());
        }
        knob.tick();
    }

    private boolean knobOff(int gear) {
        return Math.abs(knob.x(1) - layout.slotX(gear)) > 0.01f || Math.abs(knob.y(1) - layout.slotY(gear)) > 0.12f;
    }

    @Override
    public void onClose() {
        TuffGearshiftBlockEntity be = blockEntity();
        if (be != null) {
            be.muted = false;
            be.animator().snapTo(be.effectiveGear());
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ geometry

    private float colSpacing() {
        return (PLATE_W - 2f * GATE_MARGIN) / (layout.columns - 1);
    }

    private float gateToPanelX(float gx) {
        return PLATE_X + GATE_MARGIN + gx * colSpacing();
    }

    private float gateToPanelY(float gy) {
        return PLATE_Y + PLATE_H / 2f + gy * ROW;
    }

    private float panelX(double mouseX) {
        return (float) ((mouseX - left) / scale);
    }

    private float panelY(double mouseY) {
        return (float) ((mouseY - top) / scale);
    }

    private float knobX(float pt) {
        return dragging ? dragPos[0] : knob.x(pt);
    }

    private float knobY(float pt) {
        return dragging ? dragPos[1] : knob.y(pt);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && knob != null) {
            float px = panelX(mouseX);
            float py = panelY(mouseY);
            float kx = gateToPanelX(shownSet ? shown[0] : knobX(1));
            float ky = gateToPanelY(shownSet ? shown[1] : knobY(1));
            if ((px - kx) * (px - kx) + (py - ky) * (py - ky) <= 13 * 13) {
                TuffGearshiftBlockEntity be = blockEntity();
                if (be != null && be.isLocked()) {
                    refuse(be);
                    return true;
                }
                dragging = true;
                dragPos[0] = knob.x(1);
                dragPos[1] = knob.y(1);
                lastRailTick = dragPos[0];
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!dragging) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        TuffGearshiftBlockEntity be = blockEntity();
        if (be != null && be.isLocked()) {
            dragging = false;
            refuse(be);
            return true;
        }
        float gx = (panelX(mouseX) - PLATE_X - GATE_MARGIN) / colSpacing();
        float gy = (panelY(mouseY) - PLATE_Y - PLATE_H / 2f) / ROW;
        boolean wall = layout.drag(dragPos, gx, gy);
        if (wall && wallShake < 0.4f) wallShake = 1f;
        if (Math.abs(dragPos[1]) < 0.01f && Math.abs(dragPos[0] - lastRailTick) > 0.4f) {
            lastRailTick = dragPos[0];
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f, 0.08f);
        }
        int at = layout.gearAt(dragPos[0], dragPos[1]);
        if (at > 0 && at != committed) {
            if (be != null && be.wouldOverspeed(at)) {
                // The gear would spin faster than Create allows: the gate pushes back.
                dragPos[1] = Math.signum(dragPos[1]) * 0.7f;
                tooFast(be);
            } else {
                commit(at);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!dragging) return super.mouseReleased(mouseX, mouseY, button);
        dragging = false;
        int at = layout.gearAt(dragPos[0], dragPos[1]);
        knob.place(dragPos[0], dragPos[1]);
        if (at == GateLayout.N) {
            if (committed != GateLayout.N) commit(GateLayout.N);
            knob.moveTo(GateLayout.N, false);
        } else {
            // In a slot end: stays (already committed). Mid-column: spring back along the gate.
            knob.moveTo(committed, false);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int gear = -1;
        if (keyCode == 82) gear = GateLayout.R;            // R
        else if (keyCode == 78) gear = GateLayout.N;       // N
        else if (keyCode >= 49 && keyCode <= 54) gear = keyCode - 48; // 1-6
        else if (keyCode >= 321 && keyCode <= 326) gear = keyCode - 320; // keypad 1-6
        if (gear >= 0 && knob != null) {
            TuffGearshiftBlockEntity be = blockEntity();
            if (be != null && be.isLocked()) {
                refuse(be);
                return true;
            }
            if (layout.isValid(gear) && !dragging) {
                if (be != null && gear != committed && be.wouldOverspeed(gear)) {
                    tooFast(be);
                    return true;
                }
                if (gear != committed) commit(gear);
                knob.moveTo(gear, false);
            }
            return true;
        }
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commit(int gear) {
        committed = gear;
        ticksSinceCommit = 0;
        PacketDistributor.sendToServer(new SelectGearPayload(pos, gear));
        if (gear == GateLayout.R) uiSound(SoundEvents.PISTON_CONTRACT, 0.75f, 0.35f);
        else if (gear == GateLayout.N) uiSound(SoundEvents.STONE_BUTTON_CLICK_OFF, 1.4f, 0.3f);
        else uiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.9f + 0.06f * gear, 0.35f);
    }

    private void refuse(TuffGearshiftBlockEntity be) {
        refuseFlash = 12;
        wallShake = 1.4f;
        be.animator().refuse();
        uiSound(SoundEvents.CHAIN_HIT, 0.6f, 0.4f);
    }

    private void tooFast(TuffGearshiftBlockEntity be) {
        if (tooFastFlash > 0) return;
        tooFastFlash = 24;
        refuse(be);
    }

    /** Moves {@link #shown} toward the knob's target along the gate (never across the plate), by real frame time. */
    private void glide(float pt) {
        float tx = dragging ? dragPos[0] : knob.x(pt);
        float ty = dragging ? dragPos[1] : knob.y(pt);
        long now = net.minecraft.Util.getNanos();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
        lastFrame = now;
        if (!shownSet) {
            shown[0] = tx;
            shown[1] = ty;
            shownSet = true;
            return;
        }
        float follow = 1f - (float) Math.exp(-dt * (dragging ? 30f : 45f));
        java.util.List<float[]> path = new java.util.ArrayList<>();
        path.add(new float[]{shown[0], shown[1]});
        boolean sameColumn = Math.abs(shown[0] - tx) < 1e-3f;
        if (!sameColumn && Math.abs(shown[1]) > 1e-3f) path.add(new float[]{shown[0], 0});
        if (!sameColumn && Math.abs(ty) > 1e-3f) path.add(new float[]{tx, 0});
        path.add(new float[]{tx, ty});
        float[] p = GateLayout.along(path, GateLayout.length(path) * follow);
        shown[0] = p[0];
        shown[1] = p[1];
    }

    private static void uiSound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        TuffGearshiftBlockEntity be = blockEntity();
        if (be == null || knob == null) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1);

        drawFrame(g, be);
        drawGate(g, be);
        drawKnob(g, partialTick);
        drawReadout(g, be);

        pose.popPose();
    }

    private void drawFrame(GuiGraphics g, TuffGearshiftBlockEntity be) {
        // Panel body: tuff inside an alloy edge, a wooden title board with a pale alloy inlay under it.
        g.fill(0, 0, W, H, 0xFF2B2D27);
        g.fill(1, 1, W - 1, H - 1, 0xFF4E5147);
        for (int y = 2; y < H - 2; y += 16) {
            for (int x = 2; x < W - 2; x += 16) {
                g.blit(TEX, x, y, 80, 104, Math.min(16, W - 2 - x), Math.min(16, H - 2 - y));
            }
        }
        g.blit(TEX, 2, 2, 0, 132, W - 4, 11);
        g.fill(2, 13, W - 2, 14, 0xFFBBBDB1);
        g.fill(2, H - 3, W - 2, H - 2, 0x802B2D27);
        g.drawString(font, be.getBlockState().getBlock().getName(), 6, 4, 0xFFF2EAD8, true);
        // Plate: the block's top (wood board between tuff strips), recessed.
        g.fill(PLATE_X - 1, PLATE_Y - 1, PLATE_X + PLATE_W + 1, PLATE_Y + PLATE_H + 1, 0xFF1C201C);
        g.blit(TEX, PLATE_X, PLATE_Y, PLATE_W, PLATE_H, 0, 0, 144, 100, 256, 256);
    }

    private void drawGate(GuiGraphics g, TuffGearshiftBlockEntity be) {
        int active = dragging ? layout.gearAt(dragPos[0], dragPos[1]) : (be.isLocked() ? GateLayout.R : committed);
        float railY = gateToPanelY(0);
        groove(g, gateToPanelX(0), railY, gateToPanelX(layout.columns - 1), railY);
        for (int c = 0; c < layout.columns; c++) {
            float x = gateToPanelX(c);
            float y0 = layout.hasSlot(c, -1) ? gateToPanelY(-1) : railY;
            float y1 = layout.hasSlot(c, 1) ? gateToPanelY(1) : railY;
            groove(g, x, y0, x, y1);
        }
        for (int gear : layout.gears()) {
            float x = gateToPanelX(layout.slotX(gear));
            float y = gateToPanelY(layout.slotY(gear));
            String label = GateLayout.label(gear);
            int w = font.width(label);
            boolean restOnColumn = layout.restX == Math.round(layout.restX);
            int ly = gear == GateLayout.N ? (int) (restOnColumn ? y - 14 : y + 7) : layout.slotY(gear) < 0 ? (int) (y - 16) : (int) (y + 8);
            if (gear == GateLayout.N && restOnColumn) x += 11; // N sits where the 3/4 column crosses the rail
            boolean on = gear == active;
            int color = on ? (gear == GateLayout.R ? 0xFFFF5040 : gear == GateLayout.N ? 0xFFFFC040 : 0xFF70FF70) : 0xFF2C302C;
            if (on) {
                int glow = (color & 0x00FFFFFF) | 0x50000000;
                g.fill((int) x - w / 2 - 3, ly - 2, (int) x + w / 2 + 3, ly + 9, glow);
            }
            if (on) g.drawString(font, label, (int) x - w / 2, ly, color, true);
            else g.drawString(font, label, (int) x - w / 2, ly, color, false);
        }
    }

    private static void groove(GuiGraphics g, float x0, float y0, float x1, float y1) {
        int ax = Math.round(Math.min(x0, x1)) - 4;
        int bx = Math.round(Math.max(x0, x1)) + 4;
        int ay = Math.round(Math.min(y0, y1)) - 4;
        int by = Math.round(Math.max(y0, y1)) + 4;
        g.fill(ax, ay, bx, by, 0xFF0E100E);
        g.fill(ax + 1, ay + 1, bx - 1, by - 1, 0xFF1D211D);
        g.fill(ax + 1, by - 1, bx - 1, by, 0x40FFFFFF);
    }

    private void drawKnob(GuiGraphics g, float pt) {
        glide(pt);
        float shake = (float) Math.sin((ticks + pt) * 2.1) * 1.6f * wallShake;
        float x = gateToPanelX(shown[0]) + shake;
        float y = gateToPanelY(shown[1]);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        // Leather boot ring, then the knob (sport: metal ball with red band; regular: black knob with gold collar).
        g.blit(TEX, -12, -12, 32, 104, 24, 24, 256, 256);
        g.blit(TEX, -8, -8, layout == GateLayout.SPORT ? 16 : 0, 104, 16, 16, 256, 256);
        pose.popPose();
    }

    private void drawReadout(GuiGraphics g, TuffGearshiftBlockEntity be) {
        int x0 = PLATE_X + PLATE_W + 6;
        int y0 = PLATE_Y;
        int w = W - x0 - 6;
        g.fill(x0, y0, x0 + w, PLATE_Y + PLATE_H, 0xFF1A1E1A);
        g.fill(x0 + 1, y0 + 1, x0 + w - 1, PLATE_Y + PLATE_H - 1, 0xFF252A24);

        int gear = be.effectiveGear();
        String big = GateLayout.label(gear);
        int color = gear == GateLayout.R ? 0xFFFF5040 : gear == GateLayout.N ? 0xFFFFC040 : 0xFF70FF70;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0 + w / 2f, y0 + 6, 0);
        pose.scale(2.5f, 2.5f, 1);
        g.drawString(font, big, -font.width(big) / 2, 0, color, true);
        pose.popPose();

        float in = Math.abs(be.getSpeed());
        float out = be.hasSource() ? Math.abs(be.getSpeed() * be.appliedRatio()) : in;
        int ty = y0 + 36;
        g.drawString(font, Component.translatable("reverie.gearshift.screen.in").withStyle(ChatFormatting.GRAY), x0 + 5, ty, 0xFFAAAAAA, false);
        g.drawString(font, TuffGearshiftBlockEntity.formatRpm(in), x0 + w - 5 - font.width(TuffGearshiftBlockEntity.formatRpm(in)), ty, 0xFFE0E0E0, false);
        g.drawCenteredString(font, "↓", x0 + w / 2, ty + 9, 0xFF808080);
        g.drawString(font, Component.translatable("reverie.gearshift.screen.out").withStyle(ChatFormatting.GRAY), x0 + 5, ty + 18, 0xFFAAAAAA, false);
        g.drawString(font, TuffGearshiftBlockEntity.formatRpm(out), x0 + w - 5 - font.width(TuffGearshiftBlockEntity.formatRpm(out)), ty + 18, 0xFFE0E0E0, false);
        g.drawCenteredString(font, Component.translatable("reverie.gearshift.screen.rpm"), x0 + w / 2, ty + 28, 0xFF707070);
        String ratio = TuffGearshiftBlockEntity.formatRatio(be.appliedRatio());
        g.drawCenteredString(font, ratio, x0 + w / 2, ty + 40, 0xFFE2E4DA);
        if (tooFastFlash > 0 && (tooFastFlash / 3) % 2 == 0) {
            g.drawCenteredString(font, Component.translatable("reverie.gearshift.screen.too_fast"), x0 + w / 2, ty + 51, 0xFFFF6050);
        }

        boolean lit = be.isLocked() && (refuseFlash == 0 || (refuseFlash / 2) % 2 == 0);
        int lampY = PLATE_Y + PLATE_H - 16;
        g.blit(TEX, x0 + 4, lampY, lit ? 68 : 56, 104, 12, 12, 256, 256);
        pose.pushPose();
        pose.translate(x0 + 18, lampY + 3, 0);
        pose.scale(0.75f, 0.75f, 1);
        g.drawString(font, Component.translatable("reverie.gearshift.screen.redstone"), 0, 0, lit ? 0xFFFF6050 : 0xFF6A6A6A, false);
        pose.popPose();
    }
}
