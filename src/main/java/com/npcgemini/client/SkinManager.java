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

    /** Skins incluidas en el mod (assets/npcgemini/textures/entity/npc/<id>.png). */
    public static final String[] BUILTIN = {"luna", "kai", "mira", "bruno"};

    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, DynamicTexture> DYN = new ConcurrentHashMap<>();
    private static final ResourceLocation FALLBACK =
            new ResourceLocation(NPCGeminiMod.MODID, "textures/entity/npc/luna.png");

    public static ResourceLocation getTexture(String skinId) {
        if (skinId == null || skinId.isEmpty()) skinId = "luna";
        skinId = skinId.replace("\\", "/");
        if (skinId.contains("..") || skinId.contains("/")) {
            return FALLBACK;
        }

        ResourceLocation cached = CACHE.get(skinId);
        if (cached != null) return cached;

        File skinsDir = new File(ProfileManager.getConfigRoot(), "skins");
        File png = new File(skinsDir, skinId + ".png");
        if (png.isFile()) {
            try {
                BufferedImage img = ImageIO.read(png);
                if (img != null && isValidSkin(img)) {
                    if (img.getHeight() == 32 && img.getWidth() == 64) {
                        img = convertLegacySkin(img);
                    }
                    DynamicTexture dyn = new DynamicTexture(img);
                    ResourceLocation loc = Minecraft.getMinecraft().getTextureManager()
                            .getDynamicTextureLocation("npcgemini_skin_" + skinId, dyn);
                    CACHE.put(skinId, loc);
                    DYN.put(skinId, dyn);
                    NPCGeminiMod.logger.info("[NPCGemini] Skin cargada: " + png.getAbsolutePath());
                    return loc;
                }
            } catch (Exception e) {
                NPCGeminiMod.logger.warn("No se pudo cargar skin " + skinId + ": " + e.getMessage());
            }
        }

        ResourceLocation modSkin = new ResourceLocation(NPCGeminiMod.MODID, "textures/entity/npc/" + skinId + ".png");
        CACHE.put(skinId, modSkin);
        return modSkin;
    }

    public static void reload() {
        for (Map.Entry<String, ResourceLocation> e : CACHE.entrySet()) {
            if (DYN.containsKey(e.getKey())) {
                try {
                    Minecraft.getMinecraft().getTextureManager().deleteTexture(e.getValue());
                } catch (Exception ignored) {}
            }
        }
        CACHE.clear();
        DYN.clear();
        NPCGeminiMod.logger.info("[NPCGemini] Cache de skins reiniciada.");
    }

    private static boolean isValidSkin(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        return (w == 64 && (h == 64 || h == 32));
    }

    private static BufferedImage convertLegacySkin(BufferedImage old) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                img.setRGB(x, y, old.getRGB(x, y));
            }
        }
        // Piernas y brazos izquierdos: copia espejada de los derechos
        flip(img, 4, 16, 16, 32, 4, 4);
        flip(img, 8, 16, 16, 32, 4, 4);
        flip(img, 0, 20, 24, 32, 4, 12);
        flip(img, 4, 20, 16, 32, 4, 12);
        flip(img, 8, 20, 8, 32, 4, 12);
        flip(img, 12, 20, 16, 32, 4, 12);
        flip(img, 44, 16, -8, 32, 4, 4);
        flip(img, 48, 16, -8, 32, 4, 4);
        flip(img, 40, 20, 0, 32, 4, 12);
        flip(img, 44, 20, -8, 32, 4, 12);
        flip(img, 48, 20, -16, 32, 4, 12);
        flip(img, 52, 20, -8, 32, 4, 12);
        // La capa base debe ser opaca (cabeza y cuerpo)
        opaque(img, 0, 0, 32, 16);
        opaque(img, 0, 16, 64, 32);
        opaque(img, 16, 48, 48, 64);
        return img;
    }

    /** Copia el bloque (x,y,w,h) a (x+dx, y+dy) invertido horizontalmente. */
    private static void flip(BufferedImage img, int x, int y, int dx, int dy, int w, int h) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                img.setRGB(x + dx + (w - 1 - i), y + dy + j, img.getRGB(x + i, y + j));
            }
        }
    }

    private static void opaque(BufferedImage img, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                img.setRGB(x, y, img.getRGB(x, y) | 0xFF000000);
            }
        }
    }
}
