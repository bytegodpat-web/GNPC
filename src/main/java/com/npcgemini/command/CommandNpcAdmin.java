package com.npcgemini.command;

import com.npcgemini.client.ChatHandler;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.entity.NpcRegistryData;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import com.npcgemini.server.NpcActions;
import com.npcgemini.util.ItemNames;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.*;

/**
 * /npc ... : comandos propios para gestionar NPC (servidor).
 *   list | info <n> | inv <n> | tp <n> [x y z] | kill <n> | say <n> <msg> | spawn <perfil> |
 *   stop <n> | follow <n> | stay <n>
 * Teletransportar y eliminar conservan nombre, perfil e inventario. Funcionan también con NPC
 * cuyo chunk no está cargado (se carga el chunk usando el registro guardado en el mundo).
 */
public class CommandNpcAdmin extends CommandBase {

    private static final List<String> SUBS = Arrays.asList("list", "info", "inv", "tp", "kill", "say", "spawn", "stop", "follow", "stay");

    @Override public String getName() { return "npc"; }
    @Override public List<String> getAliases() { return Collections.singletonList("gnpc"); }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/npc <list|info|inv|tp|kill|say|spawn|stop|follow|stay> ...";
    }

    private static void msg(ICommandSender s, String text) { s.sendMessage(new TextComponentString(text)); }
    private static void err(ICommandSender s, String text) { msg(s, "\u00a7c" + text); }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) { help(sender); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list": list(server, sender); return;
            case "spawn": spawn(server, sender, args); return;
            default: break;
        }
        if (!SUBS.contains(sub)) { help(sender); return; }
        if (args.length < 2) { err(sender, "Falta el nombre del NPC. Uso: /npc " + sub + " <nombre>" + ("say".equals(sub) ? " <mensaje>" : "")); return; }

        EntityNPC npc = findNpc(server, sender, args[1]);
        if (npc == null) {
            String known = knownNames(server);
            err(sender, "No encuentro ningún NPC llamado «" + args[1] + "»." + (known.isEmpty() ? "" : " Conocidos: " + known));
            return;
        }
        EntityPlayer player = sender instanceof EntityPlayer ? (EntityPlayer) sender : null;

        switch (sub) {
            case "info": info(sender, npc); break;
            case "inv": msg(sender, "\u00a7d[" + npc.getName() + "] \u00a7f" + npc.describeInventory()); break;
            case "tp": tp(server, sender, player, npc, args); break;
            case "kill": {
                String name = npc.getName();
                npc.dismiss(player);
                msg(sender, "\u00a7eEliminaste a " + name + ". Su inventario " + (player != null ? "pasó a tu inventario (lo que no cupo cayó al suelo)." : "cayó al suelo.")
                        + " Su perfil se conserva (\u00a7f/npc spawn " + name + "\u00a7e para recrearla).");
                break;
            }
            case "say": {
                if (args.length < 3) { err(sender, "Uso: /npc say <nombre> <mensaje>"); return; }
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) { if (sb.length() > 0) sb.append(' '); sb.append(args[i]); }
                final String text = sb.toString();
                final String pid = npc.getProfileId();
                Minecraft.getMinecraft().addScheduledTask(() -> ChatHandler.sendToBot(pid, text, false));
                break;
            }
            case "stop": {
                String was = npc.cancelJob();
                npc.setMovementOrder(player != null ? player.getUniqueID() : null, EntityNPC.ORDER_IDLE);
                msg(sender, "\u00a7e" + npc.getName() + (was != null ? " canceló: " + was + "." : " está detenida."));
                break;
            }
            case "follow":
                if (player == null) { err(sender, "Solo un jugador puede dar esta orden."); return; }
                npc.setMovementOrder(player.getUniqueID(), EntityNPC.ORDER_FOLLOW);
                msg(sender, "\u00a7e" + npc.getName() + " te sigue.");
                break;
            case "stay":
                npc.cancelJob();
                npc.setMovementOrder(player != null ? player.getUniqueID() : null, EntityNPC.ORDER_IDLE);
                msg(sender, "\u00a7e" + npc.getName() + " se queda quieta.");
                break;
            default: help(sender);
        }
    }

    private void help(ICommandSender s) {
        msg(s, "\u00a7eComandos de GNPC:");
        msg(s, "\u00a77/npc list \u00a7f- lista todos los NPC y dónde están");
        msg(s, "\u00a77/npc info <nombre> \u00a7f- datos, tarea e inventario");
        msg(s, "\u00a77/npc inv <nombre> \u00a7f- inventario exacto");
        msg(s, "\u00a77/npc tp <nombre> [x y z] \u00a7f- teletransporta (a ti por defecto)");
        msg(s, "\u00a77/npc kill <nombre> \u00a7f- elimina (su inventario pasa a ti)");
        msg(s, "\u00a77/npc say <nombre> <mensaje> \u00a7f- le habla / le da una orden");
        msg(s, "\u00a77/npc spawn <perfil> \u00a7f- crea un NPC con ese perfil");
        msg(s, "\u00a77/npc stop|follow|stay <nombre>");
    }

    // ----------------------------------------------------------------- búsqueda

    private static boolean matches(EntityNPC n, String q) {
        String qn = ItemNames.normalize(q);
        if (qn.isEmpty()) return false;
        return ItemNames.normalize(n.getName()).equals(qn) || n.getProfileId().equalsIgnoreCase(q)
                || n.getUniqueID().toString().startsWith(q.toLowerCase(Locale.ROOT));
    }

    private static List<EntityNPC> loadedNpcs(MinecraftServer server) {
        List<EntityNPC> out = new ArrayList<>();
        for (WorldServer ws : server.worlds) {
            if (ws == null) continue;
            for (Entity e : new ArrayList<>(ws.loadedEntityList)) if (e instanceof EntityNPC && !e.isDead) out.add((EntityNPC) e);
        }
        return out;
    }

    static EntityNPC findNpc(MinecraftServer server, ICommandSender sender, String query) {
        List<EntityNPC> found = new ArrayList<>();
        for (EntityNPC n : loadedNpcs(server)) if (matches(n, query)) found.add(n);
        if (found.isEmpty()) {
            // No cargado: usar el registro del mundo y cargar su chunk
            NpcRegistryData reg = NpcRegistryData.get(sender.getEntityWorld());
            if (reg != null) {
                String qn = ItemNames.normalize(query);
                for (NpcRegistryData.Entry e : new ArrayList<>(reg.all().values())) {
                    boolean m = ItemNames.normalize(e.name).equals(qn) || (e.profileId != null && e.profileId.equalsIgnoreCase(query))
                            || e.uuid.toString().startsWith(query.toLowerCase(Locale.ROOT));
                    if (!m) continue;
                    WorldServer ws = server.getWorld(e.dim);
                    if (ws == null || e.pos == null) continue;
                    ws.getChunkProvider().provideChunk(e.pos.getX() >> 4, e.pos.getZ() >> 4);
                    Entity ent = ws.getEntityFromUuid(e.uuid);
                    if (ent instanceof EntityNPC && !ent.isDead) found.add((EntityNPC) ent);
                }
            }
        }
        if (found.isEmpty()) return null;
        Entity from = sender.getCommandSenderEntity();
        if (from == null || found.size() == 1) return found.get(0);
        EntityNPC best = null; double bd = Double.MAX_VALUE;
        for (EntityNPC n : found) {
            double d = n.world == from.world ? n.getDistanceSq(from) : Double.MAX_VALUE - 1;
            if (d < bd) { bd = d; best = n; }
        }
        return best;
    }

    private static String knownNames(MinecraftServer server) {
        Set<String> names = new LinkedHashSet<>();
        for (EntityNPC n : loadedNpcs(server)) names.add(n.getName());
        NpcRegistryData reg = NpcRegistryData.get(server.getWorld(0));
        if (reg != null) for (NpcRegistryData.Entry e : reg.all().values()) if (e.name != null && !e.name.isEmpty()) names.add(e.name);
        return String.join(", ", names);
    }

    // ----------------------------------------------------------------- subcomandos

    private void list(MinecraftServer server, ICommandSender sender) {
        List<EntityNPC> loaded = loadedNpcs(server);
        Set<UUID> loadedIds = new HashSet<>();
        for (EntityNPC n : loaded) { loadedIds.add(n.getUniqueID()); NpcRegistryData.touch(n); }
        NpcRegistryData reg = NpcRegistryData.get(server.getWorld(0));
        int count = 0;
        msg(sender, "\u00a7eNPC del mundo:");
        if (reg != null) {
            for (NpcRegistryData.Entry e : reg.all().values()) {
                boolean on = loadedIds.contains(e.uuid);
                msg(sender, "\u00a77- \u00a7d" + e.name + " \u00a77(" + e.profileId + ") dim " + e.dim + " en "
                        + e.pos.getX() + " " + e.pos.getY() + " " + e.pos.getZ() + (on ? " \u00a7a[cargado]" : " \u00a78[sin cargar]"));
                count++;
            }
        }
        if (count == 0) msg(sender, "\u00a77No hay ninguno. Usa /npc spawn <perfil> o el huevo de aparición.");
    }

    private void info(ICommandSender sender, EntityNPC npc) {
        BlockPos p = npc.getPosition();
        msg(sender, "\u00a7d" + npc.getName() + " \u00a77(perfil " + npc.getProfileId() + ", " + npc.getUniqueID().toString().substring(0, 8) + ")");
        msg(sender, "\u00a77Posición: \u00a7f" + p.getX() + " " + p.getY() + " " + p.getZ() + " \u00a77dim " + npc.dimension);
        msg(sender, "\u00a77Vida: \u00a7f" + String.format("%.0f/%.0f", npc.getHealth(), npc.getMaxHealth())
                + " \u00a77Combate: \u00a7f" + (npc.isCombatEnabled() ? "sí" : "no")
                + " \u00a77Tarea: \u00a7f" + (npc.getJobDescription() == null ? "ninguna" : npc.getJobDescription()));
        BlockPos h = npc.getHomePosition();
        msg(sender, "\u00a77Casa: \u00a7f" + (h == null ? "sin definir" : h.getX() + " " + h.getY() + " " + h.getZ())
                + " \u00a77Sitios: \u00a7f" + npc.getRememberedPlaces().size());
        msg(sender, "\u00a77" + npc.describeInventory());
    }

    private void tp(MinecraftServer server, ICommandSender sender, EntityPlayer player, EntityNPC npc, String[] args) throws CommandException {
        double x, y, z;
        int targetDim;
        if (args.length >= 5) {
            BlockPos bp = parseBlockPos(sender, args, 2, true);
            x = bp.getX() + 0.5; y = bp.getY(); z = bp.getZ() + 0.5;
            targetDim = sender.getEntityWorld().provider.getDimension();
        } else if (player != null) {
            x = player.posX; y = player.posY; z = player.posZ;
            targetDim = player.dimension;
        } else {
            err(sender, "Uso: /npc tp <nombre> <x> <y> <z>");
            return;
        }
        EntityNPC target = npc;
        npc.cancelJob();
        npc.getNavigator().clearPath();
        if (npc.dimension != targetDim) {
            Entity moved = npc.changeDimension(targetDim);   // conserva datos/inventario (copia NBT)
            if (!(moved instanceof EntityNPC)) { err(sender, "No pude llevarla a esa dimensión."); return; }
            target = (EntityNPC) moved;
        }
        target.fallDistance = 0.0F;
        target.setPositionAndUpdate(x, y, z);
        NpcRegistryData.touch(target);
        msg(sender, "\u00a7e" + target.getName() + " fue teletransportada a " + (int) x + " " + (int) y + " " + (int) z + ".");
    }

    private void spawn(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) { err(sender, "Uso: /npc spawn <perfil>"); return; }
        if (!(sender instanceof EntityPlayer)) { err(sender, "Solo un jugador puede usar /npc spawn."); return; }
        EntityPlayer player = (EntityPlayer) sender;
        String id = args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
        if (id.isEmpty()) { err(sender, "Nombre de perfil no válido."); return; }
        BotProfile prof = ProfileManager.getOrCreate(id);
        EntityNPC n = new EntityNPC(player.world);
        n.setLocationAndAngles(player.posX, player.posY, player.posZ, player.rotationYaw, 0.0F);
        n.setProfileId(prof.id);
        n.setOwner(player.getUniqueID());
        player.world.spawnEntity(n);
        NpcRegistryData.touch(n);
        msg(sender, "\u00a7eCreaste a \u00a7d" + n.getName() + "\u00a7e (perfil " + prof.id + ").");
    }

    // ----------------------------------------------------------------- tab

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, SUBS);
        if (args.length == 2) {
            if ("spawn".equals(args[0])) {
                List<String> ids = new ArrayList<>();
                for (BotProfile p : ProfileManager.all()) ids.add(p.id);
                return getListOfStringsMatchingLastWord(args, ids);
            }
            List<String> names = new ArrayList<>();
            for (EntityNPC n : loadedNpcs(server)) names.add(n.getName());
            NpcRegistryData reg = NpcRegistryData.get(server.getWorld(0));
            if (reg != null) for (NpcRegistryData.Entry e : reg.all().values()) if (e.name != null && !names.contains(e.name)) names.add(e.name);
            return getListOfStringsMatchingLastWord(args, names);
        }
        return Collections.emptyList();
    }
}
