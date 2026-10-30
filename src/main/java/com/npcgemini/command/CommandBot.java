package com.npcgemini.command;

import com.npcgemini.client.ChatHandler;
import com.npcgemini.profile.BotProfile;
import com.npcgemini.profile.ProfileManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /bot <nombre> <mensaje...>
 * Envía un mensaje suelto al bot.
 */
public class CommandBot extends CommandBase {

    public static void register() {
        ClientCommandHandler.instance.registerCommand(new CommandBot());
        ClientCommandHandler.instance.registerCommand(new CommandBotChat());
        CommandNPC.register();
    }

    @Override
    public String getName() {
        return "bot";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/bot <nombre> <mensaje>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("§eUso: /bot <nombre> <mensaje>"));
            return;
        }
        String name = args[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(' ');
            sb.append(args[i]);
        }
        String msg = sb.toString().trim();
        if (msg.isEmpty()) {
            sender.sendMessage(new TextComponentString("§eEscribe un mensaje."));
            return;
        }

        BotProfile p = ProfileManager.get(name);
        if (p == null) {
            // Crear perfil al vuelo si no existe
            p = ProfileManager.getOrCreate(name);
            sender.sendMessage(new TextComponentString("§7Perfil creado: " + p.name));
        }

        ChatHandler.sendToBot(p.id, msg, true);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (BotProfile p : ProfileManager.all()) {
                names.add(p.id);
            }
            return getListOfStringsMatchingLastWord(args, names);
        }
        return Collections.emptyList();
    }
}
