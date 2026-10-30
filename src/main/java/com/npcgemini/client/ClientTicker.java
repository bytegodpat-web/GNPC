package com.npcgemini.client;

import com.npcgemini.NPCGeminiMod;
import com.npcgemini.config.ModConfig;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.gemini.GeminiClient;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import com.npcgemini.util.ContextBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NPCGeminiMod.MODID, value = Side.CLIENT)
public class ClientTicker {

    private static final Random RNG = new Random();
    private static final Map<String, Long> nextSpontaneous = new HashMap<>();
    private static final Map<String, Long> alertCooldown = new HashMap<>();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.isGamePaused()) return;

        tickCounter++;
        if (tickCounter % 40 != 0) return;

        EntityPlayerSP player = mc.player;
        long now = System.currentTimeMillis();

        AxisAlignedBB box = player.getEntityBoundingBox().grow(48.0);
        List<EntityNPC> npcs = mc.world.getEntitiesWithinAABB(EntityNPC.class, box);

        for (EntityNPC npc : npcs) {
            String pid = npc.getProfileId();
            BotProfile profile = ProfileManager.get(pid);
            if (profile == null) continue;

            Long next = nextSpontaneous.get(pid);
            if (next == null) {
                nextSpontaneous.put(pid, now + randomInterval());
            } else if (now >= next && !GeminiClient.isBusy() && ModConfig.spontaneousMinMin < 500) {
                ChatHandler.sendSpontaneous(profile, npc);
                nextSpontaneous.put(pid, now + randomInterval());
            }

            if (ModConfig.alertsEnabled) {
                Long cd = alertCooldown.get(pid);
                if (cd == null || now >= cd) {
                    String alert = ContextBuilder.detectAlert(player);
                    if (alert != null && !GeminiClient.isBusy()) {
                        ChatHandler.sendAlert(profile, npc, alert);
                        alertCooldown.put(pid, now + 45_000L);
                    }
                }
            }
        }
    }

    private static long randomInterval() {
        int min = Math.max(1, ModConfig.spontaneousMinMin);
        int max = Math.max(min + 1, ModConfig.spontaneousMaxMin);
        int minutes = min + RNG.nextInt(max - min + 1);
        return minutes * 60_000L;
    }

    public static void resetSpontaneous(String profileId) {
        nextSpontaneous.put(profileId, System.currentTimeMillis() + randomInterval());
    }

    @SubscribeEvent
    public static void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int y = 6;

        if (ChatHandler.chatModeBot != null) {
            BotProfile p = ProfileManager.get(ChatHandler.chatModeBot);
            String name = p != null ? p.name : ChatHandler.chatModeBot;
            String label = "\u00a7d\u2709 " + name + " \u00a77(/botchat off)";
            int w = mc.fontRenderer.getStringWidth(label.replaceAll("\u00a7.", ""));
            mc.fontRenderer.drawStringWithShadow(label, sr.getScaledWidth() - w - 6, y, 0xFFFFFF);
            y += 12;
            if (GeminiClient.isBusy()) {
                mc.fontRenderer.drawStringWithShadow("\u00a77escribiendo...", sr.getScaledWidth() - 80, y, 0xAAAAAA);
                y += 12;
            }
        }

        // HUD tarea del NPC mas cercano
        AxisAlignedBB box = mc.player.getEntityBoundingBox().grow(32.0);
        List<EntityNPC> npcs = mc.world.getEntitiesWithinAABB(EntityNPC.class, box);
        EntityNPC closest = null;
        double best = Double.MAX_VALUE;
        for (EntityNPC n : npcs) {
            double d = mc.player.getDistanceSq(n);
            if (d < best) { best = d; closest = n; }
        }
        if (closest != null) {
            String line = "\u00a7d" + closest.getName() + "\u00a77: " + closest.getStatusLine()
                    + " \u00a78HP " + (int) closest.getHealth() + "/" + (int) closest.getMaxHealth();
            mc.fontRenderer.drawStringWithShadow(line, 6, sr.getScaledHeight() - 20, 0xFFFFFF);
        }
    }
}
