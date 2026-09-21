package re.imc.geysermodelengine.managers.commands.managers.geysermodelengine;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.commands.geysermodelenginecommands.GeyserModelEngineReloadCommand;
import re.imc.geysermodelengine.managers.commands.CommandManagers;
import re.imc.geysermodelengine.managers.commands.subcommands.SubCommands;

import java.util.ArrayList;

public class GeyserModelEngineCommandManager implements CommandManagers {

    private final ArrayList<SubCommands> commands = new ArrayList<>();

    public GeyserModelEngineCommandManager(GeyserModelEngine plugin) {
        commands.add(new GeyserModelEngineReloadCommand(plugin));

        registerCommand(plugin);
    }

    private void registerCommand(GeyserModelEngine plugin) {
        // Paper builds the command tree itself on each (re)load; the handler runs every time.
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(getName());
            commands.forEach(subCommands -> root.then(subCommands.onCommand()));
            event.registrar().register(root.build());
        });
    }

    @Override
    public String getName() {
        return "geysermodelengine";
    }

    @Override
    public ArrayList<SubCommands> getCommands() {
        return commands;
    }
}
