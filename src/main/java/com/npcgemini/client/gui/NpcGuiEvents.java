package com.npcgemini.client.gui;

import com.npcgemini.NPCGeminiMod;
import com.npcgemini.entity.NpcInventory;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Cambia el cofre vanilla que abre el servidor para el NPC por la GUI propia (mismo contenedor). */
@Mod.EventBusSubscriber(modid = NPCGeminiMod.MODID, value = Side.CLIENT)
public class NpcGuiEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGuiOpen(GuiOpenEvent event) {
        GuiScreen g = event.getGui();
        if (!(g instanceof GuiChest)) return;
        Container c = ((GuiChest) g).inventorySlots;
        if (!(c instanceof ContainerChest)) return;
        String title = ((ContainerChest) c).getLowerChestInventory().getDisplayName().getUnformattedText();
        if (title == null || !title.startsWith(NpcInventory.TITLE_PREFIX)) return;
        String[] parts = title.split("\\|", 3);
        if (parts.length < 3) return;
        try {
            int id = Integer.parseInt(parts[1]);
            event.setGui(new GuiNpcContainer((ContainerChest) c, id, parts[2]));
        } catch (NumberFormatException ignored) { }
    }
}
