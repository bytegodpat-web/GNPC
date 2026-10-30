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
        // Nunca modificar la copia cliente: el inventario real vive en la entidad del servidor integrado.
        serverNpc = ChatHandler.resolveServerNpc(clientNpc);

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

        ItemStack slotStack = serverNpc.getStackInSlot(slot);
        ItemStack held = mc.player.getHeldItemMainhand();

        // Click izquierdo: transferir el stack completo desde la mano al NPC,
        // o retirar el stack del NPC si la mano está vacía.
        if (mouseButton == 0) {
            if (!held.isEmpty()) {
                int leftover = serverNpc.insertItem(held);
                int moved = held.getCount() - leftover;
                if (moved > 0) held.shrink(moved);
            } else if (!slotStack.isEmpty()) {
                // Extraer primero del NPC y devolver al NPC lo que no quepa en el jugador.
                ItemStack extracted = serverNpc.extractFromSlot(slot, slotStack.getCount());
                if (!extracted.isEmpty() && !mc.player.inventory.addItemStackToInventory(extracted)) {
                    if (!extracted.isEmpty()) serverNpc.insertItem(extracted);
                }
            }
        }
        // Click derecho: entregar solo una unidad desde la mano.
        if (mouseButton == 1 && !held.isEmpty()) {
            ItemStack one = held.copy();
            one.setCount(1);
            if (serverNpc.insertItem(one) == 0) held.shrink(1);
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
        if (serverNpc == null) {
            drawCenteredString(fontRenderer, "§cNo se pudo conectar con el inventario del servidor", width / 2, invTop + 20, 0xFFFFFF);
        }

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
        fontRenderer.drawString("§7Click izq: guardar stack / retirar si tienes la mano vacía", invLeft - 24, invTop + ROWS * SLOT + 40, 0xAAAAAA);
        fontRenderer.drawString("§7Click der: guardar 1 objeto desde la mano", invLeft - 24, invTop + ROWS * SLOT + 51, 0xAAAAAA);
        if (serverNpc != null) fontRenderer.drawString("§7Espacios usados: " + serverNpc.getUsedSlots() + "/27", invLeft, invTop - 27, 0xAAAAAA);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
