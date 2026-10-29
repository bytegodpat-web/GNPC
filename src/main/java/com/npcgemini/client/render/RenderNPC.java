package com.npcgemini.client.render;

import com.npcgemini.NPCGeminiMod;
import com.npcgemini.config.ModConfig;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class RenderNPC extends RenderLivingBase<EntityNPC> {

    private static final ResourceLocation DEFAULT_TEXTURE =
            new ResourceLocation(NPCGeminiMod.MODID, "textures/entity/npc/luna.png");

    private final ModelPlayer modelSlim;
    private final ModelPlayer modelClassic;

    public RenderNPC(RenderManager manager) {
        super(manager, new ModelPlayer(0.0F, true), 0.5F);
        this.modelSlim = new ModelPlayer(0.0F, true);
        this.modelClassic = new ModelPlayer(0.0F, false);
    }

    @Override
    protected void preRenderCallback(EntityNPC entity, float partialTickTime) {
        float scale = 0.9375F;
        GlStateManager.scale(scale, scale, scale);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityNPC entity) {
        String skinId = entity.getSkinId();
        if (skinId == null || skinId.isEmpty()) skinId = "luna";
        return new ResourceLocation(NPCGeminiMod.MODID, "textures/entity/npc/" + skinId + ".png");
    }

    @Override
    public void doRender(EntityNPC entity, double x, double y, double z, float entityYaw, float partialTicks) {
        BotProfile p = ProfileManager.get(entity.getProfileId());
        boolean slim = p == null || p.slimArms;
        this.mainModel = slim ? modelSlim : modelClassic;

        super.doRender(entity, x, y, z, entityYaw, partialTicks);

        if (ModConfig.bubbleEnabled) {
            renderBubble(entity, x, y, z);
        }
    }

    private void renderBubble(EntityNPC entity, double x, double y, double z) {
        String text = entity.getBubbleText();
        if (text == null || text.isEmpty()) return;

        float alpha = entity.getBubbleAlpha();
        if (alpha <= 0.01f) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        double dist = mc.player.getDistance(entity);
        if (dist > ModConfig.bubbleViewRange) return;

        FontRenderer fr = getFontRendererFromRenderManager();
        int maxWidth = 140;
        List<String> lines = wrap(text, fr, maxWidth);

        float lineH = 9f;
        float totalH = lines.size() * lineH + 6f;
        float maxLineW = 0;
        for (String l : lines) {
            float w = fr.getStringWidth(l);
            if (w > maxLineW) maxLineW = w;
        }
        float totalW = maxLineW + 8f;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + entity.height + 0.6F, (float) z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        float halfW = totalW / 2f;
        float top = -totalH;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.disableTexture2D();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float a = 0.55f * alpha;
        buf.pos(-halfW, top, 0).color(0f, 0f, 0f, a).endVertex();
        buf.pos(-halfW, 2, 0).color(0f, 0f, 0f, a).endVertex();
        buf.pos(halfW, 2, 0).color(0f, 0f, 0f, a).endVertex();
        buf.pos(halfW, top, 0).color(0f, 0f, 0f, a).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();

        int textAlpha = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        float ty = top + 4f;
        for (String line : lines) {
            float tw = fr.getStringWidth(line);
            fr.drawString(line, -tw / 2f, ty, textAlpha, false);
            ty += lineH;
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private List<String> wrap(String text, FontRenderer fr, int maxWidth) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (fr.getStringWidth(test) > maxWidth && line.length() > 0) {
                result.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) result.add(line.toString());
        if (result.isEmpty()) result.add(text);
        return result;
    }
}
