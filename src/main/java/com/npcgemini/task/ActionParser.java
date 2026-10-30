package com.npcgemini.task;

import com.npcgemini.util.ItemNames;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta órdenes en lenguaje natural. Usa coincidencia por palabra completa
 * (antes "ven" coincidía dentro de "convencido" y "mina" dentro de "camina").
 */
public class ActionParser {

    public enum Type {
        NONE, FOLLOW, STOP, COME, TP, MINE, CHOP, REMEMBER, GOTO,
        INVENTORY, DELIVER, DELIVER_ITEM, COMBAT_ON, COMBAT_OFF, HOME_SET, HOME_GO, WAYPOINTS, OPEN_INV, EXPLORE, PICKUP
    }

    public static class Result {
        public Type type = Type.NONE;
        public Block[] blocks;          // materiales a minar
        public String material;         // nombre dicho por el jugador
        public String waypoint;
        public String raw;
        public String itemName;         // texto para ItemQuery (dame ...)
        public int count = -1;          // cantidad pedida (-1 = no dicha)
        public boolean treesCount = false; // "2 arboles" en vez de "10 troncos"
        public boolean fetch = false;   // "traeme": recolectar y entregar
        public boolean lookedAt = false;// "rompe ese bloque"
    }

    private static final Map<String, Block[]> MATERIALS = new LinkedHashMap<>();
    static {
        mat("carbon", Blocks.COAL_ORE); mat("coal", Blocks.COAL_ORE);
        mat("hierro", Blocks.IRON_ORE); mat("iron", Blocks.IRON_ORE);
        mat("oro", Blocks.GOLD_ORE); mat("gold", Blocks.GOLD_ORE);
        mat("diamante", Blocks.DIAMOND_ORE); mat("diamantes", Blocks.DIAMOND_ORE); mat("diamond", Blocks.DIAMOND_ORE);
        mat("redstone", Blocks.REDSTONE_ORE, Blocks.LIT_REDSTONE_ORE);
        mat("lapis", Blocks.LAPIS_ORE);
        mat("esmeralda", Blocks.EMERALD_ORE); mat("esmeraldas", Blocks.EMERALD_ORE); mat("emerald", Blocks.EMERALD_ORE);
        mat("cobblestone", Blocks.COBBLESTONE); mat("adoquin", Blocks.COBBLESTONE); mat("adoquines", Blocks.COBBLESTONE); mat("cobble", Blocks.COBBLESTONE);
        mat("piedra", Blocks.STONE); mat("stone", Blocks.STONE);
        mat("tierra", Blocks.DIRT, Blocks.GRASS); mat("dirt", Blocks.DIRT, Blocks.GRASS);
        mat("arena", Blocks.SAND); mat("sand", Blocks.SAND);
        mat("grava", Blocks.GRAVEL); mat("gravel", Blocks.GRAVEL);
        mat("obsidiana", Blocks.OBSIDIAN); mat("obsidian", Blocks.OBSIDIAN);
        mat("arcilla", Blocks.CLAY);
    }
    private static void mat(String k, Block... b) { MATERIALS.put(k, b); }

    private static final Map<String, Integer> NUMBER_WORDS = new HashMap<>();
    static {
        String[] w = {"un", "una", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
                "once", "doce", "docena", "quince", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta"};
        int[] v = {1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 12, 15, 20, 30, 40, 50, 60};
        for (int i = 0; i < w.length; i++) NUMBER_WORDS.put(w[i], v[i]);
    }

    public static Result parse(String text) { return parse(text, null); }

    public static Result parse(String text, String botName) {
        Result r = new Result();
        if (text == null) return r;
        r.raw = text;
        String t = ItemNames.normalize(text);
        // Quitar el nombre del bot al inicio ("luna tala un arbol")
        if (botName != null) {
            String n = ItemNames.normalize(botName);
            if (!n.isEmpty() && t.startsWith(n + " ")) t = t.substring(n.length()).trim();
        }
        if (t.isEmpty()) return r;

        // ---- Detener
        if (t.matches("^(detente|detenerte|stop|alto|quieto|quieta|cancela|cancelar|basta|espera)( .*)?$|^para( ya| de .*)?$")
                || has(t, "detente", "cancela", "cancelar", "deja eso", "deja de hacer eso", "ya basta", "para ya")) {
            r.type = Type.STOP; return r;
        }

        // ---- Entregar todo / objeto
        if (has(t, "dame todo", "dame todos", "dame todas", "entregame todo", "devuelveme todo", "dame lo que tienes", "dame lo que llevas",
                "vacia tu inventario", "vacia inventario", "give me all", "pasame todo")) {
            r.type = Type.DELIVER; return r;
        }
        if (t.matches("^(dame|damela|entregame|pasame|devuelveme|give me)\\b.*")) {
            r.type = Type.DELIVER_ITEM;
            r.itemName = t.replaceFirst("^(dame|damela|entregame|pasame|devuelveme|give me)\\s*", "");
            r.count = findNumber(t);
            return r;
        }

        // ---- Inventario
        if (has(t, "abre inventario", "abre tu inventario", "abrir inventario", "open inventory", "muestrame tu inventario", "muestrame el inventario")) {
            r.type = Type.OPEN_INV; return r;
        }
        if (has(t, "inventario", "inventory", "que llevas", "que tienes", "que cargas", "que traes", "que hay en tu mochila")
                || t.matches(".*\\bcuant[oa]s? .*\\b(llevas|tienes|cargas)\\b.*")) {
            r.type = Type.INVENTORY;
            r.itemName = t.replaceAll("\\b(cuantos|cuantas|cuanto|cuanta|llevas|tienes|cargas|inventario|que|hay|en|tu|de|el|la|los|las)\\b", " ").replaceAll("\\s+", " ").trim();
            r.count = findNumber(t);
            return r;
        }

        // ---- Combate / casa / sitios / movimiento
        if (has(t, "no pelees", "no ataques", "deja de pelear", "combat off", "modo pacifico")) { r.type = Type.COMBAT_OFF; return r; }
        if (has(t, "pelea", "ataca", "defiendeme", "combat on", "modo combate", "protegeme", "protege")) { r.type = Type.COMBAT_ON; return r; }
        if (has(t, "esta es tu casa", "aqui es tu casa", "aqui es casa", "set home", "pon casa", "guarda casa", "tu casa es aqui")) { r.type = Type.HOME_SET; return r; }
        if (has(t, "ve a casa", "anda a casa", "go home", "vuelve a casa", "regresa a casa", "ve a tu casa")) { r.type = Type.HOME_GO; return r; }
        if (has(t, "donde conoces", "lista sitios", "waypoints", "que sitios", "que lugares conoces", "sitios que conoces")) { r.type = Type.WAYPOINTS; return r; }
        if (has(t, "sigueme", "follow", "acompaname", "acompana", "ven conmigo", "siguem")) { r.type = Type.FOLLOW; return r; }
        if (has(t, "teletransportate", "teletransporta", "teleport", "tp", "tepea", "aparece aqui", "aparece")) { r.type = Type.TP; return r; }
        if (has(t, "ven aqui", "ven", "come here", "acercate", "ven para aca", "ven a mi", "vente")) { r.type = Type.COME; return r; }
        if (has(t, "explora", "explorar", "reconoce la zona", "explore")) { r.type = Type.EXPLORE; return r; }
        if (has(t, "recuerda", "marca aqui", "guarda este sitio", "waypoint", "memoriza")) {
            r.type = Type.REMEMBER;
            r.waypoint = extractWaypointName(t);
            if (r.waypoint == null) r.waypoint = "punto";
            return r;
        }

        // ---- Recoger objetos del suelo
        if (has(t, "recoge los objetos", "recoge los items", "recoge lo del suelo", "recoge lo que hay en el suelo", "recoge todo",
                "recoge los objetos del suelo", "junta los objetos", "recoge items", "recoge los drops", "pickup")) {
            r.type = Type.PICKUP; return r;
        }

        // ---- Talar
        boolean woodWord = has(t, "arbol", "arboles", "madera", "tronco", "troncos", "lena", "leno", "wood", "logs", "tree", "trees");
        boolean chopVerb = has(t, "tala", "talar", "talas", "corta", "cortar", "chop", "derriba", "derribar");
        boolean fetchVerb = has(t, "trae", "traeme", "traer", "busca", "buscar", "consigue", "conseguir", "necesito", "recoge", "recolecta", "recolectar", "junta", "consigueme", "getme", "get");
        if (chopVerb || (woodWord && fetchVerb)) {
            r.type = Type.CHOP;
            r.fetch = fetchVerb && !chopVerb || has(t, "traeme", "trae");
            r.count = findNumber(t);
            r.treesCount = has(t, "arbol", "arboles", "tree", "trees");
            return r;
        }

        // ---- Minar / recolectar bloques
        boolean mineVerb = has(t, "pica", "picar", "mina", "minar", "extrae", "extraer", "rompe", "romper", "excava", "cava", "cavar", "mine", "dig", "break");
        if (mineVerb || fetchVerb) {
            String key = findMaterialKey(t);
            if (key != null) {
                r.type = Type.MINE;
                r.blocks = MATERIALS.get(key);
                r.material = key;
                r.fetch = fetchVerb && !mineVerb;
                r.count = findNumber(t);
                return r;
            }
            if (mineVerb && has(t, "ese bloque", "este bloque", "ese mineral", "eso", "esto", "that block", "mina eso", "rompe eso")) {
                r.type = Type.MINE; r.lookedAt = true; r.count = 1; return r;
            }
        }

        // ---- Ir a un sitio recordado
        if (has(t, "ve", "anda", "vaya", "goto", "go to", "ve al", "ve a la") && !t.contains("casa")) {
            String w = extractWaypointName(t);
            if (w != null) { r.type = Type.GOTO; r.waypoint = w; return r; }
        }
        return r;
    }

    // ---------------------------------------------------------------- helpers

    /** Coincidencia por palabra completa (frases incluidas) en texto ya normalizado. */
    private static boolean has(String t, String... keys) {
        for (String k : keys) {
            if (Pattern.compile("(^|\\s)" + Pattern.quote(k) + "(\\s|$)").matcher(t).find()) return true;
        }
        return false;
    }

    private static String findMaterialKey(String t) {
        for (String k : MATERIALS.keySet()) if (has(t, k)) return k;
        return null;
    }

    /** Primer número en dígitos o palabra ("cinco"). -1 si no hay. */
    public static int findNumber(String normalized) {
        Matcher m = Pattern.compile("\\b(\\d{1,3})\\b").matcher(normalized);
        if (m.find()) {
            try { return Math.max(1, Math.min(2304, Integer.parseInt(m.group(1)))); } catch (NumberFormatException ignored) { }
        }
        for (String w : normalized.split(" ")) {
            Integer v = NUMBER_WORDS.get(w);
            if (v != null) return v;
        }
        if (has(normalized, "stack", "pila")) return 64;
        return -1;
    }

    private static String extractWaypointName(String t) {
        String[] markers = {" como ", " llamado ", " llama ", " a la ", " al ", " a el ", " a "};
        for (String m : markers) {
            int i = t.lastIndexOf(m);
            if (i >= 0) {
                String rest = t.substring(i + m.length()).trim();
                if (!rest.isEmpty()) return rest.split("\\s+")[0];
            }
        }
        return null;
    }

    public static String blockName(Block b) {
        if (b == null) return "bloque";
        if (b == Blocks.COAL_ORE) return "carbón";
        if (b == Blocks.IRON_ORE) return "hierro";
        if (b == Blocks.GOLD_ORE) return "oro";
        if (b == Blocks.DIAMOND_ORE) return "diamante";
        if (b == Blocks.LOG || b == Blocks.LOG2) return "madera";
        return b.getLocalizedName();
    }
}
