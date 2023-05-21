package dev.permasha.gptcraft.commands;

import dev.permasha.gptcraft.GPTCraft;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class AskCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            return true;
        }

        String lab = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        GPTCraft.ask(player, lab);

        return true;
    }

}
