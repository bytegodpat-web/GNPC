package com.npcgemini.client;

import com.npcgemini.NPCGeminiMod;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carga skins PNG desde config/npcgemini/skins/ y las registra como DynamicTexture.
 * ID de skin = nombre del archivo sin .png
 */
public class SkinManager {

    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation FALLBACK =
            new ResourceLocation(NPCGeminiMod.MODID, "textures/entity/npc/luna.png");

    public static ResourceLocation getTexture(String skinId) {
        if (skinId == null || skinId.isEmpty()) skinId = "luna";
        // Sanitizar: solo nombre de archivo, sin rutas
        skinId = skinId.replace("\\", "/");
        if (skinId.contains("..") || skinId.contains("/")) {
            return FALLBACK;
        }

        ResourceLocation cached = CACHE.get(skinId);
        if (cached != null) return cached;

        // 1) Intentar skin del jugador en config/npcgemini/skins/<id>.png
        File skinsDir = new File(ProfileManager.getConfigRoot(), "skins");
        File png = new File(skinsDir, skinId + ".png");
        if (png.isFile()) {
            try {
                BufferedImage img = ImageIO.read(png);
                if (img != null && isValidSkin(img)) {
                    // Convertir 64x32 antiguo a 64x64 si hace falta
                    if (img.getHeight() == 32 && img.getWidth() == 64) {
                        img = convertLegacySkin(img);
                    }
                    DynamicTexture dyn = new DynamicTexture(img);
                    ResourceLocation loc = Minecraft.getMinecraft().getTextureManager()
                            .getDynamicTextureLocation("npcgemini_skin_" + skinId, dyn);
                    CACHE.put(skinId, loc);
                    NPCGeminiMod.logger.info("[NPCGemini] Skin cargada: " + png.getAbsolutePath());
                    return loc;
                }
            } catch (Exception e) {
                NPCGeminiMod.logger.warn("No se pudo cargar skin " + skinId + ": " + e.getMessage());
            }
        }

        // 2) Skin incluida en el mod
        ResourceLocation modSkin = new ResourceLocation(NPCGeminiMod.MODID, "textures/entity/npc/" + skinId + ".png");
        // No podemos comprobar existencia facil; usamos fallback solo si falla el bind
        CACHE.put(skinId, modSkin);
        return modSkin;
    }

    public static void reload() {
        for (ResourceLocation loc : CACHE.values()) {
            if (loc.getResourcePath().startsWith("dynamic/")) {
                try {
                    Minecraft.getMinecraft().getTextureManager().deleteTexture(loc);
                } catch (Exception ignored) {}
            }
        }
        CACHE.clear();
        NPCGeminiMod.logger.info("[NPCGemini] Cache de skins reiniciada.");
    }

    private static boolean isValidSkin(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        // 64x64 actual o 64x32 legacy
        return (w == 64 && (h == 64 || h == 32));
    }

    /** Espeja piernas/brazos del formato antiguo 64x32 al 64x64 */
    private static BufferedImage convertLegacySkin(BufferedImage old) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        // Copiar parte superior
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                img.setRGB(x, y, old.getRGB(x, y));
            }
        }
        // Pierna izquierda (espejo de derecha) en fila inferior
        // Formato legacy: leg en 0,16  arm en 40,16 — simplificado: copiar zona inferior vacia
        // Minecraft leg mirror: copy 0,16 16x16 to 16,48 mirrored is complex;
        // para skins modernas el jugador ya usa 64x64. Si es 64x32, al menos se ve la mitad superior.
        return img;
    }
}
