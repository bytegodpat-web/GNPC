package com.npcgemini.client.gui;

import com.npcgemini.client.ChatHandler;
import com.npcgemini.entity.EntityNPC;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Inventario visual 3x9 del NPC (cliente).
 * Las modificaciones se aplican sobre la entidad del servidor integrado.
 */
public class GuiNPCInventory extends GuiScreen {

    private final EntityNPC clientNpc;
    private EntityNPC serverNpc;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT = 18;

    private int invLeft, invTop;

    private final GuiScreen parent;

    public GuiNPCInventory(EntityNPC npc) {
        this(npc, null);
    }

    public GuiNPCInventory(EntityNPC npc, GuiScreen parent) {
        this.clientNpc = npc;
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        serverNpc = ChatHandler.resolveServerNpc(clientNpc);
        if (serverNpc == null) serverNpc = clientNpc;

        invLeft = width / 2 - (COLS * SLOT) / 2;
        invTop = height / 2 - 40;

        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 100, invTop + ROWS * SLOT + 16, 98, 20, "Tomar todo"));
        buttonList.add(new GuiButton(1, width / 2 + 2, invTop + ROWS * SLOT + 16, 98, 20, parent != null ? "Volver" : "Cerrar"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 0 && serverNpc != null && mc.player != null) {
            int n = serverNpc.deliverAllTo(mc.player);
            mc.player.sendMessage(new TextComponentString(
                    "\u00a7aEntregado: " + n + " stacks."));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (serverNpc == null || mc.player == null) return;

        int slot = slotAt(mouseX, mouseY);
        if (slot < 0) return;

        // Click izquierdo: sacar stack al jugador
        if (mouseButton == 0) {
            ItemStack s = serverNpc.getStackInSlot(slot);
            if (!s.isEmpty()) {
                ItemStack copy = s.copy();
                if (mc.player.inventory.addItemStackToInventory(copy)) {
                    serverNpc.setStackInSlot(slot, ItemStack.EMPTY);
                } else if (copy.getCount() < s.getCount()) {
                    s.setCount(copy.getCount());
                }
            }
        }
        // Click derecho: meter 1 del item en mano del jugador en ese slot si esta vacio
        if (mouseButton == 1) {
            ItemStack held = mc.player.getHeldItemMainhand();
            if (!held.isEmpty() && held.getItem() != net.minecraft.init.Items.AIR
                    && serverNpc.getStackInSlot(slot).isEmpty()) {
                ItemStack one = held.copy();
                one.setCount(1);
                serverNpc.setStackInSlot(slot, one);
                held.shrink(1);
            }
        }
    }

    private int slotAt(int mx, int my) {
        for (int i = 0; i < EntityNPC.INV_SIZE; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = invLeft + col * SLOT;
            int y = invTop + row * SLOT;
            if (mx >= x && mx < x + SLOT && my >= y && my < y + SLOT) return i;
        }
        return -1;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = "Inventario de " + (serverNpc != null ? serverNpc.getName() : "NPC");
        drawCenteredString(fontRenderer, "\u00a7d" + title, width / 2, invTop - 16, 0xFFFFFF);

        // fondo
        drawRect(invLeft - 4, invTop - 4, invLeft + COLS * SLOT + 4, invTop + ROWS * SLOT + 4, 0xCC000000);

        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < EntityNPC.INV_SIZE; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = invLeft + col * SLOT;
            int y = invTop + row * SLOT;
            drawRect(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF8B8B8B);
            if (serverNpc != null) {
                ItemStack s = serverNpc.getStackInSlot(i);
                if (!s.isEmpty()) {
                    GlStateManager.pushMatrix();
                    itemRender.renderItemAndEffectIntoGUI(s, x + 1, y + 1);
                    itemRender.renderItemOverlayIntoGUI(fontRenderer, s, x + 1, y + 1, null);
                    GlStateManager.popMatrix();
                }
            }
        }
        RenderHelper.disableStandardItemLighting();

        // tooltip
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && serverNpc != null) {
            ItemStack s = serverNpc.getStackInSlot(slot);
            if (!s.isEmpty()) {
                renderToolTip(s, mouseX, mouseY);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        fontRenderer.drawString("\u00a77Click izq: sacar  |  Click der: guardar 1 de la mano", invLeft, invTop + ROWS * SLOT + 40, 0xAAAAAA);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
