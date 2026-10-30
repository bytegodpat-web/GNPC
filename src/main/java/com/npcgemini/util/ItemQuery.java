package com.npcgemini.util;

import net.minecraft.item.ItemStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta peticiones como "20 troncos", "hierro", "la espada" y decide qué stacks coinciden.
 * amount == -1 significa "todo lo que coincida".
 */
public class ItemQuery {

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "dame", "damela", "dámela", "traeme", "entregame", "devuelveme", "dame", "de", "del", "el", "la", "los", "las",
            "un", "una", "unos", "unas", "mi", "me", "por", "favor", "todo", "todos", "toda", "todas", "unidades",
            "unidad", "items", "item", "objetos", "objeto", "bloques", "bloque", "que", "tengas", "tienes", "y", "pls"));

    private static final Map<String, String[]> SYN = new HashMap<>();
    static {
        syn("madera", "log", "wood"); syn("tronco", "log"); syn("troncos", "log"); syn("leno", "log"); syn("lena", "log");
        syn("tablon", "planks"); syn("tablones", "planks"); syn("tablas", "planks");
        syn("piedra", "stone", "cobblestone"); syn("adoquin", "cobblestone"); syn("adoquines", "cobblestone");
        syn("carbon", "coal"); syn("hierro", "iron"); syn("oro", "gold"); syn("diamante", "diamond"); syn("diamantes", "diamond");
        syn("palo", "stick"); syn("palos", "stick"); syn("espada", "sword"); syn("pico", "pickaxe"); syn("picos", "pickaxe");
        syn("pala", "shovel"); syn("hacha", "axe"); syn("tierra", "dirt"); syn("arena", "sand"); syn("antorcha", "torch");
        syn("antorchas", "torch"); syn("cubeta", "bucket"); syn("cubo", "bucket"); syn("manzana", "apple"); syn("manzanas", "apple");
        syn("pan", "bread"); syn("lana", "wool"); syn("semilla", "seeds"); syn("semillas", "seeds"); syn("esmeralda", "emerald");
        syn("esmeraldas", "emerald"); syn("lapis", "lapis"); syn("redstone", "redstone"); syn("cuero", "leather");
        syn("flecha", "arrow"); syn("flechas", "arrow"); syn("arco", "bow"); syn("carne", "beef", "porkchop", "chicken", "mutton");
    }
    private static void syn(String k, String... v) { SYN.put(k, v); }

    public final int amount;           // -1 = todo
    public final List<String> tokens;  // tokens significativos normalizados

    private ItemQuery(int amount, List<String> tokens) { this.amount = amount; this.tokens = tokens; }

    public boolean isEmpty() { return tokens.isEmpty(); }

    public static ItemQuery parse(String raw) {
        String t = ItemNames.normalize(raw);
        int amount = -1;
        Matcher m = Pattern.compile("\\b(\\d{1,4})\\b").matcher(t);
        if (m.find()) {
            try { amount = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) { }
            t = t.replace(m.group(1), " ");
        }
        List<String> tokens = new ArrayList<>();
        for (String w : t.split(" ")) {
            if (w.isEmpty() || STOP.contains(w)) continue;
            tokens.add(w);
        }
        return new ItemQuery(amount, tokens);
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (tokens.isEmpty()) return true;
        String hay = haystack(stack);
        for (String tok : tokens) {
            boolean ok = hay.contains(tok);
            if (!ok && tok.length() > 3 && tok.endsWith("s")) ok = hay.contains(tok.substring(0, tok.length() - 1));
            if (!ok) {
                String[] alts = SYN.get(tok);
                if (alts == null && tok.endsWith("s")) alts = SYN.get(tok.substring(0, tok.length() - 1));
                if (alts != null) for (String a : alts) if (hay.contains(a)) { ok = true; break; }
            }
            if (!ok) return false;
        }
        return true;
    }

    private static String haystack(ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        sb.append(ItemNames.normalize(ItemNames.name(stack))).append(' ');
        sb.append(ItemNames.normalize(stack.getDisplayName())).append(' ');
        if (stack.getItem().getRegistryName() != null) {
            String full = stack.getItem().getRegistryName().toString();   // minecraft:iron_ingot
            sb.append(ItemNames.normalize(full.substring(full.indexOf(':') + 1).replace('_', ' ')));
        }
        return sb.toString();
    }
}
