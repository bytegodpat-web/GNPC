package com.npcgemini.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.npcgemini.NPCGeminiMod;
import com.npcgemini.config.ModConfig;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona los perfiles de bots en disco (config/npcgemini/profiles/).
 */
public class ProfileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, BotProfile> CACHE = new ConcurrentHashMap<>();
    private static File profilesDir;
    private static File configRoot;

    /**
     * Llamar desde preInit/init con el directorio de config del mod
     * (event.getModConfigurationDirectory() o equivalente).
     */
    public static void init(File modConfigDir) {
        if (modConfigDir == null) {
            modConfigDir = new File("config");
        }
        configRoot = new File(modConfigDir, NPCGeminiMod.MODID);
        if (!configRoot.exists()) {
            configRoot.mkdirs();
        }
        profilesDir = new File(configRoot, "profiles");
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
        File skinsDir = new File(configRoot, "skins");
        if (!skinsDir.exists()) {
            skinsDir.mkdirs();
        }
        loadAll();
        if (CACHE.isEmpty()) {
            BotProfile def = new BotProfile("luna");
            def.name = "Luna";
            def.role = "una companera exploradora del mundo de Minecraft";
            def.personality = "amigable, curiosa y un poco bromista";
            def.attitude = "te considera un amigo de confianza";
            def.speechStyle = "informal y cercano";
            def.language = "espanol";
            def.responseLength = "2";
            def.skinId = "luna";
            def.slimArms = true;
            def.capturesEnabled = ModConfig.capturesEnabledDefault;
            save(def);
            CACHE.put(def.id, def);
        }
    }

    /** Compatibilidad: si alguien llama init() sin args */
    public static void init() {
        if (configRoot != null) return;
        // Fallback: carpeta config relativa al working dir del juego
        init(new File("config"));
    }

    public static File getConfigRoot() {
        return configRoot != null ? configRoot : new File("config/" + NPCGeminiMod.MODID);
    }

    public static void loadAll() {
        CACHE.clear();
        if (profilesDir == null || !profilesDir.exists()) return;
        File[] files = profilesDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        for (File f : files) {
            try (FileReader r = new FileReader(f)) {
                BotProfile p = GSON.fromJson(r, BotProfile.class);
                if (p != null && p.id != null) {
                    CACHE.put(p.id.toLowerCase(), p);
                }
            } catch (Exception e) {
                NPCGeminiMod.logger.warn("No se pudo cargar perfil " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    public static BotProfile get(String id) {
        if (id == null) return null;
        return CACHE.get(id.toLowerCase());
    }

    public static BotProfile getOrCreate(String id) {
        BotProfile p = get(id);
        if (p != null) return p;
        p = new BotProfile(id.toLowerCase());
        p.capturesEnabled = ModConfig.capturesEnabledDefault;
        save(p);
        CACHE.put(p.id, p);
        return p;
    }

    public static void save(BotProfile profile) {
        if (profile == null || profile.id == null) return;
        if (profilesDir == null) return;
        File f = new File(profilesDir, profile.id.toLowerCase() + ".json");
        try (FileWriter w = new FileWriter(f)) {
            GSON.toJson(profile, w);
        } catch (Exception e) {
            NPCGeminiMod.logger.error("Error guardando perfil " + profile.id + ": " + e.getMessage());
        }
        CACHE.put(profile.id.toLowerCase(), profile);
    }

    public static Collection<BotProfile> all() {
        return CACHE.values();
    }

    public static boolean exists(String id) {
        return id != null && CACHE.containsKey(id.toLowerCase());
    }
}
