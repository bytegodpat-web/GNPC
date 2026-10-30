package com.npcgemini.task;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ActionParser {

    public enum Type {
        NONE, FOLLOW, STOP, COME, TP, MINE, CHOP, REMEMBER, GOTO,
        INVENTORY, DELIVER, COMBAT_ON, COMBAT_OFF, HOME_SET, HOME_GO, WAYPOINTS, OPEN_INV
    }

    public static class Result {
        public Type type = Type.NONE;
        public Block block;
        public String waypoint;
        public String raw;
    }

    private static final Map<String, Block> ORES = new HashMap<>();
    static {
        ORES.put("carbon", Blocks.COAL_ORE);
        ORES.put("carbón", Blocks.COAL_ORE);
        ORES.put("coal", Blocks.COAL_ORE);
        ORES.put("hierro", Blocks.IRON_ORE);
        ORES.put("iron", Blocks.IRON_ORE);
        ORES.put("oro", Blocks.GOLD_ORE);
        ORES.put("gold", Blocks.GOLD_ORE);
        ORES.put("diamante", Blocks.DIAMOND_ORE);
        ORES.put("diamond", Blocks.DIAMOND_ORE);
        ORES.put("redstone", Blocks.REDSTONE_ORE);
        ORES.put("lapis", Blocks.LAPIS_ORE);
        ORES.put("esmeralda", Blocks.EMERALD_ORE);
        ORES.put("emerald", Blocks.EMERALD_ORE);
        ORES.put("piedra", Blocks.STONE);
        ORES.put("stone", Blocks.STONE);
        ORES.put("tierra", Blocks.DIRT);
        ORES.put("dirt", Blocks.DIRT);
        ORES.put("cobble", Blocks.COBBLESTONE);
        ORES.put("obsidiana", Blocks.OBSIDIAN);
    }

    public static Result parse(String text) {
        Result r = new Result();
        if (text == null) return r;
        r.raw = text;
        String t = text.toLowerCase(Locale.ROOT).trim();

        if (containsAny(t, "para", "stop", "detente", "quieto", "cancela", "cancelar")) {
            r.type = Type.STOP; return r;
        }
        if (containsAny(t, "abre inventario", "abrir inventario", "open inventory", "muestra inventario grafico")) {
            r.type = Type.OPEN_INV; return r;
        }
        if (containsAny(t, "inventario", "inventory", "que tienes", "qué tienes", "muestra items")) {
            r.type = Type.INVENTORY; return r;
        }
        if (containsAny(t, "dame todo", "entregame", "entrégame", "devuelveme", "devuélveme",
                "dame lo que tienes", "vacia inventario", "vacía inventario", "give me all")) {
            r.type = Type.DELIVER; return r;
        }
        if (containsAny(t, "no pelees", "no ataques", "deja de pelear", "combat off", "modo pacifico", "modo pacífico")) {
            r.type = Type.COMBAT_OFF; return r;
        }
        if (containsAny(t, "pelea", "ataca", "defiendeme", "defiéndeme", "combat on", "modo combate", "protege")) {
            r.type = Type.COMBAT_ON; return r;
        }
        if (containsAny(t, "esta es tu casa", "aquí es casa", "aqui es casa", "set home", "pon casa", "guarda casa")) {
            r.type = Type.HOME_SET; return r;
        }
        if (containsAny(t, "ve a casa", "anda a casa", "go home", "vuelve a casa")) {
            r.type = Type.HOME_GO; return r;
        }
        if (containsAny(t, "donde conoces", "dónde conoces", "lista sitios", "waypoints", "que sitios", "qué sitios")) {
            r.type = Type.WAYPOINTS; return r;
        }
        if (containsAny(t, "sigueme", "sígueme", "follow", "acompaname", "acompaña", "ven conmigo")) {
            r.type = Type.FOLLOW; return r;
        }
        if (containsAny(t, "teletransport", "teleport", "tp ", " tp", "ven ya", "aparece")) {
            r.type = Type.TP; return r;
        }
        if (containsAny(t, "ven aqui", "ven aquí", "ven", "come here", "acercate", "acércate", "ven para aca", "ven para acá")) {
            if (!t.contains("conmigo")) { r.type = Type.COME; return r; }
        }
        if (containsAny(t, "recuerda", "marca aqui", "marca aquí", "guarda este sitio", "waypoint")) {
            r.type = Type.REMEMBER;
            r.waypoint = extractWaypointName(t);
            if (r.waypoint == null) r.waypoint = "punto";
            return r;
        }
        if (t.matches(".*\\b(ve|anda|vaya|goto|go to)\\b.*") && !t.contains("casa")) {
            r.type = Type.GOTO;
            r.waypoint = extractWaypointName(t);
            return r;
        }
        if (containsAny(t, "tala", "corta arbol", "corta árbol", "madera", "chop", "wood", "arbol", "árbol", "tronco")) {
            if (containsAny(t, "trae", "busca", "consigue", "tala", "corta", "chop", "necesito madera", "dame madera")) {
                r.type = Type.CHOP; return r;
            }
        }
        if (containsAny(t, "pica", "mina", "rompe", "trae", "busca", "consigue", "extrae")) {
            Block b = findBlock(t);
            if (b != null) { r.type = Type.MINE; r.block = b; return r; }
            if (containsAny(t, "pica", "rompe", "mina eso", "mina esto", "ese bloque", "ese mineral")) {
                r.type = Type.MINE; r.block = null; return r;
            }
        }
        return r;
    }

    private static Block findBlock(String t) {
        for (Map.Entry<String, Block> e : ORES.entrySet()) {
            if (t.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static String extractWaypointName(String t) {
        String[] markers = {"como ", "llamado ", "llama ", "a la ", "al ", "a el ", "a "};
        for (String m : markers) {
            int i = t.lastIndexOf(m);
            if (i >= 0) {
                String rest = t.substring(i + m.length()).trim();
                rest = rest.replaceAll("[^a-zA-Záéíóúñ0-9_ ]", "").trim();
                if (!rest.isEmpty()) return rest.split("\\s+")[0].toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static boolean containsAny(String t, String... keys) {
        for (String k : keys) if (t.contains(k)) return true;
        return false;
    }

    public static String blockName(Block b) {
        if (b == null) return "bloque";
        if (b == Blocks.COAL_ORE) return "carbon";
        if (b == Blocks.IRON_ORE) return "hierro";
        if (b == Blocks.GOLD_ORE) return "oro";
        if (b == Blocks.DIAMOND_ORE) return "diamante";
        if (b == Blocks.LOG || b == Blocks.LOG2) return "madera";
        return b.getLocalizedName();
    }
}
