package com.npcgemini;

import com.npcgemini.client.render.RenderNPC;
import com.npcgemini.entity.EntityNPC;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import org.apache.logging.log4j.Logger;

/**
 * Mod solo de cliente (juego de un jugador). El servidor integrado corre en el
 * mismo proceso, por eso la entidad se registra aqui y funciona igualmente.
 */
@Mod(
    modid = NPCGeminiMod.MODID,
    name = NPCGeminiMod.NAME,
    version = NPCGeminiMod.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    clientSideOnly = true
)
public class NPCGeminiMod {
    public static final String MODID = "npcgemini";
    public static final String NAME = "Gemini NPC";
    public static final String VERSION = "0.1.0";

    public static Logger logger;

    @Mod.Instance(MODID)
    public static NPCGeminiMod instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();

        EntityRegistry.registerModEntity(
            new ResourceLocation(MODID, "gemini_npc"),
            EntityNPC.class,
            "gemini_npc",
            0,          // id de la entidad dentro del mod
            this,
            64,         // rango de seguimiento
            3,          // frecuencia de actualizacion
            true,       // envia actualizaciones de velocidad
            0x6366F1,   // color primario del huevo
            0x10B981    // color secundario del huevo
        );

        RenderingRegistry.registerEntityRenderingHandler(EntityNPC.class, RenderNPC::new);

        logger.info("[NPCGemini] Fase 1 cargada: entidad y render registrados.");
    }
}
