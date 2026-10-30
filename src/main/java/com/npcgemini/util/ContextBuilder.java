package com.npcgemini.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.List;

/**
 * Construye el bloque [CONTEXTO: ...] con datos fiables del mundo.
 */
public class ContextBuilder {

    public static String build(EntityPlayer player, boolean nearBot) {
        if (player == null || player.world == null) return "";

        World world = player.world;
        StringBuilder sb = new StringBuilder("[CONTEXTO: ");

        // Bioma
        Biome biome = world.getBiome(player.getPosition());
        sb.append("bioma=").append(biome.getBiomeName());

        // Hora
        long time = world.getWorldTime() % 24000;
        String momento;
        if (time < 1000) momento = "amanecer";
        else if (time < 6000) momento = "mañana";
        else if (time < 12000) momento = "mediodía";
        else if (time < 13000) momento = "atardecer";
        else if (time < 18000) momento = "noche";
        else momento = "medianoche";
        sb.append(", hora=").append(momento);

        // Vida
        float hp = player.getHealth();
        float max = player.getMaxHealth();
        sb.append(", vida=").append(String.format("%.0f/%.0f", hp, max));

        // Objeto en mano
        ItemStack held = player.getHeldItemMainhand();
        if (!held.isEmpty()) {
            sb.append(", mano=").append(held.getDisplayName());
        }

        // Mobs hostiles cercanos
        AxisAlignedBB box = player.getEntityBoundingBox().grow(12.0);
        List<EntityLivingBase> nearby = world.getEntitiesWithinAABB(EntityLivingBase.class, box,
                e -> e instanceof EntityMob && e.isEntityAlive());
        if (!nearby.isEmpty()) {
            sb.append(", mobs=");
            int count = 0;
            for (EntityLivingBase e : nearby) {
                if (count > 0) sb.append("+");
                sb.append(e.getName());
                if (e instanceof EntityCreeper) sb.append("(creeper!)");
                count++;
                if (count >= 4) break;
            }
        }

        sb.append(", cerca=").append(nearBot ? "si" : "no");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Detecta si hay un peligro real que justifique una alerta.
     */
    public static String detectAlert(EntityPlayer player) {
        if (player == null || player.world == null) return null;

        if (player.getHealth() <= 6.0F) {
            return "vida baja (" + String.format("%.0f", player.getHealth()) + ")";
        }

        AxisAlignedBB box = player.getEntityBoundingBox().grow(6.0);
        List<EntityCreeper> creepers = player.world.getEntitiesWithinAABB(EntityCreeper.class, box,
                e -> e.isEntityAlive());
        if (!creepers.isEmpty()) {
            return "creeper cerca";
        }

        return null;
    }
}
