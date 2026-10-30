package com.npcgemini.util;

import com.npcgemini.NPCGeminiMod;
import com.npcgemini.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.IntBuffer;
import java.util.Iterator;

/**
 * Captura el framebuffer, lo escala y lo codifica a JPEG.
 * Debe llamarse desde el hilo de render (o el siguiente tick de render).
 */
public class ScreenshotHelper {

    /**
     * Captura la pantalla actual y devuelve JPEG, o null si falla.
     */
    public static byte[] captureJpeg() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            Framebuffer fb = mc.getFramebuffer();
            int w = mc.displayWidth;
            int h = mc.displayHeight;

            if (OpenGlHelper.isFramebufferEnabled()) {
                w = fb.framebufferTextureWidth;
                h = fb.framebufferTextureHeight;
            }

            IntBuffer buffer = BufferUtils.createIntBuffer(w * h);
            int[] values = new int[w * h];

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

            if (OpenGlHelper.isFramebufferEnabled()) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            } else {
                GL11.glReadPixels(0, 0, w, h, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            }

            buffer.get(values);

            // Invertir verticalmente y convertir BGRA -> ARGB
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = values[(h - 1 - y) * w + x];
                    // BGRA en little-endian: ya viene como ARGB útil tras el REV
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }

            // Escalar manteniendo proporción
            int maxSide = ModConfig.maxCaptureSize;
            int nw = w, nh = h;
            if (w > maxSide || h > maxSide) {
                if (w >= h) {
                    nw = maxSide;
                    nh = (int) (h * (maxSide / (double) w));
                } else {
                    nh = maxSide;
                    nw = (int) (w * (maxSide / (double) h));
                }
            }

            BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(image, 0, 0, nw, nh, null);
            g2.dispose();

            // JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                ImageIO.write(scaled, "jpg", baos);
            } else {
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.75f);
                }
                writer.setOutput(new MemoryCacheImageOutputStream(baos));
                writer.write(null, new IIOImage(scaled, null, null), param);
                writer.dispose();
            }

            return baos.toByteArray();
        } catch (Exception e) {
            NPCGeminiMod.logger.warn("Error capturando pantalla: " + e.getMessage());
            return null;
        }
    }
}
