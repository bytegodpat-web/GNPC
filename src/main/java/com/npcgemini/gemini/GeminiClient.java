package com.npcgemini.gemini;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.npcgemini.NPCGeminiMod;
import com.npcgemini.config.ModConfig;
import com.npcgemini.profile.BotProfile;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Cliente HTTP para la API de Gemini (Google AI Studio).
 */
public class GeminiClient {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NPCGemini-API");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean busy = false;

    public static boolean isBusy() {
        return busy;
    }

    public static void ask(BotProfile profile, String userText, String context,
                           byte[] jpegBytes, Consumer<String> onSuccess, Consumer<String> onError) {

        if (busy) {
            schedule(onError, "El bot ya esta pensando. Espera un momento.");
            return;
        }
        if (!ModConfig.canRequest()) {
            if (ModConfig.apiKey == null || ModConfig.apiKey.trim().isEmpty()) {
                schedule(onError, "No hay API key. Configurala en config/npcgemini.cfg");
            } else {
                schedule(onError, "Limite de peticiones o cooldown activo.");
            }
            return;
        }

        busy = true;
        EXECUTOR.submit(() -> {
            try {
                String response = callApi(profile, userText, context, jpegBytes);
                ModConfig.recordRequest();
                String cleaned = cleanResponse(response);
                if (cleaned.isEmpty()) {
                    schedule(onError, "Respuesta vacia del modelo.");
                } else {
                    schedule(onSuccess, cleaned);
                }
            } catch (Exception e) {
                NPCGeminiMod.logger.warn("Error Gemini: " + e.getMessage());
                schedule(onError, friendlyError(e));
            } finally {
                busy = false;
            }
        });
    }

    private static String callApi(BotProfile profile, String userText, String context, byte[] jpegBytes) throws Exception {
        String model = ModConfig.model == null ? "gemini-3.5-flash" : ModConfig.model.trim();
        // URL sin key en query; key va en header (recomendado por Google)
        String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent";

        JsonObject body = new JsonObject();

        // systemInstruction
        JsonObject sys = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysPart = new JsonObject();
        sysPart.addProperty("text", profile.buildSystemPrompt());
        sysParts.add(sysPart);
        sys.add("parts", sysParts);
        body.add("systemInstruction", sys);

        JsonArray contents = new JsonArray();

        // Historial
        List<String> hist = profile.history;
        for (int i = 0; i + 1 < hist.size(); i += 2) {
            contents.add(makeContent("user", stripPrefix(hist.get(i))));
            contents.add(makeContent("model", stripPrefix(hist.get(i + 1))));
        }

        // Mensaje actual
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray parts = new JsonArray();

        StringBuilder textBuilder = new StringBuilder();
        if (context != null && !context.isEmpty()) {
            textBuilder.append(context).append("\n");
        }
        textBuilder.append("[JUGADOR]: ").append(userText);

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", textBuilder.toString());
        parts.add(textPart);

        if (jpegBytes != null && jpegBytes.length > 0) {
            JsonObject imgPart = new JsonObject();
            JsonObject inline = new JsonObject();
            inline.addProperty("mimeType", "image/jpeg");
            inline.addProperty("data", Base64.getEncoder().encodeToString(jpegBytes));
            imgPart.add("inlineData", inline);
            parts.add(imgPart);
        }

        userContent.add("parts", parts);
        contents.add(userContent);
        body.add("contents", contents);

        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", 0.8);
        gen.addProperty("maxOutputTokens", 256);
        body.add("generationConfig", gen);

        String jsonBody = body.toString();
        String key = ModConfig.apiKey.trim();

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("x-goog-api-key", key);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(25000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(
                    conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        String resp = sb.toString();
        if (code < 200 || code >= 300) {
            NPCGeminiMod.logger.warn("Gemini HTTP " + code + " model=" + model + " body=" +
                    (resp.length() > 300 ? resp.substring(0, 300) : resp));
            throw new ApiException(code, resp, model);
        }

        return extractText(resp);
    }

    private static JsonObject makeContent(String role, String text) {
        JsonObject c = new JsonObject();
        c.addProperty("role", role);
        JsonArray parts = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("text", text);
        parts.add(p);
        c.add("parts", parts);
        return c;
    }

    private static String stripPrefix(String s) {
        if (s == null) return "";
        int idx = s.indexOf("]: ");
        if (idx >= 0) return s.substring(idx + 3);
        return s;
    }

    private static String extractText(String json) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) {
                if (root.has("promptFeedback")) {
                    return "(Filtro de seguridad del modelo.)";
                }
                return "";
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null) return "";
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.size() == 0) return "";
            return parts.get(0).getAsJsonObject().get("text").getAsString();
        } catch (Exception e) {
            NPCGeminiMod.logger.warn("Parse error: " + e.getMessage());
            return "";
        }
    }

    private static String cleanResponse(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replaceAll("\\*\\*", "");
        s = s.replaceAll("\\*", "");
        s = s.replaceAll("`", "");
        s = s.replaceAll("#{1,6}\\s*", "");
        if (s.length() > 400) {
            s = s.substring(0, 397) + "...";
        }
        return s.trim();
    }

    private static String friendlyError(Exception e) {
        if (e instanceof ApiException) {
            ApiException ae = (ApiException) e;
            if (ae.code == 404) {
                return "Modelo no encontrado (404): '" + ae.model
                        + "'. Cambia S:model en config/npcgemini.cfg "
                        + "(prueba gemini-3.5-flash o gemini-2.5-flash).";
            }
            if (ae.code == 429) return "Cuota agotada (429). Espera o revisa tu plan.";
            if (ae.code == 403) return "Clave invalida o region bloqueada (403).";
            if (ae.code == 400) {
                String hint = "";
                if (ae.body != null && ae.body.contains("API key")) hint = " Revisa la API key.";
                return "Peticion invalida (400)." + hint;
            }
            return "Error de API (" + ae.code + ").";
        }
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase().contains("timeout")) {
            return "La API no respondio a tiempo.";
        }
        return "Error de red o de la API.";
    }

    private static void schedule(Consumer<String> cb, String msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (cb != null) cb.accept(msg);
        });
    }

    private static class ApiException extends Exception {
        final int code;
        final String body;
        final String model;
        ApiException(int code, String body, String model) {
            super("HTTP " + code + " model=" + model);
            this.code = code;
            this.body = body;
            this.model = model;
        }
    }
}
