package re.imc.geysermodelengine.managers.commands.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface SubCommands {

    /**
     * Subcommand setup, as a Brigadier literal registered under the manager's root command.
     * Paper's own command API is used: it follows the server version, unlike a shaded NMS library.
     */
    LiteralArgumentBuilder<CommandSourceStack> onCommand();
}
