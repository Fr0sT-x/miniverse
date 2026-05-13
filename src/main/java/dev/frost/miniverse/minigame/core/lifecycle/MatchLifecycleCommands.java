package dev.frost.miniverse.minigame.core.lifecycle;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.session.SessionPermissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

public final class MatchLifecycleCommands {
    private MatchLifecycleCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("miniverse_cancel_return")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (!SessionPermissions.checkCanManageSessions(player, "cancel match return teleport")) {
                    return 0;
                }
                boolean cancelled = MatchLifecycleController.getInstance().cancelReturn(player);
                if (!cancelled) {
                    context.getSource().sendFeedback(() -> Text.literal("No active return countdown.").formatted(Formatting.YELLOW), false);
                    return 0;
                }
                return 1;
            }));
    }
}
