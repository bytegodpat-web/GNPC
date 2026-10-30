package com.npcgemini.config;

import com.npcgemini.NPCGeminiMod;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Configuración global del cliente (API key, modelo, límites, etc.).
 * Nunca se guarda en el NBT de la entidad ni en el mundo.
 */
public class ModConfig {

    private static Configuration config;

    public static String apiKey = "";
    public static String model = "gemini-2.0-flash";
    public static int maxCaptureSize = 1024;
    public static int dailyRequestLimit = 200;
    public static int minRequestCooldownSec = 3;
    public static int spontaneousMinMin = 6;
    public static int spontaneousMaxMin = 99;
    public static boolean alertsEnabled = true;
    public static boolean bubbleEnabled = true;
    public static boolean chatEnabled = true;
    public static boolean capturesEnabledDefault = true;
    public static double bubbleViewRange = 32.0;
    public static boolean privacyAccepted = false;

    // Contadores de sesión (no se persisten entre reinicios del juego)
    public static int requestsToday = 0;
    public static long lastRequestMs = 0L;
    public static String lastRequestDay = "";

    public static void init(File configDir) {
        File cfgFile = new File(configDir, NPCGeminiMod.MODID + ".cfg");
        config = new Configuration(cfgFile);
        load();
    }

    public static void load() {
        config.load();

        apiKey = config.getString("apiKey", "general", "",
                "API key de Google AI Studio (https://aistudio.google.com/apikey). Nunca se comparte.");
        model = config.getString("model", "general", "gemini-2.0-flash",
                "Modelo de Gemini a usar. Cambia si Google retira el actual.");
        maxCaptureSize = config.getInt("maxCaptureSize", "general", 1024, 256, 2048,
                "Lado largo máximo de la captura en píxeles.");
        dailyRequestLimit = config.getInt("dailyRequestLimit", "general", 200, 10, 10000,
                "Tope diario de peticiones a la API.");
        minRequestCooldownSec = config.getInt("minRequestCooldownSec", "general", 3, 1, 60,
                "Segundos mínimos entre peticiones.");
        spontaneousMinMin = config.getInt("spontaneousMinMin", "general", 6, 1, 120,
                "Minutos mínimos del temporizador espontáneo.");
        spontaneousMaxMin = config.getInt("spontaneousMaxMin", "general", 99, 2, 300,
                "Minutos máximos del temporizador espontáneo.");
        alertsEnabled = config.getBoolean("alertsEnabled", "general", true,
                "Detectar peligros (creeper cerca, vida baja) y avisar en personaje.");
        bubbleEnabled = config.getBoolean("bubbleEnabled", "general", true,
                "Mostrar burbuja de texto sobre la cabeza del NPC.");
        chatEnabled = config.getBoolean("chatEnabled", "general", true,
                "Mostrar respuestas en el chat.");
        capturesEnabledDefault = config.getBoolean("capturesEnabledDefault", "general", true,
                "Valor por defecto de capturas al crear un perfil nuevo.");
        bubbleViewRange = config.getFloat("bubbleViewRange", "general", 32.0F, 8.0F, 128.0F,
                "Distancia máxima para ver la burbuja.");
        privacyAccepted = config.getBoolean("privacyAccepted", "general", false,
                "El jugador ya vio el aviso de privacidad.");

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void save() {
        if (config == null) return;
        config.get("general", "apiKey", "").set(apiKey);
        config.get("general", "model", "gemini-2.0-flash").set(model);
        config.get("general", "maxCaptureSize", 1024).set(maxCaptureSize);
        config.get("general", "dailyRequestLimit", 200).set(dailyRequestLimit);
        config.get("general", "minRequestCooldownSec", 3).set(minRequestCooldownSec);
        config.get("general", "spontaneousMinMin", 6).set(spontaneousMinMin);
        config.get("general", "spontaneousMaxMin", 99).set(spontaneousMaxMin);
        config.get("general", "alertsEnabled", true).set(alertsEnabled);
        config.get("general", "bubbleEnabled", true).set(bubbleEnabled);
        config.get("general", "chatEnabled", true).set(chatEnabled);
        config.get("general", "capturesEnabledDefault", true).set(capturesEnabledDefault);
        config.get("general", "bubbleViewRange", 32.0F).set((float) bubbleViewRange);
        config.get("general", "privacyAccepted", false).set(privacyAccepted);
        config.save();
    }

    public static boolean canRequest() {
        long now = System.currentTimeMillis();
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(lastRequestDay)) {
            lastRequestDay = today;
            requestsToday = 0;
        }
        if (requestsToday >= dailyRequestLimit) return false;
        if (now - lastRequestMs < minRequestCooldownSec * 1000L) return false;
        if (apiKey == null || apiKey.trim().isEmpty()) return false;
        return true;
    }

    public static void recordRequest() {
        lastRequestMs = System.currentTimeMillis();
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(lastRequestDay)) {
            lastRequestDay = today;
            requestsToday = 0;
        }
        requestsToday++;
    }
}
