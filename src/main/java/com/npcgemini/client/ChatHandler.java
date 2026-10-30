package com.npcgemini.client;

import com.npcgemini.NPCGeminiMod;
import com.npcgemini.config.ModConfig;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.gemini.GeminiClient;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import com.npcgemini.server.NpcActions;
import com.npcgemini.task.ActionParser;
import com.npcgemini.util.ContextBuilder;
import com.npcgemini.util.ScreenshotHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NPCGeminiMod.MODID, value = Side.CLIENT)
public class ChatHandler {

    /** Nombre del bot con el que se está en modo conversación (null = off) */
    public static String chatModeBot = null;

    private static boolean pendingCapture = false;
    private static String pendingBotId = null;
    private static String pendingText = null;
    private static boolean pendingNear = false;

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        if (chatModeBot == null) return;
        String msg = event.getMessage();
        if (msg == null) return;
        // No interceptar comandos
        if (msg.startsWith("/")) return;

        event.setCanceled(true);
        sendToBot(chatModeBot, msg, true);
    }

    /**
     * Envía un mensaje a un bot por nombre de perfil.
     */
    public static void sendToBot(String botId, String text, boolean tryCapture) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;

        BotProfile profile = ProfileManager.getOrCreate(botId);
        if (profile == null) {
            player.sendMessage(new TextComponentString("§cNo se encontró el perfil del bot."));
            return;
        }

        // Las órdenes (talar, minar, dame X, sígueme...) se ejecutan en el servidor y no pasan por Gemini.
        EntityNPC nearNpc = findNearbyNpc(player, botId, 16.0);
        ActionParser.Result action = ActionParser.parse(text, profile.name);
        if (action.type != ActionParser.Type.NONE) {
            EntityNPC target = nearNpc != null ? nearNpc : findNearbyNpc(player, botId, 64.0);
            if (target == null) {
                player.sendMessage(new TextComponentString("\u00a77" + profile.name
                        + " no est\u00e1 lo bastante cerca para obedecer (m\u00e1ximo 64 bloques)."));
                return;
            }
            if (!NpcActions.dispatch(target, action)) {
                player.sendMessage(new TextComponentString("\u00a7cLas \u00f3rdenes solo funcionan en un mundo de un jugador."));
            }
            return;
        }

        if (GeminiClient.isBusy()) {
            player.sendMessage(new TextComponentString("§7" + profile.name + " está escribiendo..."));
            return;
        }
        boolean near = nearNpc != null;

        String context = buildContext(player, near, nearNpc);

        if (tryCapture && near && profile.capturesEnabled && ModConfig.capturesEnabledDefault) {
            // Capturar en el siguiente tick de render (chat cerrado)
            pendingCapture = true;
            pendingBotId = botId;
            pendingText = text;
            pendingNear = near;
            player.sendMessage(new TextComponentString("§7" + profile.name + " está escribiendo..."));
            return;
        }

        doAsk(profile, text, context, null, nearNpc);
    }

    private static void doAsk(BotProfile profile, String text, String context,
                              byte[] jpeg, EntityNPC nearNpc) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;

        ClientTicker.resetSpontaneous(profile.id);

        GeminiClient.ask(profile, text, context, jpeg,
                reply -> {
                    profile.addToHistory(text, reply);
                    ProfileManager.save(profile);

                    if (ModConfig.chatEnabled) {
                        player.sendMessage(new TextComponentString(
                                "§d[" + profile.name + "] §f" + reply));
                    }
                    if (nearNpc != null && ModConfig.bubbleEnabled) {
                        nearNpc.showBubble(reply);
                    } else if (nearNpc == null && ModConfig.bubbleEnabled) {
                        // Buscar de nuevo por si se cargó
                        EntityNPC found = findNearbyNpc(player, profile.id, 48.0);
                        if (found != null) found.showBubble(reply);
                    }
                },
                err -> player.sendMessage(new TextComponentString("§c[" + profile.name + "] " + err))
        );
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!pendingCapture) return;

        pendingCapture = false;
        String botId = pendingBotId;
        String text = pendingText;
        boolean near = pendingNear;
        pendingBotId = null;
        pendingText = null;

        BotProfile profile = ProfileManager.get(botId);
        if (profile == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;

        byte[] jpeg = null;
        try {
            jpeg = ScreenshotHelper.captureJpeg();
        } catch (Exception e) {
            NPCGeminiMod.logger.warn("Captura falló: " + e.getMessage());
        }

        EntityNPC nearNpc = findNearbyNpc(player, botId, 16.0);
        String context = buildContext(player, near, nearNpc);
        doAsk(profile, text, context, jpeg, nearNpc);
    }

    /** Contexto del mundo + estado real del NPC (inventario, tarea) para que Gemini no invente. */
    private static String buildContext(EntityPlayerSP player, boolean near, EntityNPC nearNpc) {
        String ctx = ContextBuilder.build(player, near);
        if (nearNpc != null) {
            EntityNPC sv = resolveServerNpc(nearNpc);
            if (sv != null) ctx = ctx + "\n" + NpcActions.stateBlock(sv);
        }
        return ctx;
    }

    public static EntityNPC findNearbyNpc(EntityPlayerSP player, String profileId, double range) {
        if (player == null || player.world == null) return null;
        AxisAlignedBB box = player.getEntityBoundingBox().grow(range);
        List<EntityNPC> list = player.world.getEntitiesWithinAABB(EntityNPC.class, box);
        EntityNPC best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityNPC npc : list) {
            if (profileId != null && !profileId.equalsIgnoreCase(npc.getProfileId())) continue;
            double d = player.getDistanceSq(npc);
            if (d < bestDist) {
                bestDist = d;
                best = npc;
            }
        }
        return best;
    }

    /** Busca en el servidor integrado la entidad que corresponde a la del cliente (mismo id). */
    public static EntityNPC resolveServerNpc(EntityNPC clientNpc) {
        if (clientNpc == null) return null;
        IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null) return null;
        WorldServer ws = server.getWorld(clientNpc.dimension);
        if (ws == null) return null;
        Entity e = ws.getEntityByID(clientNpc.getEntityId());
        return (e instanceof EntityNPC) ? (EntityNPC) e : null;
    }

    public static void sendSpontaneous(BotProfile profile, EntityNPC npc) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        boolean near = npc != null && mc.player.getDistance(npc) < 32;
        String context = ContextBuilder.build(mc.player, near) + "\n[EVENTO: espontaneo]";
        GeminiClient.ask(profile, "(pensamiento espontáneo)", context, null,
                reply -> {
                    profile.addToHistory("(espontáneo)", reply);
                    ProfileManager.save(profile);
                    if (ModConfig.chatEnabled) {
                        mc.player.sendMessage(new TextComponentString(
                                "§d[" + profile.name + "] §f" + reply));
                    }
                    if (npc != null && ModConfig.bubbleEnabled) {
                        npc.showBubble(reply);
                    }
                },
                err -> { /* silencio en espontáneos fallidos */ }
        );
    }

    public static void sendAlert(BotProfile profile, EntityNPC npc, String alertFact) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        boolean near = npc != null;
        String context = ContextBuilder.build(mc.player, near)
                + "\n[EVENTO: alerta] peligro=" + alertFact;
        GeminiClient.ask(profile, "(alerta de peligro)", context, null,
                reply -> {
                    if (ModConfig.chatEnabled) {
                        mc.player.sendMessage(new TextComponentString(
                                "§d[" + profile.name + "] §c" + reply));
                    }
                    if (npc != null && ModConfig.bubbleEnabled) {
                        npc.showBubble(reply);
                    }
                },
                err -> { }
        );
    }
}
