package com.npcgemini;

import com.npcgemini.client.render.RenderNPC;
import com.npcgemini.command.CommandBot;
import com.npcgemini.command.CommandNpcAdmin;
import com.npcgemini.config.ModConfig;
import com.npcgemini.entity.EntityNPC;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import org.apache.logging.log4j.Logger;

/**
 * Mod de cliente (un solo jugador). El servidor integrado corre en el mismo proceso.
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
    public static final String VERSION = "2.0.0-beta1";

    public static Logger logger;

    @Mod.Instance(MODID)
    public static NPCGeminiMod instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        ModConfig.init(event.getModConfigurationDirectory());

        EntityRegistry.registerModEntity(
            new ResourceLocation(MODID, "gemini_npc"),
            EntityNPC.class,
            "gemini_npc",
            0,
            this,
            64,
            3,
            true,
            0x6366F1,
            0x10B981
        );

        RenderingRegistry.registerEntityRenderingHandler(EntityNPC.class, RenderNPC::new);

        logger.info("[NPCGemini] PreInit: entidad y render registrados.");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ProfileManager.init();
        CommandBot.register();
        logger.info("[NPCGemini] Init: perfiles, comandos /bot y /botchat listos.");
        if (ModConfig.apiKey == null || ModConfig.apiKey.trim().isEmpty()) {
            logger.warn("[NPCGemini] No hay API key. Edita config/npcgemini.cfg y pon apiKey=TU_CLAVE");
        }
    }

    /** Comandos de servidor (/npc ...): corren en el servidor integrado, sin hilos cruzados. */
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandNpcAdmin());
        logger.info("[NPCGemini] Comando /npc registrado.");
    }
}
