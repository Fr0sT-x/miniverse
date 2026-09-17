package dev.frost.miniverse.minigame.impl.zombies.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesMinigame;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponItemHelper;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ZombiesCommand {
    private ZombiesCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Standard /zombies command tree
        dispatcher.register(
            CommandManager.literal("zombies")
                .then(CommandManager.literal("giveGold")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ZombiesCommand::giveGold)))
                .then(CommandManager.literal("giveWeapon")
                    .then(CommandManager.argument("weapon", StringArgumentType.word())
                        .executes(ZombiesCommand::giveWeapon)))
                .then(CommandManager.literal("power")
                    .executes(ZombiesCommand::togglePower))
                .then(CommandManager.literal("dev")
                    .then(CommandManager.literal("gold")
                        .executes(ctx -> giveGoldAmount(ctx, 10000))
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ZombiesCommand::giveGold)))
                    .then(CommandManager.literal("skip")
                        .executes(ZombiesCommand::devSkip))
                    .then(CommandManager.literal("nuke")
                        .executes(ZombiesCommand::devGiveNuke)
                        .then(CommandManager.literal("trigger")
                            .executes(ZombiesCommand::devTriggerNuke))))
        );

        // Convenient /zdev root command
        dispatcher.register(
            CommandManager.literal("zdev")
                .then(CommandManager.literal("gold")
                    .executes(ctx -> giveGoldAmount(ctx, 10000))
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ZombiesCommand::giveGold)))
                .then(CommandManager.literal("skip")
                    .executes(ZombiesCommand::devSkip))
                .then(CommandManager.literal("nuke")
                    .executes(ZombiesCommand::devGiveNuke)
                    .then(CommandManager.literal("trigger")
                        .executes(ZombiesCommand::devTriggerNuke)))
        );

        // Direct root shortcuts for rapid testing
        dispatcher.register(
            CommandManager.literal("zgold")
                .executes(ctx -> giveGoldAmount(ctx, 10000))
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                    .executes(ZombiesCommand::giveGold))
        );

        dispatcher.register(
            CommandManager.literal("zskip")
                .executes(ZombiesCommand::devSkip)
        );

        dispatcher.register(
            CommandManager.literal("znuke")
                .executes(ZombiesCommand::devGiveNuke)
                .then(CommandManager.literal("trigger")
                    .executes(ZombiesCommand::devTriggerNuke))
        );
    }

    private static int giveGoldAmount(CommandContext<ServerCommandSource> ctx, int amount) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if (MinigameManager.getInstance().getActiveMinigame() instanceof ZombiesMinigame zm && zm.getState() == GameState.RUNNING) {
            zm.addGold(player, amount);
            int current = zm.getGold(player);
            ctx.getSource().sendFeedback(() -> Text.literal("[DEV] Added " + amount + " gold to " + player.getName().getString() + "! (Total: " + current + "g)").formatted(Formatting.GREEN, Formatting.BOLD), false);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("No active Zombies game running."));
        return 0;
    }

    private static int giveGold(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        return giveGoldAmount(ctx, amount);
    }

    private static int devSkip(CommandContext<ServerCommandSource> ctx) {
        if (MinigameManager.getInstance().getActiveMinigame() instanceof ZombiesMinigame zm && zm.getState() == GameState.RUNNING) {
            zm.devSkipRound();
            ctx.getSource().sendFeedback(() -> Text.literal("[DEV] Skipped round / wave!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("No active Zombies game running."));
        return 0;
    }

    private static int devGiveNuke(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if (MinigameManager.getInstance().getActiveMinigame() instanceof ZombiesMinigame zm && zm.getState() == GameState.RUNNING) {
            ItemStack nuke = ZombiesMinigame.createNukeItem();
            player.getInventory().offerOrDrop(nuke);
            ctx.getSource().sendFeedback(() -> Text.literal("[DEV] Gave Tactical Nuke item! Right-click anywhere to detonate.").formatted(Formatting.RED, Formatting.BOLD), false);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("No active Zombies game running."));
        return 0;
    }

    private static int devTriggerNuke(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if (MinigameManager.getInstance().getActiveMinigame() instanceof ZombiesMinigame zm && zm.getState() == GameState.RUNNING) {
            zm.triggerNuke(player);
            ctx.getSource().sendFeedback(() -> Text.literal("[DEV] Tactical Nuke detonated!").formatted(Formatting.RED, Formatting.BOLD), false);
            return 1;
        }
        ctx.getSource().sendError(Text.literal("No active Zombies game running."));
        return 0;
    }

    private static int giveWeapon(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String weaponName = StringArgumentType.getString(ctx, "weapon");
        WeaponType type = WeaponType.fromString(weaponName);

        ItemStack stack = WeaponItemHelper.createWeaponStack(type);
        player.getInventory().offerOrDrop(stack);
        ctx.getSource().sendFeedback(() -> Text.literal("Gave " + type.getData().displayName() + " to " + player.getName().getString()).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int togglePower(CommandContext<ServerCommandSource> ctx) {
        if (MinigameManager.getInstance().getActiveMinigame() instanceof ZombiesMinigame zm) {
            ctx.getSource().sendFeedback(() -> Text.literal("Toggled Zombies power.").formatted(Formatting.GOLD), false);
            return 1;
        }

        ctx.getSource().sendError(Text.literal("No active Zombies game running."));
        return 0;
    }
}
