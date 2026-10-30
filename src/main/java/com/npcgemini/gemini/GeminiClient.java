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
 * Usa HttpURLConnection (Java 8) en un hilo aparte.
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

    /**
     * Envía un mensaje al modelo y devuelve la respuesta por callback en el hilo del juego.
     *
     * @param profile   perfil del bot
     * @param userText  texto del jugador (o evento)
     * @param context   bloque [CONTEXTO: ...] opcional
     * @param jpegBytes captura JPEG opcional (null si no hay)
     * @param onSuccess callback con la respuesta limpia
     * @param onError   callback con mensaje de error legible
     */
    public static void ask(BotProfile profile, String userText, String context,
                           byte[] jpegBytes, Consumer<String> onSuccess, Consumer<String> onError) {

        if (busy) {
            schedule(onError, "El bot ya está pensando. Espera un momento.");
            return;
        }
        if (!ModConfig.canRequest()) {
            if (ModConfig.apiKey == null || ModConfig.apiKey.trim().isEmpty()) {
                schedule(onError, "No hay API key. Configúrala en config/npcgemini.cfg (apiKey=...).");
            } else {
                schedule(onError, "Límite de peticiones alcanzado o cooldown activo. Intenta más tarde.");
            }
            return;
        }

        busy = true;
        EXECUTOR.submit(() -> {
            try {
                String response = callApi(profile, userText, context, jpegBytes);
                ModConfig.recordRequest();
                String cleaned = cleanResponse(response);
                schedule(onSuccess, cleaned);
            } catch (Exception e) {
                NPCGeminiMod.logger.warn("Error Gemini: " + e.getMessage());
                schedule(onError, friendlyError(e));
            } finally {
                busy = false;
            }
        });
    }

    private static String callApi(BotProfile profile, String userText, String context, byte[] jpegBytes) throws Exception {
        String model = ModConfig.model;
       String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/"
        + model
        + ":generateContent";

        JsonObject body = new JsonObject();

        // systemInstruction
        JsonObject sys = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysPart = new JsonObject();
        sysPart.addProperty("text", profile.buildSystemPrompt());
        sysParts.add(sysPart);
        sys.add("parts", sysParts);
        body.add("systemInstruction", sys);

        // contents: historial + mensaje actual
        JsonArray contents = new JsonArray();

        // Historial (solo texto)
        List<String> hist = profile.history;
        for (int i = 0; i + 1 < hist.size(); i += 2) {
            String pMsg = hist.get(i);
            String bMsg = hist.get(i + 1);
            contents.add(makeContent("user", stripPrefix(pMsg)));
            contents.add(makeContent("model", stripPrefix(bMsg)));
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

        // generationConfig
        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", 0.8);
        gen.addProperty("maxOutputTokens", 1024);
        body.add("generationConfig", gen);

        String jsonBody = body.toString();

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("x-goog-api-key", ModConfig.apiKey.trim());
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream() != null
                    ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
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
            throw new ApiException(code, resp);
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
                    return "(El modelo no generó respuesta; posible filtro de seguridad.)";
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
        // Quitar markdown residual
        s = s.replaceAll("\\*\\*", "");
        s = s.replaceAll("\\*", "");
        s = s.replaceAll("`", "");
        s = s.replaceAll("#{1,6}\\s*", "");
        // Cortar si es demasiado largo
        if (s.length() > 400) {
            s = s.substring(0, 397) + "...";
        }
        return s.trim();
    }

    private static String friendlyError(Exception e) {
        if (e instanceof ApiException) {
            ApiException ae = (ApiException) e;
            if (ae.code == 429) return "Cuota de la API agotada (429). Espera o revisa tu plan.";
            if (ae.code == 403) return "Clave inválida o región no permitida (403).";
            if (ae.code == 400) return "Petición inválida (400). Revisa el modelo en la config.";
            return "Error de API (" + ae.code + ").";
        }
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase().contains("timeout")) {
            return "La API no respondió a tiempo.";
        }
        return "Error de red o de la API. Revisa la conexión y la clave.";
    }

    private static void schedule(Consumer<String> cb, String msg) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (cb != null) cb.accept(msg);
        });
    }

    private static class ApiException extends Exception {
        final int code;
        ApiException(int code, String body) {
            super("HTTP " + code + ": " + (body != null && body.length() > 200 ? body.substring(0, 200) : body));
            this.code = code;
        }
    }
}
