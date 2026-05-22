package dev.frost.miniverse.common;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Shared payload IDs and codecs for the session GUI. */
public final class NetworkConstants {
    public static final String MOD_ID = "miniverse";

    public static final CustomPayload.Id<RequestSessionsPayload> REQUEST_SESSIONS_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_request"));
    public static final CustomPayload.Id<SessionListPayload> SESSION_LIST_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_list"));
    public static final CustomPayload.Id<CreateSessionPayload> CREATE_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_create"));
    public static final CustomPayload.Id<LaunchSessionPayload> LAUNCH_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_launch"));
    public static final CustomPayload.Id<StopSessionPayload> STOP_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_stop"));
    public static final CustomPayload.Id<InspectSessionPayload> INSPECT_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_inspect"));
    public static final CustomPayload.Id<RelaunchSessionPayload> RELAUNCH_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_relaunch"));
    public static final CustomPayload.Id<ChangeSeedPayload> CHANGE_SEED_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "change_seed"));
    public static final CustomPayload.Id<CleanupPlayerPayload> CLEANUP_PLAYER_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "cleanup_player"));
    public static final CustomPayload.Id<LauncherSettingsPayload> LAUNCHER_SETTINGS_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "launcher_settings"));
    public static final CustomPayload.Id<ServerSettingsPayload> SERVER_SETTINGS_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "server_settings"));
    public static final CustomPayload.Id<ClientConnectionHostPayload> CLIENT_CONNECTION_HOST_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "client_connection_host"));
    public static final CustomPayload.Id<FreezeStatePayload> FREEZE_STATE_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "freeze_state"));
    public static final CustomPayload.Id<TransitionStartPayload> TRANSITION_START_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "transition_start"));
    public static final CustomPayload.Id<TransitionReadyPayload> TRANSITION_READY_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "transition_ready"));
    public static final CustomPayload.Id<ClientMatchReadyPayload> CLIENT_MATCH_READY_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "client_match_ready"));
    public static final CustomPayload.Id<MatchIntroPayload> MATCH_INTRO_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "match_intro"));
    public static final CustomPayload.Id<MatchReadyStatePayload> MATCH_READY_STATE_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "match_ready_state"));
    public static final CustomPayload.Id<MatchStartPayload> MATCH_START_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "match_start"));
    public static final CustomPayload.Id<VelocityProxyPayload> VELOCITY_PROXY_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "velocity"));
    public static final CustomPayload.Id<ProtectionOverlayPayload> PROTECTION_OVERLAY_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "protection_overlay"));
    public static final CustomPayload.Id<BountyHuntInvincibilityPayload> BOUNTYHUNT_INVINCIBILITY_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "bountyhunt_invincibility"));

    private static boolean payloadTypesRegistered;

    private NetworkConstants() {
    }

    public static synchronized void registerPayloadTypes() {
        if (payloadTypesRegistered) {
            return;
        }

        PayloadTypeRegistry.playC2S().register(REQUEST_SESSIONS_ID, RequestSessionsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CREATE_SESSION_ID, CreateSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LAUNCH_SESSION_ID, LaunchSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(STOP_SESSION_ID, StopSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(INSPECT_SESSION_ID, InspectSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RELAUNCH_SESSION_ID, RelaunchSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CHANGE_SEED_ID, ChangeSeedPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CLEANUP_PLAYER_ID, CleanupPlayerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LAUNCHER_SETTINGS_ID, LauncherSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SERVER_SETTINGS_ID, ServerSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CLIENT_CONNECTION_HOST_ID, ClientConnectionHostPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TRANSITION_READY_ID, TransitionReadyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CLIENT_MATCH_READY_ID, ClientMatchReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SESSION_LIST_ID, SessionListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FREEZE_STATE_ID, FreezeStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TRANSITION_START_ID, TransitionStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MATCH_INTRO_ID, MatchIntroPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MATCH_READY_STATE_ID, MatchReadyStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MATCH_START_ID, MatchStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VELOCITY_PROXY_ID, VelocityProxyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PROTECTION_OVERLAY_ID, ProtectionOverlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BOUNTYHUNT_INVINCIBILITY_ID, BountyHuntInvincibilityPayload.CODEC);

        payloadTypesRegistered = true;
    }

    public record RequestSessionsPayload(String marker) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, RequestSessionsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            RequestSessionsPayload::marker,
            RequestSessionsPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return REQUEST_SESSIONS_ID;
        }
    }

    public record SessionListPayload(NbtCompound sessions) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, SessionListPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.NBT_COMPOUND,
            SessionListPayload::sessions,
            SessionListPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return SESSION_LIST_ID;
        }
    }

    public record CreateSessionPayload(String game, String name, NbtCompound plan) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, CreateSessionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            CreateSessionPayload::game,
            PacketCodecs.STRING,
            CreateSessionPayload::name,
            PacketCodecs.NBT_COMPOUND,
            CreateSessionPayload::plan,
            CreateSessionPayload::new
        );

        public CreateSessionPayload(String game, String name) {
            this(game, name, new NbtCompound());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return CREATE_SESSION_ID;
        }
    }

    public record LaunchSessionPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, LaunchSessionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            LaunchSessionPayload::sessionId,
            LaunchSessionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return LAUNCH_SESSION_ID;
        }
    }

    public record StopSessionPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, StopSessionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            StopSessionPayload::sessionId,
            StopSessionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return STOP_SESSION_ID;
        }
    }

    public record InspectSessionPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, InspectSessionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            InspectSessionPayload::sessionId,
            InspectSessionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return INSPECT_SESSION_ID;
        }
    }

    public record RelaunchSessionPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, RelaunchSessionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            RelaunchSessionPayload::sessionId,
            RelaunchSessionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return RELAUNCH_SESSION_ID;
        }
    }

    public record ChangeSeedPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, ChangeSeedPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            ChangeSeedPayload::sessionId,
            ChangeSeedPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CHANGE_SEED_ID;
        }
    }


    public record CleanupPlayerPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, CleanupPlayerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            CleanupPlayerPayload::sessionId,
            CleanupPlayerPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CLEANUP_PLAYER_ID;
        }
    }

    public record LauncherSettingsPayload(NbtCompound settings) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, LauncherSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.NBT_COMPOUND,
            LauncherSettingsPayload::settings,
            LauncherSettingsPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return LAUNCHER_SETTINGS_ID;
        }
    }

    public record ServerSettingsPayload(NbtCompound settings) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, ServerSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.NBT_COMPOUND,
            ServerSettingsPayload::settings,
            ServerSettingsPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return SERVER_SETTINGS_ID;
        }
    }

    public record ClientConnectionHostPayload(String host) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, ClientConnectionHostPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            ClientConnectionHostPayload::host,
            ClientConnectionHostPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CLIENT_CONNECTION_HOST_ID;
        }
    }

    public record FreezeStatePayload(boolean frozen) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, FreezeStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL,
            FreezeStatePayload::frozen,
            FreezeStatePayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return FREEZE_STATE_ID;
        }
    }

    public record TransitionStartPayload(String token, String context) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, TransitionStartPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            TransitionStartPayload::token,
            PacketCodecs.STRING,
            TransitionStartPayload::context,
            TransitionStartPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return TRANSITION_START_ID;
        }
    }

    public record TransitionReadyPayload(String token) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, TransitionReadyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            TransitionReadyPayload::token,
            TransitionReadyPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return TRANSITION_READY_ID;
        }
    }

    public record ClientMatchReadyPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, ClientMatchReadyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            ClientMatchReadyPayload::sessionId,
            ClientMatchReadyPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CLIENT_MATCH_READY_ID;
        }
    }

    public record MatchIntroPayload(NbtCompound data) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, MatchIntroPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.NBT_COMPOUND,
            MatchIntroPayload::data,
            MatchIntroPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return MATCH_INTRO_ID;
        }
    }

    public record MatchReadyStatePayload(String sessionId, int readyPlayers, int totalPlayers, String status, NbtCompound data) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, MatchReadyStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            MatchReadyStatePayload::sessionId,
            PacketCodecs.INTEGER,
            MatchReadyStatePayload::readyPlayers,
            PacketCodecs.INTEGER,
            MatchReadyStatePayload::totalPlayers,
            PacketCodecs.STRING,
            MatchReadyStatePayload::status,
            PacketCodecs.NBT_COMPOUND,
            MatchReadyStatePayload::data,
            MatchReadyStatePayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return MATCH_READY_STATE_ID;
        }
    }

    public record MatchStartPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, MatchStartPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            MatchStartPayload::sessionId,
            MatchStartPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return MATCH_START_ID;
        }
    }

    public record VelocityProxyPayload(byte[] data) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, VelocityProxyPayload> CODEC = PacketCodec.of(
            (payload, buffer) -> buffer.writeBytes(payload.data()),
            buffer -> {
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);
                return new VelocityProxyPayload(data);
            }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return VELOCITY_PROXY_ID;
        }
    }

    public record ProtectionOverlayPayload(java.util.UUID playerId,
                                           Identifier overlayId,
                                           int remainingTicks,
                                           boolean active,
                                           int argbColor) implements CustomPayload {
        private static final PacketCodec<RegistryByteBuf, java.util.UUID> UUID_CODEC = PacketCodec.of(
            (uuid, buffer) -> buffer.writeUuid(uuid),
            (RegistryByteBuf buffer) -> buffer.readUuid()
        );
        private static final PacketCodec<RegistryByteBuf, Identifier> IDENTIFIER_CODEC = PacketCodec.of(
            (identifier, buffer) -> buffer.writeIdentifier(identifier),
            (RegistryByteBuf buffer) -> buffer.readIdentifier()
        );
        public static final PacketCodec<RegistryByteBuf, ProtectionOverlayPayload> CODEC = PacketCodec.tuple(
            UUID_CODEC,
            ProtectionOverlayPayload::playerId,
            IDENTIFIER_CODEC,
            ProtectionOverlayPayload::overlayId,
            PacketCodecs.INTEGER,
            ProtectionOverlayPayload::remainingTicks,
            PacketCodecs.BOOL,
            ProtectionOverlayPayload::active,
            PacketCodecs.INTEGER,
            ProtectionOverlayPayload::argbColor,
            ProtectionOverlayPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return PROTECTION_OVERLAY_ID;
        }
    }

    public record BountyHuntInvincibilityPayload(java.util.UUID playerId, int remainingTicks, boolean active) implements CustomPayload {
        private static final PacketCodec<RegistryByteBuf, java.util.UUID> UUID_CODEC = PacketCodec.of(
            (uuid, buffer) -> buffer.writeUuid(uuid),
            (RegistryByteBuf buffer) -> buffer.readUuid()
        );
        public static final PacketCodec<RegistryByteBuf, BountyHuntInvincibilityPayload> CODEC = PacketCodec.tuple(
            UUID_CODEC,
             BountyHuntInvincibilityPayload::playerId,
             PacketCodecs.INTEGER,
             BountyHuntInvincibilityPayload::remainingTicks,
             PacketCodecs.BOOL,
             BountyHuntInvincibilityPayload::active,
             BountyHuntInvincibilityPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return BOUNTYHUNT_INVINCIBILITY_ID;
        }
    }
}