package re.imc.geysermodelengine.commands.geysermodelenginecommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.commands.subcommands.SubCommands;
import re.imc.geysermodelengine.util.ColourUtils;

public class GeyserModelEngineReloadCommand implements SubCommands {

    private final GeyserModelEngine plugin;

    private final ColourUtils colourUtils = new ColourUtils();

    public GeyserModelEngineReloadCommand(GeyserModelEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> onCommand() {
        return Commands.literal("reload")
                .requires(source -> source.getSender().hasPermission("geysermodelengine.commands.reload"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> plugin.getConfigManager().load());
                    sender.sendMessage(colourUtils.miniFormat(plugin.getConfigManager().getLang().getString("commands.reload.successfully-reloaded")));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
