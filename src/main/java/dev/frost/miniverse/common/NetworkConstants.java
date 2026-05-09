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
    public static final CustomPayload.Id<GrantOpPayload> GRANT_OP_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "grant_op"));
    public static final CustomPayload.Id<CleanupPlayerPayload> CLEANUP_PLAYER_ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "cleanup_player"));

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
        PayloadTypeRegistry.playC2S().register(GRANT_OP_ID, GrantOpPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CLEANUP_PLAYER_ID, CleanupPlayerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SESSION_LIST_ID, SessionListPayload.CODEC);

        payloadTypesRegistered = true;
    }

    public record RequestSessionsPayload(String marker) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, RequestSessionsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.cast(),
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
            PacketCodecs.NBT_COMPOUND.cast(),
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
            PacketCodecs.STRING.cast(),
            CreateSessionPayload::game,
            PacketCodecs.STRING.cast(),
            CreateSessionPayload::name,
            PacketCodecs.NBT_COMPOUND.cast(),
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
            PacketCodecs.STRING.cast(),
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
            PacketCodecs.STRING.cast(),
            StopSessionPayload::sessionId,
            StopSessionPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return STOP_SESSION_ID;
        }
    }

    public record GrantOpPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, GrantOpPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.cast(),
            GrantOpPayload::sessionId,
            GrantOpPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return GRANT_OP_ID;
        }
    }

    public record CleanupPlayerPayload(String sessionId) implements CustomPayload {
        public static final PacketCodec<RegistryByteBuf, CleanupPlayerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.cast(),
            CleanupPlayerPayload::sessionId,
            CleanupPlayerPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return CLEANUP_PLAYER_ID;
        }
    }
}



