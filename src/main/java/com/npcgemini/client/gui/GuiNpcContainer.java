package com.npcgemini.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import com.npcgemini.entity.EntityNPC;

import java.io.IOException;

/**
 * Inventario del NPC. El contenedor es el ContainerChest vanilla creado por el servidor:
 * clics, arrastrar, shift-clic y sincronización las hace Minecraft, así que no hay objetos
 * que desaparezcan o se dupliquen. Esta clase solo dibuja el diseño propio y añade botones
 * que envían clics de contenedor normales.
 */
public class GuiNpcContainer extends GuiContainer {

    private static final int PANEL_W = 100;
    private final int npcId;
    private final String npcName;

    public GuiNpcContainer(ContainerChest container, int npcId, String npcName) {
        super(container);
        this.npcId = npcId;
        this.npcName = npcName;
        this.xSize = 176;
        this.ySize = 168;
    }

    private EntityNPC npc() {
        Entity e = mc.world == null ? null : mc.world.getEntityByID(npcId);
        return e instanceof EntityNPC ? (EntityNPC) e : null;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft -= (PANEL_W + 6) / 2;   // deja sitio al panel lateral
        int px = guiLeft + xSize + 6;
        int py = guiTop + 92;
        buttonList.add(new GuiButton(0, px, py, PANEL_W, 20, "Tomar todo"));
        buttonList.add(new GuiButton(1, px, py + 24, PANEL_W, 20, "Guardar objeto en mano"));
        buttonList.add(new GuiButton(2, px, py + 48, PANEL_W, 20, "Volver"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            for (int i = 0; i < EntityNPC.INV_SIZE; i++) {
                if (inventorySlots.getSlot(i).getHasStack())
                    mc.playerController.windowClick(inventorySlots.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
            }
        } else if (button.id == 1) {
            int slot = EntityNPC.INV_SIZE + 27 + mc.player.inventory.currentItem;
            if (inventorySlots.getSlot(slot).getHasStack())
                mc.playerController.windowClick(inventorySlots.windowId, slot, 0, ClickType.QUICK_MOVE, mc.player);
        } else if (button.id == 2) {
            Entity e = mc.world.getEntityByID(npcId);
            mc.player.closeScreen();
            if (e instanceof EntityNPC) mc.displayGuiScreen(new GuiNpcInteract((EntityNPC) e));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // Panel del inventario
        drawRect(guiLeft - 4, guiTop - 4, guiLeft + xSize + 4, guiTop + ySize + 4, 0xF0101018);
        drawRect(guiLeft - 3, guiTop - 3, guiLeft + xSize + 3, guiTop + ySize + 3, 0xF03A3A4A);
        drawRect(guiLeft - 2, guiTop - 2, guiLeft + xSize + 2, guiTop + ySize + 2, 0xF01C1C26);
        // Huecos
        for (Slot s : inventorySlots.inventorySlots) {
            int x = guiLeft + s.xPos - 1, y = guiTop + s.yPos - 1;
            drawRect(x, y, x + 18, y + 18, 0xFF0B0B10);
            drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF4A4A58);
        }
        // Panel lateral
        int px = guiLeft + xSize + 6;
        drawRect(px - 2, guiTop - 4, px + PANEL_W + 2, guiTop + ySize + 4, 0xF0101018);
        drawRect(px - 1, guiTop - 3, px + PANEL_W + 1, guiTop + ySize + 3, 0xF01C1C26);
        EntityNPC n = npc();
        if (n != null) GuiInventory.drawEntityOnScreen(px + PANEL_W / 2, guiTop + 62, 30,
                (float) (px + PANEL_W / 2 - mouseX), (float) (guiTop + 20 - mouseY), n);
        fontRenderer.drawStringWithShadow("\u00a7d" + npcName, px + 4, guiTop + 2, 0xFFFFFF);
        int used = 0;
        for (int i = 0; i < EntityNPC.INV_SIZE; i++) if (inventorySlots.getSlot(i).getHasStack()) used++;
        fontRenderer.drawString("\u00a77Espacios: " + used + "/" + EntityNPC.INV_SIZE, px + 4, guiTop + 72, 0xAAAAAA);
        if (n != null) fontRenderer.drawString("\u00a77Vida: " + (int) n.getHealth() + "/" + (int) n.getMaxHealth(), px + 4, guiTop + 82, 0xAAAAAA);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString("Inventario de " + npcName, 8, 6, 0xE0E0E0);
        fontRenderer.drawString("Tu inventario", 8, 73, 0xE0E0E0);
    }
}
