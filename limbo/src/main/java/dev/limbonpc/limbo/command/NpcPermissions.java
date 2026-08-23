package dev.limbonpc.limbo.command;

import com.loohp.limbo.commands.CommandSender;

public final class NpcPermissions {
    private NpcPermissions() {}
    public static boolean has(CommandSender sender, String action) {
        return sender.hasPermission("limbo-npc.npc.*") || sender.hasPermission("limbo-npc.npc." + action);
    }
}
