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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /botchat <nombre>  — entra en modo conversación
 * /botchat off       — sale del modo
 */
public class CommandBotChat extends CommandBase {

    @Override
    public String getName() {
        return "botchat";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/botchat <nombre|off>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            if (ChatHandler.chatModeBot != null) {
                sender.sendMessage(new TextComponentString(
                        "§aModo conversación activo con §d" + ChatHandler.chatModeBot
                                + "§a. Usa /botchat off para salir."));
            } else {
                sender.sendMessage(new TextComponentString("§eUso: /botchat <nombre>  o  /botchat off"));
            }
            return;
        }

        String arg = args[0].toLowerCase();
        if (arg.equals("off") || arg.equals("none") || arg.equals("salir")) {
            ChatHandler.chatModeBot = null;
            sender.sendMessage(new TextComponentString("§7Modo conversación desactivado."));
            return;
        }

        BotProfile p = ProfileManager.get(arg);
        if (p == null) {
            p = ProfileManager.getOrCreate(arg);
            sender.sendMessage(new TextComponentString("§7Perfil creado: " + p.name));
        }

        ChatHandler.chatModeBot = p.id;
        sender.sendMessage(new TextComponentString(
                "§aHablando con §d" + p.name + "§a. Todo lo que escribas irá al bot. /botchat off para salir."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            names.add("off");
            for (BotProfile p : ProfileManager.all()) {
                names.add(p.id);
            }
            return getListOfStringsMatchingLastWord(args, names);
        }
        return Collections.emptyList();
    }
}
