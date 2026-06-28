package dev.frost.miniverse.map.editor;

import dev.frost.miniverse.map.MapPosition;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MapEditorPlacementController {
    private static final Map<UUID, PlacementSession> SESSIONS = new HashMap<>();

    private MapEditorPlacementController() {
    }

    public static void register() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            PlacementSession session = SESSIONS.get(serverPlayer.getUuid());
            if (session == null) {
                return ActionResult.PASS;
            }
            if (hand == Hand.MAIN_HAND) {
                if (session.handleLeftClick(serverPlayer, pos)) {
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            PlacementSession session = SESSIONS.get(serverPlayer.getUuid());
            if (session == null) {
                return ActionResult.PASS;
            }
            if (hand == Hand.MAIN_HAND) {
                if (session.handleRightClick(serverPlayer)) {
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            PlacementSession session = SESSIONS.get(serverPlayer.getUuid());
            if (session == null) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            if (hand == Hand.MAIN_HAND) {
                if (session.handleRightClick(serverPlayer)) {
                    return TypedActionResult.fail(player.getStackInHand(hand));
                }
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }

    public static void start(ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String propertiesJson) {
        if (SESSIONS.containsKey(player.getUuid())) {
            SESSIONS.get(player.getUuid()).cancel(player);
        }

        com.google.gson.JsonObject properties;
        try {
            properties = com.google.gson.JsonParser.parseString(propertiesJson).getAsJsonObject();
        } catch (Exception e) {
            properties = new com.google.gson.JsonObject();
        }

        PlacementSession session = new PlacementSession(mapId, extension, definition, properties);
        SESSIONS.put(player.getUuid(), session);

        if (definition.type() == MarkerType.REGION) {
            session.setupBuilderInventory(player);
            player.sendMessage(Text.literal("Entered Region Builder Mode.").formatted(Formatting.GREEN), false);
        } else {
            session.setupPointInventory(player);
            if (definition.type() == MarkerType.MULTI_POINT) {
                player.sendMessage(Text.literal("Multi-point placement: left click to add points. Right click with the Barrier to finish.").formatted(Formatting.YELLOW), false);
            } else {
                player.sendMessage(Text.literal("Point placement: left click blocks to place markers. Right click with the Barrier to finish.").formatted(Formatting.YELLOW), false);
            }
        }
    }

    private static class PlacementSession {
        private final String mapId;
        private final MapEditorExtension extension;
        private final MarkerDefinition definition;
        private final com.google.gson.JsonObject properties;
        
        // Point/Multi-Point
        private final List<MapPosition> selectedPoints = new ArrayList<>();
        
        // Region Builder
        private final List<RegionPart> regionParts = new ArrayList<>();
        private final List<RegionPart> undoneParts = new ArrayList<>();
        private MapPosition regionCorner1 = null;
        private final net.minecraft.util.collection.DefaultedList<ItemStack> savedInventory = net.minecraft.util.collection.DefaultedList.ofSize(36, ItemStack.EMPTY);

        public PlacementSession(String mapId, MapEditorExtension extension, MarkerDefinition definition, com.google.gson.JsonObject properties) {
            this.mapId = mapId;
            this.extension = extension;
            this.definition = definition;
            this.properties = properties;
        }

        public void setupBuilderInventory(ServerPlayerEntity player) {
            for (int i = 0; i < 36; i++) {
                this.savedInventory.set(i, player.getInventory().getStack(i).copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
            
            ItemStack wand = new ItemStack(Items.BLAZE_ROD);
            wand.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Region Wand (Left Click / Shift+Left Click)").formatted(Formatting.GOLD));
            player.getInventory().setStack(0, wand);
            
            ItemStack undo = new ItemStack(Items.RED_DYE);
            undo.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Undo").formatted(Formatting.RED));
            player.getInventory().setStack(6, undo);
            
            ItemStack redo = new ItemStack(Items.GREEN_DYE);
            redo.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Redo").formatted(Formatting.GREEN));
            player.getInventory().setStack(7, redo);
            
            ItemStack confirm = new ItemStack(Items.LIME_DYE);
            confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Confirm").formatted(Formatting.GREEN, Formatting.BOLD));
            player.getInventory().setStack(8, confirm);
        }

        public void restoreInventory(ServerPlayerEntity player) {
            for (int i = 0; i < 36; i++) {
                player.getInventory().setStack(i, this.savedInventory.get(i));
            }
        }

        public void setupPointInventory(ServerPlayerEntity player) {
            for (int i = 0; i < 36; i++) {
                this.savedInventory.set(i, player.getInventory().getStack(i).copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
            
            ItemStack stop = new ItemStack(Items.BARRIER);
            stop.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Stop Placing").formatted(Formatting.RED, Formatting.BOLD));
            player.getInventory().setStack(8, stop);
        }

        public boolean handleLeftClick(ServerPlayerEntity player, BlockPos pos) {
            MapPosition position;
            if (this.definition.type() == MarkerType.REGION) {
                position = new MapPosition(pos.getX(), pos.getY(), pos.getZ(), 0.0F, 0.0F);
            } else {
                position = new MapPosition(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, player.getYaw(), 0.0F);
            }
            
            if (this.definition.type() == MarkerType.REGION) {
                if (player.getMainHandStack().isOf(Items.BLAZE_ROD)) {
                    if (player.isSneaking()) {
                        this.regionParts.add(new RegionPart(position, position));
                        this.undoneParts.clear();
                        this.regionCorner1 = null;
                        this.sendSummary(player, "Added Point Selection");
                    } else {
                        if (this.regionCorner1 == null) {
                            this.regionCorner1 = position;
                            this.sendSummary(player, "Position 1 set");
                        } else {
                            MapPosition a = this.regionCorner1;
                            MapPosition b = position;
                            MapPosition min = new MapPosition(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()), 0.0F, 0.0F);
                            MapPosition max = new MapPosition(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()), 0.0F, 0.0F);
                            this.regionParts.add(new RegionPart(min, max));
                            this.regionCorner1 = null;
                            this.undoneParts.clear();
                            this.sendSummary(player, "Added Region Selection");
                        }
                    }
                    return true;
                }
                return false;
            }

            this.selectedPoints.add(position);
            this.sendPointSummary(player);
            this.savePointLike(player, position);
            // SESSIONS.remove(player.getUuid()); // Allow continuous placing!
            return true;
        }

        public boolean handleRightClick(ServerPlayerEntity player) {
            if (this.definition.type() != MarkerType.REGION) {
                if (player.getMainHandStack().isOf(Items.BARRIER) || player.getMainHandStack().isEmpty()) {
                    this.cancel(player);
                    return true;
                }
                return false;
            }
            
            ItemStack stack = player.getMainHandStack();
            if (stack.isOf(Items.RED_DYE)) {
                if (!this.regionParts.isEmpty()) {
                    this.undoneParts.add(this.regionParts.removeLast());
                    this.sendSummary(player, "Undo Successful");
                } else {
                    player.sendMessage(Text.literal("Nothing to undo.").formatted(Formatting.RED), true);
                }
                return true;
            } else if (stack.isOf(Items.GREEN_DYE)) {
                if (!this.undoneParts.isEmpty()) {
                    this.regionParts.add(this.undoneParts.removeLast());
                    this.sendSummary(player, "Redo Successful");
                } else {
                    player.sendMessage(Text.literal("Nothing to redo.").formatted(Formatting.RED), true);
                }
                return true;
            } else if (stack.isOf(Items.LIME_DYE)) {
                if (this.regionParts.isEmpty()) {
                    player.sendMessage(Text.literal("Cannot confirm an empty region.").formatted(Formatting.RED), true);
                } else {
                    this.saveRegion(player);
                    this.restoreInventory(player);
                    SESSIONS.remove(player.getUuid());
                }
                return true;
            } else if (stack.isOf(Items.BLAZE_ROD)) {
                this.cancel(player);
                return true;
            }
            return false;
        }

        private void sendSummary(ServerPlayerEntity player, String prefix) {
            player.sendMessage(Text.literal(prefix + " | Current Parts: " + this.regionParts.size()).formatted(Formatting.AQUA), true);
            net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
            for (RegionPart part : this.regionParts) {
                net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
                net.minecraft.nbt.NbtCompound min = new net.minecraft.nbt.NbtCompound();
                min.putDouble("x", part.min().x());
                min.putDouble("y", part.min().y());
                min.putDouble("z", part.min().z());
                min.putFloat("yaw", 0);
                min.putFloat("pitch", 0);
                net.minecraft.nbt.NbtCompound max = new net.minecraft.nbt.NbtCompound();
                max.putDouble("x", part.max().x());
                max.putDouble("y", part.max().y());
                max.putDouble("z", part.max().z());
                max.putFloat("yaw", 0);
                max.putFloat("pitch", 0);
                nbt.put("min", min);
                nbt.put("max", max);
                list.add(nbt);
            }
            if (this.regionCorner1 != null) {
                net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
                net.minecraft.nbt.NbtCompound min = new net.minecraft.nbt.NbtCompound();
                min.putDouble("x", this.regionCorner1.x());
                min.putDouble("y", this.regionCorner1.y());
                min.putDouble("z", this.regionCorner1.z());
                min.putFloat("yaw", 0);
                min.putFloat("pitch", 0);
                net.minecraft.nbt.NbtCompound max = new net.minecraft.nbt.NbtCompound();
                max.putDouble("x", this.regionCorner1.x());
                max.putDouble("y", this.regionCorner1.y());
                max.putDouble("z", this.regionCorner1.z());
                max.putFloat("yaw", 0);
                max.putFloat("pitch", 0);
                nbt.put("min", min);
                nbt.put("max", max);
                list.add(nbt);
            }
            net.minecraft.nbt.NbtCompound payloadNbt = new net.minecraft.nbt.NbtCompound();
            payloadNbt.put("regions", list);
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.frost.miniverse.common.NetworkConstants.SYNC_BUILDER_SELECTION_ID)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.frost.miniverse.common.NetworkConstants.SyncBuilderSelectionPayload(payloadNbt));
            }
        }

        private void sendPointSummary(ServerPlayerEntity player) {
            net.minecraft.nbt.NbtList pointList = new net.minecraft.nbt.NbtList();
            for (MapPosition p : this.selectedPoints) {
                net.minecraft.nbt.NbtCompound pt = new net.minecraft.nbt.NbtCompound();
                pt.putDouble("x", p.x());
                pt.putDouble("y", p.y());
                pt.putDouble("z", p.z());
                pt.putFloat("yaw", p.yaw());
                pt.putFloat("pitch", p.pitch());
                pointList.add(pt);
            }
            net.minecraft.nbt.NbtCompound payloadNbt = new net.minecraft.nbt.NbtCompound();
            payloadNbt.put("regions", new net.minecraft.nbt.NbtList());
            payloadNbt.put("points", pointList);
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.frost.miniverse.common.NetworkConstants.SYNC_BUILDER_SELECTION_ID)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.frost.miniverse.common.NetworkConstants.SyncBuilderSelectionPayload(payloadNbt));
            }
        }

        private void cancel(ServerPlayerEntity player) {
            this.restoreInventory(player);
            SESSIONS.remove(player.getUuid());
            // Send an empty payload to clear the client's placement preview
            net.minecraft.nbt.NbtCompound emptyPayload = new net.minecraft.nbt.NbtCompound();
            emptyPayload.put("regions", new net.minecraft.nbt.NbtList());
            emptyPayload.put("points", new net.minecraft.nbt.NbtList());
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.frost.miniverse.common.NetworkConstants.SYNC_BUILDER_SELECTION_ID)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.frost.miniverse.common.NetworkConstants.SyncBuilderSelectionPayload(emptyPayload));
            }
            player.sendMessage(Text.literal("Finished marker placement.").formatted(Formatting.YELLOW), false);
        }

        private void savePointLike(ServerPlayerEntity player, MapPosition position) {
            List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(this.mapId, this.extension, this.definition));
            if (this.definition.type() == MarkerType.MULTI_POINT && !markers.isEmpty()) {
                MapMarker route = markers.getFirst();
                List<MapPosition> points = new ArrayList<>(route.points());
                points.add(position);
                markers.set(0, new MapMarker(route.id(), route.definitionKey(), route.name(), route.type(), points, List.of(), this.properties));
                this.save(player, markers, "Added point to " + this.definition.displayName() + ".");
            } else {
                if (markers.size() >= this.definition.maxCount()) {
                    markers.removeLast();
                }
                String name = this.definition.single() ? this.definition.displayName() : this.definition.displayName() + " #" + (markers.size() + 1);
                String id = java.util.UUID.randomUUID().toString();
                markers.add(new MapMarker(id, this.definition.key(), name, this.definition.type(), List.of(position), List.of(), this.properties));
                this.save(player, markers, "Placed " + this.definition.displayName() + ".");
            }
        }

        private void saveRegion(ServerPlayerEntity player) {
            List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(this.mapId, this.extension, this.definition));
            if (this.definition.single()) {
                markers.clear();
            } else if (markers.size() >= this.definition.maxCount()) {
                markers.removeLast();
            }
            String name = this.definition.single() ? this.definition.displayName() : this.definition.displayName() + " #" + (markers.size() + 1);
            markers.add(new MapMarker(UUID.randomUUID().toString(), this.definition.key(), name, MarkerType.REGION, List.of(), new ArrayList<>(this.regionParts), this.properties));
            this.save(player, markers, "Created " + this.definition.displayName() + " with " + this.regionParts.size() + " parts.");
        }

        private void save(ServerPlayerEntity player, List<MapMarker> markers, String success) {
            try {
                MapEditorMarkerStore.save(this.mapId, this.extension, this.definition, markers);
                player.sendMessage(Text.literal(success).formatted(Formatting.GREEN), false);
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.frost.miniverse.common.NetworkConstants.HIDE_MAP_EDITOR_OVERLAY_ID)) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.frost.miniverse.common.NetworkConstants.HideMapEditorOverlayPayload(this.extension.gameId(), this.definition.key()));
                }
            } catch (IOException e) {
                player.sendMessage(Text.literal("Failed to save marker: " + e.getMessage()).formatted(Formatting.RED), false);
            }
        }
    }
}
