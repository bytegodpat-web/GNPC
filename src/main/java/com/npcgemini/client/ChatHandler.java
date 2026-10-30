package com.npcgemini.client;

import com.npcgemini.NPCGeminiMod;
import com.npcgemini.config.ModConfig;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.gemini.GeminiClient;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
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

        if (GeminiClient.isBusy()) {
            player.sendMessage(new TextComponentString("§7" + profile.name + " está escribiendo..."));
            return;
        }

        // Las órdenes de movimiento básicas se ejecutan localmente en el servidor integrado.
        EntityNPC nearNpc = findNearbyNpc(player, botId, 16.0);
        ActionParser.Result action = ActionParser.parse(text);
        if (nearNpc != null) {
            EntityNPC serverNpc = resolveServerNpc(nearNpc);
            if (serverNpc != null) {
                String confirmation = null;
                if (action.type == ActionParser.Type.FOLLOW || action.type == ActionParser.Type.COME || action.type == ActionParser.Type.STOP) {
                    int order = action.type == ActionParser.Type.FOLLOW ? EntityNPC.ORDER_FOLLOW
                            : action.type == ActionParser.Type.COME ? EntityNPC.ORDER_COME : EntityNPC.ORDER_IDLE;
                    serverNpc.setMovementOrder(player.getUniqueID(), order);
                    confirmation = action.type == ActionParser.Type.FOLLOW ? "Voy contigo."
                            : action.type == ActionParser.Type.COME ? "Voy hacia ti." : "Me detengo.";
                } else if (action.type == ActionParser.Type.DELIVER) {
                    int n = serverNpc.deliverAllTo(player);
                    confirmation = "Te entregué " + n + " stacks.";
                } else if (action.type == ActionParser.Type.DELIVER_ITEM) {
                    net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(action.itemName.replace(' ', '_'));
                    int n = serverNpc.giveItemsTo(player, item, 64);
                    confirmation = item == null ? "No reconozco ese objeto." : "Te entregué " + n + " unidades.";
                } else if (action.type == ActionParser.Type.COMBAT_ON || action.type == ActionParser.Type.COMBAT_OFF) {
                    serverNpc.setCombatEnabled(action.type == ActionParser.Type.COMBAT_ON);
                    confirmation = action.type == ActionParser.Type.COMBAT_ON ? "Te defenderé." : "No atacaré.";
                } else if (action.type == ActionParser.Type.HOME_SET) {
                    serverNpc.setHomePosition(serverNpc.getPosition()); confirmation = "Recordaré este lugar como casa.";
                } else if (action.type == ActionParser.Type.HOME_GO && serverNpc.getHomePosition() != null) {
                    serverNpc.getNavigator().tryMoveToXYZ(serverNpc.getHomePosition().getX(), serverNpc.getHomePosition().getY(), serverNpc.getHomePosition().getZ(), 1.0D);
                    confirmation = "Voy a casa.";
                } else if (action.type == ActionParser.Type.REMEMBER) {
                    serverNpc.rememberPlace(action.waypoint, serverNpc.getPosition()); confirmation = "Recordaré este lugar como " + action.waypoint + ".";
                } else if (action.type == ActionParser.Type.GOTO) {
                    net.minecraft.util.math.BlockPos pos = serverNpc.getRememberedPlace(action.waypoint);
                    if (pos != null) { serverNpc.getNavigator().tryMoveToXYZ(pos.getX(), pos.getY(), pos.getZ(), 1.0D); confirmation = "Voy a " + action.waypoint + "."; }
                } else if (action.type == ActionParser.Type.MINE && action.block != null) {
                    int mined = serverNpc.workOnBlock(action.block, 12); confirmation = mined > 0 ? "He empezado a recoger " + ActionParser.blockName(action.block) + "." : "No veo ese bloque cerca.";
                } else if (action.type == ActionParser.Type.CHOP) {
                    int chopped = serverNpc.chopNearbyTree(12); confirmation = chopped > 0 ? "He empezado a talar." : "No veo troncos cerca.";
                }
                if (confirmation != null) {
                    player.sendMessage(new TextComponentString("§d[" + profile.name + "] §f" + confirmation));
                    if (ModConfig.bubbleEnabled) nearNpc.showBubble(confirmation);
                    return;
                }
            }
        }
        boolean near = nearNpc != null;

        String context = ContextBuilder.build(player, near);

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

        String context = ContextBuilder.build(player, near);
        EntityNPC nearNpc = findNearbyNpc(player, botId, 16.0);
        doAsk(profile, text, context, jpeg, nearNpc);
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
