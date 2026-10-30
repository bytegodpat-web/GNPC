package com.npcgemini.server;

import com.npcgemini.client.ChatHandler;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.entity.NpcInventory;
import com.npcgemini.task.ActionParser;
import com.npcgemini.task.HarvestJob;
import com.npcgemini.task.PickupJob;
import com.npcgemini.task.TravelJob;
import com.npcgemini.util.BlockFinder;
import com.npcgemini.util.ItemNames;
import com.npcgemini.util.ItemQuery;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.Map;
import java.util.UUID;

/**
 * Ejecución de órdenes del jugador sobre un NPC. SIEMPRE se ejecuta en el hilo del servidor
 * (antes la interfaz modificaba la entidad desde el hilo del cliente, de ahí los objetos
 * que desaparecían o se duplicaban).
 */
public class NpcActions {

    /** Llamar desde el cliente: programa la acción en el hilo del servidor integrado. */
    public static boolean dispatch(EntityNPC clientNpc, ActionParser.Result action) {
        Minecraft mc = Minecraft.getMinecraft();
        IntegratedServer srv = mc.getIntegratedServer();
        if (srv == null || mc.player == null || clientNpc == null) return false;
        final int id = clientNpc.getEntityId();
        final int dim = clientNpc.dimension;
        final UUID pid = mc.player.getUniqueID();
        srv.addScheduledTask(() -> {
            WorldServer ws = srv.getWorld(dim);
            Entity e = ws == null ? null : ws.getEntityByID(id);
            EntityPlayerMP sp = srv.getPlayerList().getPlayerByUUID(pid);
            if (e instanceof EntityNPC && sp != null) execute((EntityNPC) e, sp, action);
        });
        return true;
    }

    /** Abre el inventario del NPC como contenedor vanilla (sincronizado por Minecraft). */
    public static void openInventory(EntityNPC clientNpc) {
        Minecraft mc = Minecraft.getMinecraft();
        IntegratedServer srv = mc.getIntegratedServer();
        if (srv == null || mc.player == null || clientNpc == null) return;
        final int id = clientNpc.getEntityId();
        final int dim = clientNpc.dimension;
        final UUID pid = mc.player.getUniqueID();
        srv.addScheduledTask(() -> {
            WorldServer ws = srv.getWorld(dim);
            Entity e = ws == null ? null : ws.getEntityByID(id);
            EntityPlayerMP sp = srv.getPlayerList().getPlayerByUUID(pid);
            if (e instanceof EntityNPC && sp != null) sp.displayGUIChest(new NpcInventory((EntityNPC) e));
        });
    }

    /** Estado verificable del NPC para el prompt de Gemini (lo que dice debe coincidir con esto). */
    public static String stateBlock(EntityNPC npc) {
        if (npc == null) return "";
        StringBuilder sb = new StringBuilder("[ESTADO: ");
        sb.append("vida=").append(String.format("%.0f/%.0f", npc.getHealth(), npc.getMaxHealth()));
        String job = npc.getJobDescription();
        sb.append(", tarea=").append(job == null ? "ninguna" : job);
        sb.append(", combate=").append(npc.isCombatEnabled() ? "si" : "no");
        sb.append(", ").append(npc.describeInventory().replace("Llevo: ", "inventario=").replace("No llevo nada.", "inventario=vacio"));
        sb.append("]");
        return sb.toString();
    }

    // ------------------------------------------------------------------ ejecución (hilo del servidor)

    public static void execute(EntityNPC npc, EntityPlayerMP player, ActionParser.Result a) {
        npc.setOwner(player.getUniqueID());
        switch (a.type) {
            case STOP: {
                String was = npc.cancelJob();
                npc.setMovementOrder(player.getUniqueID(), EntityNPC.ORDER_IDLE);
                npc.say(was != null ? "Cancelé la tarea (" + was + ") y me detengo." : "Me detengo.");
                break;
            }
            case FOLLOW:
                npc.setMovementOrder(player.getUniqueID(), EntityNPC.ORDER_FOLLOW);
                npc.say("Voy contigo.");
                break;
            case COME:
                npc.setMovementOrder(player.getUniqueID(), EntityNPC.ORDER_COME);
                npc.say("Voy hacia ti.");
                break;
            case TP: {
                if (teleportNear(npc, player)) npc.say("Listo, ya estoy a tu lado.");
                else npc.say("No encontré un lugar seguro a tu lado para aparecer.");
                break;
            }
            case DELIVER: {
                EntityNPC.Transfer t = npc.giveAll(player);
                if (t.total == 0 && !t.playerFull) npc.say("No llevo nada que darte.");
                else npc.say("Te entregué: " + t.describe() + "." + (t.playerFull ? " Tu inventario se llenó; me quedé con el resto." : ""));
                break;
            }
            case DELIVER_ITEM: {
                ItemQuery q = ItemQuery.parse(a.itemName == null ? "" : a.itemName);
                int amount = a.count > 0 ? a.count : q.amount;
                int have = npc.countMatching(q);
                if (have == 0) {
                    npc.say("No llevo " + (q.isEmpty() ? "nada" : "eso") + ". " + npc.describeInventory());
                    break;
                }
                EntityNPC.Transfer t = npc.giveMatching(player, q, amount);
                String msg = "Te entregué: " + t.describe() + ".";
                if (amount > 0 && t.total < amount && !t.playerFull) msg += " Solo tenía " + t.total + " de los " + amount + " que pediste.";
                if (t.playerFull) msg += " Tu inventario está lleno; el resto me lo quedo.";
                npc.say(msg);
                break;
            }
            case INVENTORY: {
                if (a.itemName != null && !a.itemName.isEmpty()) {
                    ItemQuery q = ItemQuery.parse(a.itemName);
                    if (!q.isEmpty()) {
                        int n = npc.countMatching(q);
                        npc.say(n == 0 ? "No llevo eso." : "De eso llevo " + n + " en total. " + npc.describeInventory());
                        break;
                    }
                }
                npc.say(npc.describeInventory());
                break;
            }
            case OPEN_INV:
                player.displayGUIChest(new NpcInventory(npc));
                break;
            case COMBAT_ON:
                npc.setCombatEnabled(true);
                npc.say("Te defenderé de los monstruos hostiles.");
                break;
            case COMBAT_OFF:
                npc.setCombatEnabled(false);
                npc.say("No atacaré a nadie.");
                break;
            case HOME_SET:
                npc.setHomePosition(player.getPosition());
                npc.say("Recordaré este lugar como mi casa.");
                break;
            case HOME_GO: {
                BlockPos home = npc.getHomePosition();
                if (home == null) { npc.say("Todavía no tengo casa. Dime «esta es tu casa» donde quieras que la fije."); break; }
                npc.startJob(new TravelJob(npc, home, "casa"));
                npc.say("Voy a casa.");
                break;
            }
            case REMEMBER:
                npc.rememberPlace(a.waypoint, player.getPosition());
                npc.say("Recordaré este lugar como «" + a.waypoint + "».");
                break;
            case GOTO: {
                BlockPos pos = npc.getRememberedPlace(a.waypoint);
                if (pos == null) {
                    npc.say("No conozco ningún sitio llamado «" + a.waypoint + "»." + knownPlaces(npc));
                    break;
                }
                npc.startJob(new TravelJob(npc, pos, a.waypoint));
                npc.say("Voy a " + a.waypoint + ".");
                break;
            }
            case WAYPOINTS:
                npc.say(npc.getRememberedPlaces().isEmpty() && npc.getHomePosition() == null
                        ? "No conozco ningún sitio todavía." : "Conozco:" + knownPlaces(npc).replace("Conozco:", ""));
                break;
            case EXPLORE:
                npc.say("Todavía no sé explorar por mi cuenta. Puedo talar, picar, recoger objetos, seguirte e ir a sitios que me enseñes.");
                break;
            case PICKUP: {
                PickupJob job = new PickupJob(npc);
                String err = job.preflight();
                if (err != null) { npc.say("No puedo recoger objetos: " + err + "."); break; }
                npc.startJob(job);
                npc.say("Voy a recoger lo que hay en el suelo.");
                break;
            }
            case CHOP: {
                int trees = 1, logs = 0;
                if (a.count > 0) { if (a.treesCount) trees = Math.min(a.count, 20); else logs = Math.min(a.count, 256); }
                HarvestJob job = HarvestJob.trees(npc, trees, logs, a.fetch);
                String err = job.preflight();
                if (err != null) { npc.say("No puedo talar: " + err + "."); break; }
                npc.startJob(job);
                npc.say(logs > 0 ? "Voy a talar hasta juntar " + logs + " troncos." : "Voy a talar " + (trees == 1 ? "un árbol." : trees + " árboles."));
                break;
            }
            case MINE: {
                HarvestJob job;
                String what;
                if (a.lookedAt) {
                    BlockPos looked = BlockFinder.lookingAt(player, 24);
                    if (looked == null || player.world.isAirBlock(looked)) { npc.say("No veo qué bloque quieres que rompa. Míralo y repite la orden."); break; }
                    job = HarvestJob.fixed(npc, looked);
                    what = "ese bloque";
                } else {
                    boolean common = a.blocks.length > 0 && (a.blocks[0] == Blocks.STONE || a.blocks[0] == Blocks.DIRT || a.blocks[0] == Blocks.SAND
                            || a.blocks[0] == Blocks.GRAVEL || a.blocks[0] == Blocks.COBBLESTONE || a.blocks[0] == Blocks.CLAY);
                    int amount = a.count > 0 ? Math.min(a.count, 128) : (common ? 8 : 5);
                    job = HarvestJob.ore(npc, a.blocks, a.material, amount, a.fetch);
                    what = a.material + " (" + amount + ")";
                }
                String err = job.preflight();
                if (err != null) { npc.say("No puedo hacerlo: " + err + "."); break; }
                npc.startJob(job);
                npc.say("Voy a picar " + what + ".");
                break;
            }
            default:
                break;
        }
    }

    private static String knownPlaces(EntityNPC npc) {
        StringBuilder sb = new StringBuilder(" Conozco:");
        boolean any = false;
        if (npc.getHomePosition() != null) { sb.append(" casa"); any = true; }
        for (Map.Entry<String, BlockPos> e : npc.getRememberedPlaces().entrySet()) { sb.append(any ? ", " : " ").append(e.getKey()); any = true; }
        return any ? sb.toString() + "." : " ningún sitio todavía.";
    }

    /** Coloca al NPC en una casilla segura junto al jugador. */
    public static boolean teleportNear(EntityNPC npc, EntityPlayer player) {
        BlockPos base = player.getPosition();
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos p = base.add(dx, dy, dz);
                    if (com.npcgemini.util.PathHelper.isStandable(player.world, p)) {
                        npc.cancelJob();
                        npc.getNavigator().clearPath();
                        npc.fallDistance = 0.0F;
                        npc.setPositionAndUpdate(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
