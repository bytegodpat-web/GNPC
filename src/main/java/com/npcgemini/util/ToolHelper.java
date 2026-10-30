package com.npcgemini.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;

/** Elección de herramienta adecuada para un bloque. */
public class ToolHelper {

    /** Resultado: slot de la herramienta (-1 = manos) y si se puede cosechar. */
    public static class Choice {
        public final int slot;
        public final boolean canHarvest;
        public final float speed;
        public Choice(int slot, boolean canHarvest, float speed) { this.slot = slot; this.canHarvest = canHarvest; this.speed = speed; }
    }

    public static boolean needsTool(IBlockState state) {
        return !state.getMaterial().isToolNotRequired();
    }

    /** Devuelve la mejor herramienta del inventario, o manos (slot -1) si no hay mejor. */
    public static Choice choose(com.npcgemini.entity.EntityNPC npc, IBlockState state) {
        boolean needs = needsTool(state);
        int bestSlot = -1;
        float bestSpeed = 1.0F;
        for (int i = 0; i < com.npcgemini.entity.EntityNPC.INV_SIZE; i++) {
            ItemStack st = npc.getStackInSlot(i);
            if (st.isEmpty()) continue;
            boolean can = st.canHarvestBlock(state);
            if (needs && !can) continue;
            float sp = st.getDestroySpeed(state);
            if (sp > bestSpeed || (needs && bestSlot == -1)) { bestSlot = i; bestSpeed = sp; }
        }
        boolean canHarvest = !needs || bestSlot != -1;
        return new Choice(bestSlot, canHarvest, bestSpeed);
    }

    /** Nombre de la herramienta que hace falta (para mensajes de error). */
    public static String requiredToolName(IBlockState state) {
        String tool = state.getBlock().getHarvestTool(state);
        int lvl = state.getBlock().getHarvestLevel(state);
        if (tool == null) return "una herramienta";
        String base = "pickaxe".equals(tool) ? "un pico" : "axe".equals(tool) ? "un hacha" : "shovel".equals(tool) ? "una pala" : "una herramienta (" + tool + ")";
        String mat = lvl <= 0 ? "" : lvl == 1 ? " de piedra o mejor" : lvl == 2 ? " de hierro o mejor" : " de diamante";
        return base + mat;
    }
}
