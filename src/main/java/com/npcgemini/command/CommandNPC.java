package com.npcgemini.command;

import com.npcgemini.client.ChatHandler;
import com.npcgemini.entity.EntityNPC;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import javax.annotation.Nullable;
import net.minecraft.util.math.BlockPos;
import java.util.Collections;
import java.util.List;

/** Comandos locales para NPCs que no son entidades jugador. */
public class CommandNPC extends CommandBase {
    public static void register() { ClientCommandHandler.instance.registerCommand(new CommandNPC()); }
    @Override public String getName() { return "npcmsg"; }
    @Override public String getUsage(ICommandSender sender) { return "/npcmsg <mensaje>"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) { sender.sendMessage(new TextComponentString("§eUso: /npcmsg <mensaje>")); return; }
        StringBuilder b = new StringBuilder(); for (String a : args) { if (b.length() > 0) b.append(' '); b.append(a); }
        EntityNPC npc = nearest();
        if (npc == null) { sender.sendMessage(new TextComponentString("§cNo hay un NPC cerca.")); return; }
        ChatHandler.sendToBot(npc.getProfileId(), b.toString(), false);
    }
    private EntityNPC nearest() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return null;
        List<EntityNPC> list = mc.world.getEntitiesWithinAABB(EntityNPC.class, mc.player.getEntityBoundingBox().grow(32));
        EntityNPC best = null; double d = Double.MAX_VALUE;
        for (EntityNPC n : list) { double x = mc.player.getDistanceSq(n); if (x < d) { d=x; best=n; } }
        return best;
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) { return Collections.emptyList(); }
}
