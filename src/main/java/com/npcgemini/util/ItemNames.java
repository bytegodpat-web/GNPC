package com.npcgemini.util;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import java.text.Normalizer;
import java.util.Locale;

/** Nombres legibles (en el idioma del cliente) y normalización de texto. */
public class ItemNames {

    public static String name(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "nada";
        if (stack.hasDisplayName()) return stack.getDisplayName();
        try {
            String key = stack.getUnlocalizedName() + ".name";
            if (I18n.hasKey(key)) return I18n.format(key);
        } catch (Throwable ignored) { }
        return stack.getDisplayName();
    }

    /** minúsculas, sin acentos, solo letras/números/espacios. */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}", "");
        n = n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return n;
    }
}
