package dev.frost.miniverse.chat;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatCommands {
    private ChatCommands() {
    }

    public static void registerNetwork() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CHAT_CHANNEL_SYNC_ID, (payload, context) -> {
            try {
                ChatChannel channel = ChatChannel.valueOf(payload.channel());
                ChatRouter.setPlayerChannel(context.player().getUuid(), channel);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        registerAllCommand(dispatcher, "a");
        registerAllCommand(dispatcher, "all");
        registerAllCommand(dispatcher, "shout");

        registerTeamCommand(dispatcher, "t");
        registerTeamCommand(dispatcher, "team");

        registerChatSwitchCommand(dispatcher, "chat");
        registerChatSwitchCommand(dispatcher, "channel");
    }

    private static void registerAllCommand(CommandDispatcher<ServerCommandSource> dispatcher, String commandName) {
        dispatcher.register(literal(commandName)
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) {
                    return 0;
                }
                ChatRouter.setPlayerChannel(player, ChatChannel.GLOBAL);
                player.sendMessage(Text.literal("You are now in the ALL channel.").formatted(Formatting.GREEN), false);
                return 1;
            })
            .then(argument("message", StringArgumentType.greedyString())
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    String message = StringArgumentType.getString(context, "message");
                    ChatRouter.dispatchMessage(player, message, ChatChannel.GLOBAL);
                    return 1;
                })));
    }

    private static void registerTeamCommand(CommandDispatcher<ServerCommandSource> dispatcher, String commandName) {
        dispatcher.register(literal(commandName)
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) {
                    return 0;
                }
                ChatRouter.setPlayerChannel(player, ChatChannel.TEAM);
                player.sendMessage(Text.literal("You are now in the TEAM channel.").formatted(Formatting.AQUA), false);
                return 1;
            })
            .then(argument("message", StringArgumentType.greedyString())
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    String message = StringArgumentType.getString(context, "message");
                    ChatRouter.dispatchMessage(player, message, ChatChannel.TEAM);
                    return 1;
                })));
    }

    private static void registerChatSwitchCommand(CommandDispatcher<ServerCommandSource> dispatcher, String commandName) {
        dispatcher.register(literal(commandName)
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) {
                    return 0;
                }
                ChatChannel current = ChatRouter.getPlayerChannel(player.getUuid());
                player.sendMessage(Text.literal("You are currently in the " + current.label() + " channel. Use /chat <all|team> to switch.").formatted(Formatting.YELLOW), false);
                return 1;
            })
            .then(literal("all")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    ChatRouter.setPlayerChannel(player, ChatChannel.GLOBAL);
                    player.sendMessage(Text.literal("You are now in the ALL channel.").formatted(Formatting.GREEN), false);
                    return 1;
                }))
            .then(literal("team")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    ChatRouter.setPlayerChannel(player, ChatChannel.TEAM);
                    player.sendMessage(Text.literal("You are now in the TEAM channel.").formatted(Formatting.AQUA), false);
                    return 1;
                }))
            .then(literal("toggle")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    ChatChannel current = ChatRouter.getPlayerChannel(player.getUuid());
                    ChatChannel next = current == ChatChannel.TEAM ? ChatChannel.GLOBAL : ChatChannel.TEAM;
                    ChatRouter.setPlayerChannel(player, next);
                    Formatting color = next == ChatChannel.GLOBAL ? Formatting.GREEN : Formatting.AQUA;
                    player.sendMessage(Text.literal("Switched to the " + next.label() + " channel.").formatted(color), false);
                    return 1;
                })));
    }
}
