package com.npcgemini.client.render;

import com.npcgemini.NPCGeminiMod;
import com.npcgemini.entity.EntityNPC;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public class RenderNPC extends RenderLivingBase<EntityNPC> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(NPCGeminiMod.MODID, "textures/entity/npc/luna.png");

    public RenderNPC(RenderManager manager) {
        // true = brazos finos (3 px). La skin de prueba esta hecha para ese modelo.
        super(manager, new ModelPlayer(0.0F, true), 0.5F);
    }

    @Override
    protected void preRenderCallback(EntityNPC entity, float partialTickTime) {
        float scale = 0.9375F;
        GlStateManager.scale(scale, scale, scale);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityNPC entity) {
        return TEXTURE;
    }
}
