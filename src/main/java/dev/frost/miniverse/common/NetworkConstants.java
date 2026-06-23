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
    public static final CustomPayload.Id<LaunchProgressPayload> LAUNCH_PROGRESS_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "launch_progress"));
    public static final CustomPayload.Id<CreateSessionPayload> CREATE_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_create"));
    public static final CustomPayload.Id<LaunchSessionPayload> LAUNCH_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_launch"));
    public static final CustomPayload.Id<StopSessionPayload> STOP_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_stop"));
    public static final CustomPayload.Id<PauseSessionPayload> PAUSE_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_pause"));
    public static final CustomPayload.Id<AssignMidGamePlayerPayload> ASSIGN_MID_GAME_PLAYER_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_assign_mid_game"));
    public static final CustomPayload.Id<InspectSessionPayload> INSPECT_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_inspect"));
    public static final CustomPayload.Id<CreateVoidMapPayload> CREATE_VOID_MAP_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "map_create_void"));
    public static final CustomPayload.Id<EditMapPayload> EDIT_MAP_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "map_edit"));
    public static final CustomPayload.Id<MapEditorActionPayload> MAP_EDITOR_ACTION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "map_editor_action"));
    public static final CustomPayload.Id<RelaunchSessionPayload> RELAUNCH_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_relaunch"));
    public static final CustomPayload.Id<DeleteSessionPayload> DELETE_SESSION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_delete"));
    public static final CustomPayload.Id<DeleteAllSessionsPayload> DELETE_ALL_SESSIONS_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "session_delete_all"));
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
    public static final CustomPayload.Id<CaptureThumbnailPayload> CAPTURE_THUMBNAIL_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "capture_thumbnail"));
    public static final CustomPayload.Id<DeleteMapPayload> DELETE_MAP_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "delete_map"));
    public static final CustomPayload.Id<RenameMapPayload> RENAME_MAP_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "rename_map"));
    public static final CustomPayload.Id<UpdateMapTagsPayload> UPDATE_MAP_TAGS_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "update_map_tags"));
    public static final CustomPayload.Id<HideMapEditorOverlayPayload> HIDE_MAP_EDITOR_OVERLAY_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "hide_map_editor_overlay"));

    public static final CustomPayload.Id<CreateDuelTypePayload> CREATE_DUEL_TYPE_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "create_duel_type"));
    public static final CustomPayload.Id<EditDuelTypePayload> EDIT_DUEL_TYPE_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "edit_duel_type"));
    public static final CustomPayload.Id<DeleteDuelTypePayload> DELETE_DUEL_TYPE_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "delete_duel_type"));

    public static final CustomPayload.Id<CreateKitPayload> CREATE_KIT_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "create_kit"));
    public static final CustomPayload.Id<RenameKitPayload> RENAME_KIT_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "rename_kit"));
    public static final CustomPayload.Id<DeleteKitPayload> DELETE_KIT_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "delete_kit"));
    public static final CustomPayload.Id<GiveKitPayload> GIVE_KIT_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "give_kit"));
    public static final CustomPayload.Id<LoadKitIntoInventoryPayload> LOAD_KIT_INTO_INVENTORY_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "load_kit"));
    public static final CustomPayload.Id<SyncKitsPayload> SYNC_KITS_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "sync_kits"));

    public static final CustomPayload.Id<SyncBuilderSelectionPayload> SYNC_BUILDER_SELECTION_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "sync_builder_selection"));
    public static final CustomPayload.Id<MapEditorHidePayload> MAP_EDITOR_HIDE_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "map_editor_hide"));

    public static final CustomPayload.Id<ManhuntLateJoinPayload> MANHUNT_LATE_JOIN_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "manhunt_late_join"));

    public static final CustomPayload.Id<SaveLayoutPayload> SAVE_LAYOUT_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "save_layout"));
    public static final CustomPayload.Id<ResetLayoutPayload> RESET_LAYOUT_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "reset_layout"));
    public static final CustomPayload.Id<LayoutSupportPayload> LAYOUT_SUPPORT_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "layout_support"));

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
        PayloadTypeRegistry.playC2S().register(DELETE_MAP_ID, DeleteMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RENAME_MAP_ID, RenameMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UPDATE_MAP_TAGS_ID, UpdateMapTagsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HIDE_MAP_EDITOR_OVERLAY_ID, HideMapEditorOverlayPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PAUSE_SESSION_ID, PauseSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ASSIGN_MID_GAME_PLAYER_ID, AssignMidGamePlayerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(INSPECT_SESSION_ID, InspectSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CREATE_VOID_MAP_ID, CreateVoidMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EDIT_MAP_ID, EditMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MAP_EDITOR_ACTION_ID, MapEditorActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RELAUNCH_SESSION_ID, RelaunchSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DELETE_SESSION_ID, DeleteSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DELETE_ALL_SESSIONS_ID, DeleteAllSessionsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CHANGE_SEED_ID, ChangeSeedPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CLEANUP_PLAYER_ID, CleanupPlayerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LAUNCHER_SETTINGS_ID, LauncherSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SERVER_SETTINGS_ID, ServerSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CLIENT_CONNECTION_HOST_ID, ClientConnectionHostPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TRANSITION_READY_ID, TransitionReadyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CLIENT_MATCH_READY_ID, ClientMatchReadyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SAVE_LAYOUT_ID, SaveLayoutPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RESET_LAYOUT_ID, ResetLayoutPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SESSION_LIST_ID, SessionListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LAUNCH_PROGRESS_ID, LaunchProgressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FREEZE_STATE_ID, FreezeStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TRANSITION_START_ID, TransitionStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MATCH_INTRO_ID, MatchIntroPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MATCH_READY_STATE_ID, MatchReadyStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MATCH_START_ID, MatchStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VELOCITY_PROXY_ID, VelocityProxyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PROTECTION_OVERLAY_ID, ProtectionOverlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BOUNTYHUNT_INVINCIBILITY_ID, BountyHuntInvincibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CAPTURE_THUMBNAIL_ID, CaptureThumbnailPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(CREATE_DUEL_TYPE_ID, CreateDuelTypePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EDIT_DUEL_TYPE_ID, EditDuelTypePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DELETE_DUEL_TYPE_ID, DeleteDuelTypePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CREATE_KIT_ID, CreateKitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RENAME_KIT_ID, RenameKitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DELETE_KIT_ID, DeleteKitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GIVE_KIT_ID, GiveKitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LOAD_KIT_INTO_INVENTORY_ID, LoadKitIntoInventoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SYNC_KITS_ID, SyncKitsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SYNC_BUILDER_SELECTION_ID, SyncBuilderSelectionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MAP_EDITOR_HIDE_ID, MapEditorHidePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LAYOUT_SUPPORT_ID, LayoutSupportPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(MANHUNT_LATE_JOIN_ID, ManhuntLateJoinPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MANHUNT_LATE_JOIN_ID, ManhuntLateJoinPayload.CODEC);
        
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

    public record LaunchProgressPayload(String sessionId, String title, String stage, String detail, int progress, boolean done) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, LaunchProgressPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            LaunchProgressPayload::sessionId,
            PacketCodecs.STRING,
            LaunchProgressPayload::title,
            PacketCodecs.STRING,
            LaunchProgressPayload::stage,
            PacketCodecs.STRING,
            LaunchProgressPayload::detail,
            PacketCodecs.INTEGER,
            LaunchProgressPayload::progress,
            PacketCodecs.BOOL,
            LaunchProgressPayload::done,
            LaunchProgressPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return LAUNCH_PROGRESS_ID;
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

    public record PauseSessionPayload(String sessionId, boolean paused) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, PauseSessionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            PauseSessionPayload::sessionId,
            PacketCodecs.BOOL,
            PauseSessionPayload::paused,
            PauseSessionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return PAUSE_SESSION_ID;
        }
    }

    public record AssignMidGamePlayerPayload(String sessionId, String playerUuid, String teamLabel, String role) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, AssignMidGamePlayerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            AssignMidGamePlayerPayload::sessionId,
            PacketCodecs.STRING,
            AssignMidGamePlayerPayload::playerUuid,
            PacketCodecs.STRING,
            AssignMidGamePlayerPayload::teamLabel,
            PacketCodecs.STRING,
            AssignMidGamePlayerPayload::role,
            AssignMidGamePlayerPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ASSIGN_MID_GAME_PLAYER_ID;
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

    public record CreateVoidMapPayload(String mapName) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, CreateVoidMapPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            CreateVoidMapPayload::mapName,
            CreateVoidMapPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CREATE_VOID_MAP_ID;
        }
    }

    public record EditMapPayload(String mapId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, EditMapPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            EditMapPayload::mapId,
            EditMapPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return EDIT_MAP_ID;
        }
    }

    public record MapEditorActionPayload(NbtCompound action) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, MapEditorActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.NBT_COMPOUND,
            MapEditorActionPayload::action,
            MapEditorActionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return MAP_EDITOR_ACTION_ID;
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

    public record DeleteSessionPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, DeleteSessionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            DeleteSessionPayload::sessionId,
            DeleteSessionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return DELETE_SESSION_ID;
        }
    }

    public record DeleteAllSessionsPayload(java.util.List<String> sessionIds) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, DeleteAllSessionsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.collect(PacketCodecs.toList()),
            DeleteAllSessionsPayload::sessionIds,
            DeleteAllSessionsPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return DELETE_ALL_SESSIONS_ID;
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



    public record ManhuntLateJoinPayload(java.util.Map<java.util.UUID, String> teammates) implements CustomPayload {
        private static final PacketCodec<RegistryByteBuf, java.util.UUID> UUID_CODEC = PacketCodec.of(
            (uuid, buffer) -> buffer.writeUuid(uuid),
            (RegistryByteBuf buffer) -> buffer.readUuid()
        );

        public static final PacketCodec<RegistryByteBuf, ManhuntLateJoinPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.map(java.util.HashMap::new, UUID_CODEC, PacketCodecs.STRING), ManhuntLateJoinPayload::teammates,
            ManhuntLateJoinPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return MANHUNT_LATE_JOIN_ID;
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
                                           int argbColor,
                                           String style,
                                           String renderMode,
                                           int glowColor,
                                           int outlineColor,
                                           float alpha,
                                           float intensity) implements CustomPayload {
        private static final PacketCodec<RegistryByteBuf, java.util.UUID> UUID_CODEC = PacketCodec.of(
            (uuid, buffer) -> buffer.writeUuid(uuid),
            (RegistryByteBuf buffer) -> buffer.readUuid()
        );
        private static final PacketCodec<RegistryByteBuf, Identifier> IDENTIFIER_CODEC = PacketCodec.of(
            (identifier, buffer) -> buffer.writeIdentifier(identifier),
            (RegistryByteBuf buffer) -> buffer.readIdentifier()
        );
        public static final PacketCodec<RegistryByteBuf, ProtectionOverlayPayload> CODEC = PacketCodec.of(
            (payload, buffer) -> {
                buffer.writeUuid(payload.playerId);
                buffer.writeIdentifier(payload.overlayId);
                buffer.writeInt(payload.remainingTicks);
                buffer.writeBoolean(payload.active);
                buffer.writeInt(payload.argbColor);
                buffer.writeString(payload.style);
                buffer.writeString(payload.renderMode);
                buffer.writeInt(payload.glowColor);
                buffer.writeInt(payload.outlineColor);
                buffer.writeFloat(payload.alpha);
                buffer.writeFloat(payload.intensity);
            },
            buffer -> new ProtectionOverlayPayload(
                buffer.readUuid(),
                buffer.readIdentifier(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readString(),
                buffer.readString(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readFloat(),
                buffer.readFloat()
            )
        );

        public ProtectionOverlayPayload(java.util.UUID playerId,
                                        Identifier overlayId,
                                        int remainingTicks,
                                        boolean active,
                                        int argbColor) {
            this(
                playerId,
                overlayId,
                remainingTicks,
                active,
                argbColor,
                "vanilla_glow",
                "depth_tested",
                0xFF000000 | (argbColor & 0x00FFFFFF),
                0xFFFFFFFF,
                ((argbColor >>> 24) & 0xFF) / 255.0F,
                1.0F
            );
        }

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

    public record CaptureThumbnailPayload(String path) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, CaptureThumbnailPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            CaptureThumbnailPayload::path,
            CaptureThumbnailPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CAPTURE_THUMBNAIL_ID;
        }
    }

    public record DeleteMapPayload(String mapId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, DeleteMapPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            DeleteMapPayload::mapId,
            DeleteMapPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return DELETE_MAP_ID;
        }
    }

    public record RenameMapPayload(String mapId, String newName) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, RenameMapPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, RenameMapPayload::mapId,
            PacketCodecs.STRING, RenameMapPayload::newName,
            RenameMapPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return RENAME_MAP_ID;
        }
    }

    public record HideMapEditorOverlayPayload(String gameId, String definitionKey) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, HideMapEditorOverlayPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, HideMapEditorOverlayPayload::gameId,
            PacketCodecs.STRING, HideMapEditorOverlayPayload::definitionKey,
            HideMapEditorOverlayPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return HIDE_MAP_EDITOR_OVERLAY_ID;
        }
    }

    public record UpdateMapTagsPayload(String mapId, java.util.List<String> tags) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, UpdateMapTagsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, UpdateMapTagsPayload::mapId,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), UpdateMapTagsPayload::tags,
            UpdateMapTagsPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return UPDATE_MAP_TAGS_ID;
        }
    }

    public record RenameKitPayload(String kitId, String newName) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, RenameKitPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, RenameKitPayload::kitId,
            PacketCodecs.STRING, RenameKitPayload::newName,
            RenameKitPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return RENAME_KIT_ID;
        }
    }

    public record DeleteKitPayload(String kitId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, DeleteKitPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, DeleteKitPayload::kitId,
            DeleteKitPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return DELETE_KIT_ID;
        }
    }

    public record GiveKitPayload(String kitId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, GiveKitPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, GiveKitPayload::kitId,
            GiveKitPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return GIVE_KIT_ID;
        }
    }

    public record SaveLayoutPayload(String gamemode) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, SaveLayoutPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            SaveLayoutPayload::gamemode,
            SaveLayoutPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return SAVE_LAYOUT_ID;
        }
    }

    public record ResetLayoutPayload(String gamemode) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, ResetLayoutPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            ResetLayoutPayload::gamemode,
            ResetLayoutPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return RESET_LAYOUT_ID;
        }
    }

    public record LayoutSupportPayload(String gamemode, String layoutProfile) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, LayoutSupportPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            LayoutSupportPayload::gamemode,
            PacketCodecs.STRING,
            LayoutSupportPayload::layoutProfile,
            LayoutSupportPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return LAYOUT_SUPPORT_ID;
        }
    }

    public record SyncBuilderSelectionPayload(NbtCompound selection) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, SyncBuilderSelectionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.NBT_COMPOUND,
            SyncBuilderSelectionPayload::selection,
            SyncBuilderSelectionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return SYNC_BUILDER_SELECTION_ID;
        }
    }

    /** Sent server→client immediately before a map editor quit/transfer to hide all overlays without waiting for DISCONNECT. */
    public record MapEditorHidePayload() implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, MapEditorHidePayload> CODEC = PacketCodec.unit(new MapEditorHidePayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return MAP_EDITOR_HIDE_ID;
        }
    }

    public record CreateDuelTypePayload(
        String id,
        String name,
        boolean knockbackOnly,
        boolean allowBuilding,
        boolean allowBreaking,
        boolean allowHunger,
        boolean naturalRegen
    ) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, CreateDuelTypePayload> CODEC = PacketCodec.of(
            (payload, buffer) -> {
                buffer.writeString(payload.id());
                buffer.writeString(payload.name());
                buffer.writeBoolean(payload.knockbackOnly());
                buffer.writeBoolean(payload.allowBuilding());
                buffer.writeBoolean(payload.allowBreaking());
                buffer.writeBoolean(payload.allowHunger());
                buffer.writeBoolean(payload.naturalRegen());
            },
            buffer -> new CreateDuelTypePayload(
                buffer.readString(),
                buffer.readString(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
            )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CREATE_DUEL_TYPE_ID;
        }
    }

    public record EditDuelTypePayload(
        String id,
        String name,
        boolean knockbackOnly,
        boolean allowBuilding,
        boolean allowBreaking,
        boolean allowHunger,
        boolean naturalRegen
    ) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, EditDuelTypePayload> CODEC = PacketCodec.of(
            (payload, buffer) -> {
                buffer.writeString(payload.id());
                buffer.writeString(payload.name());
                buffer.writeBoolean(payload.knockbackOnly());
                buffer.writeBoolean(payload.allowBuilding());
                buffer.writeBoolean(payload.allowBreaking());
                buffer.writeBoolean(payload.allowHunger());
                buffer.writeBoolean(payload.naturalRegen());
            },
            buffer -> new EditDuelTypePayload(
                buffer.readString(),
                buffer.readString(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
            )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return EDIT_DUEL_TYPE_ID;
        }
    }

    public record DeleteDuelTypePayload(String id) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, DeleteDuelTypePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            DeleteDuelTypePayload::id,
            DeleteDuelTypePayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return DELETE_DUEL_TYPE_ID;
        }
    }

    public record CreateKitPayload(String id, String displayName, String categories) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, CreateKitPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            CreateKitPayload::id,
            PacketCodecs.STRING,
            CreateKitPayload::displayName,
            PacketCodecs.STRING,
            CreateKitPayload::categories,
            CreateKitPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CREATE_KIT_ID;
        }
    }

    public record LoadKitIntoInventoryPayload(String id) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, LoadKitIntoInventoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            LoadKitIntoInventoryPayload::id,
            LoadKitIntoInventoryPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return LOAD_KIT_INTO_INVENTORY_ID;
        }
    }

    public record SyncKitsPayload(String jsonArrayString) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, SyncKitsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            SyncKitsPayload::jsonArrayString,
            SyncKitsPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return SYNC_KITS_ID;
        }
    }
}
