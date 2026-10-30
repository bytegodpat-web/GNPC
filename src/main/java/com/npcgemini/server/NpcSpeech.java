package com.npcgemini.server;

import com.npcgemini.config.ModConfig;
import com.npcgemini.entity.EntityNPC;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;

/**
 * Mensajes del NPC generados por el servidor (resultado de tareas, errores).
 * Salen en el chat del dueño y como burbuja sobre la cabeza.
 * El mod es solo cliente (servidor integrado), por eso puede avisar a la burbuja del cliente.
 */
public class NpcSpeech {

    public static void say(EntityNPC npc, String text) {
        if (npc == null || npc.world == null || npc.world.isRemote || text == null || text.isEmpty()) return;
        EntityPlayer target = npc.getOwnerPlayer();
        if (target == null) target = npc.world.getClosestPlayerToEntity(npc, 64.0D);
        if (target != null) target.sendMessage(new TextComponentString("\u00a7d[" + npc.getName() + "] \u00a7f" + text));

        final int id = npc.getEntityId();
        final String msg = text;
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.world == null || !ModConfig.bubbleEnabled) return;
            Entity e = mc.world.getEntityByID(id);
            if (e instanceof EntityNPC) ((EntityNPC) e).showBubble(msg);
        });
    }
}
