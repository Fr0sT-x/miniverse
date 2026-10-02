package dev.frost.miniverse.map.editor;

import dev.frost.miniverse.map.MapPosition;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                if (session.handleRightClick(serverPlayer, hitResult.getBlockPos())) {
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
                if (session.handleRightClick(serverPlayer, null)) {
                    return TypedActionResult.fail(player.getStackInHand(hand));
                }
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }

    public static void start(ServerPlayerEntity player, String mapId, MapEditorExtension extension, MarkerDefinition definition, String name, String propertiesJson) {
        if (SESSIONS.containsKey(player.getUuid())) {
            SESSIONS.get(player.getUuid()).cancel(player);
        }

        com.google.gson.JsonObject properties;
        try {
            properties = com.google.gson.JsonParser.parseString(propertiesJson).getAsJsonObject();
        } catch (Exception e) {
            properties = new com.google.gson.JsonObject();
        }

        PlacementSession session = new PlacementSession(mapId, extension, definition, name, properties);
        SESSIONS.put(player.getUuid(), session);

        if (definition.type() == MarkerType.REGION) {
            session.setupBuilderInventory(player);
            player.sendMessage(Text.literal("§6§l[REGION BUILDER] §aEntered Region Builder Mode.").formatted(Formatting.GREEN), false);
            player.sendMessage(Text.literal("§eLeft-Click: §fPos 1 §7| §eRight-Click: §fPos 2 §7| §eShift+Left: §f+Block §7| §a[Lime Dye]: §fConfirm").formatted(Formatting.YELLOW), true);
        } else {
            session.setupPointInventory(player);
            if (definition.type() == MarkerType.MULTI_POINT) {
                player.sendMessage(Text.literal("§e§l[POINT PLACER] §7Left-click blocks to add route points. Right-click [Lime Dye] to save, [Barrier] to cancel.").formatted(Formatting.YELLOW), false);
            } else if (definition.maxCount() > 1) {
                player.sendMessage(Text.literal("§e§l[POINT PLACER] §7Left-click blocks to place " + definition.displayName() + " points (up to " + definition.maxCount() + "). Right-click [Lime Dye] to save, [Barrier] to cancel.").formatted(Formatting.YELLOW), false);
            } else {
                player.sendMessage(Text.literal("§e§l[POINT PLACER] §7Left-click a block to position " + definition.displayName() + ". Right-click [Lime Dye] to save, [Barrier] to cancel.").formatted(Formatting.YELLOW), false);
            }
            if (definition.maxCount() > 1 || definition.type() == MarkerType.MULTI_POINT) {
                player.sendMessage(Text.literal("§6[Placing: " + definition.displayName() + "] §eLeft-Click: §fAdd Point | §a[Lime Dye]: §fSave | §c[Barrier]: §fCancel").formatted(Formatting.GOLD), true);
            } else {
                player.sendMessage(Text.literal("§6[Placing: " + definition.displayName() + "] §eLeft-Click: §fPosition | §a[Lime Dye]: §fSave | §c[Barrier]: §fCancel").formatted(Formatting.GOLD), true);
            }
        }
    }

    private static class PlacementSession {
        private final String mapId;
        private final MapEditorExtension extension;
        private final MarkerDefinition definition;
        private final String name;
        private final com.google.gson.JsonObject properties;

        // Debounce
        private long lastLeftClickTime = 0;
        private long lastRightClickTime = 0;

        // Point/Multi-Point
        private final List<MapPosition> selectedPoints = new ArrayList<>();
        private final List<List<MapPosition>> pointUndoHistory = new ArrayList<>();
        private final List<List<MapPosition>> pointRedoHistory = new ArrayList<>();

        // Region Builder
        private final List<RegionPart> regionParts = new ArrayList<>();
        private final List<List<RegionPart>> undoHistory = new ArrayList<>();
        private final List<List<RegionPart>> redoHistory = new ArrayList<>();
        private MapPosition regionCorner1 = null;
        private final net.minecraft.util.collection.DefaultedList<ItemStack> savedInventory = net.minecraft.util.collection.DefaultedList.ofSize(36, ItemStack.EMPTY);

        public PlacementSession(String mapId, MapEditorExtension extension, MarkerDefinition definition, String name, com.google.gson.JsonObject properties) {
            this.mapId = mapId;
            this.extension = extension;
            this.definition = definition;
            this.name = name;
            this.properties = properties;
        }

        private static ItemStack createTool(ItemConvertible item, Text name, List<Text> lore) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponentTypes.CUSTOM_NAME, name);
            if (lore != null && !lore.isEmpty()) {
                stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
            }
            return stack;
        }

        public void setupBuilderInventory(ServerPlayerEntity player) {
            for (int i = 0; i < 36; i++) {
                this.savedInventory.set(i, player.getInventory().getStack(i).copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }

            // Slot 0: Region Wand
            player.getInventory().setStack(0, createTool(
                Items.BLAZE_ROD,
                Text.literal("Region Wand").formatted(Formatting.GOLD, Formatting.BOLD),
                List.of(
                    Text.literal("Left-Click: ").formatted(Formatting.YELLOW).append(Text.literal("Set Corner 1").formatted(Formatting.WHITE)),
                    Text.literal("Right-Click: ").formatted(Formatting.YELLOW).append(Text.literal("Set Corner 2 (creates 3D box)").formatted(Formatting.WHITE)),
                    Text.literal("Shift + Left-Click: ").formatted(Formatting.AQUA).append(Text.literal("Add single block (1x1x1)").formatted(Formatting.WHITE)),
                    Text.literal("Shift + Right-Click: ").formatted(Formatting.RED).append(Text.literal("Carve/remove clicked block").formatted(Formatting.WHITE))
                )
            ));

            // Slot 1: Region Eraser
            player.getInventory().setStack(1, createTool(
                Items.SHEARS,
                Text.literal("Region Eraser").formatted(Formatting.RED, Formatting.BOLD),
                List.of(
                    Text.literal("Left or Right-Click: ").formatted(Formatting.RED).append(Text.literal("Remove targeted block").formatted(Formatting.WHITE)),
                    Text.literal("Click any highlighted block to erase it.").formatted(Formatting.GRAY)
                )
            ));

            // Slot 4: Cancel
            player.getInventory().setStack(4, createTool(
                Items.BARRIER,
                Text.literal("Cancel Placement").formatted(Formatting.DARK_RED, Formatting.BOLD),
                List.of(
                    Text.literal("Right-Click: ").formatted(Formatting.RED).append(Text.literal("Discard changes and exit").formatted(Formatting.WHITE))
                )
            ));

            // Slot 6: Undo
            player.getInventory().setStack(6, createTool(
                Items.RED_DYE,
                Text.literal("Undo").formatted(Formatting.RED, Formatting.BOLD),
                List.of(
                    Text.literal("Right-Click: ").formatted(Formatting.YELLOW).append(Text.literal("Undo last action").formatted(Formatting.WHITE))
                )
            ));

            // Slot 7: Redo
            player.getInventory().setStack(7, createTool(
                Items.GREEN_DYE,
                Text.literal("Redo").formatted(Formatting.GREEN, Formatting.BOLD),
                List.of(
                    Text.literal("Right-Click: ").formatted(Formatting.YELLOW).append(Text.literal("Redo last action").formatted(Formatting.WHITE))
                )
            ));

            // Slot 8: Confirm & Save
            player.getInventory().setStack(8, createTool(
                Items.LIME_DYE,
                Text.literal("Confirm & Save").formatted(Formatting.GREEN, Formatting.BOLD),
                List.of(
                    Text.literal("Right-Click: ").formatted(Formatting.GREEN).append(Text.literal("Save region and finish").formatted(Formatting.WHITE))
                )
            ));
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

            // Slot 0: Point Placer Compass
            player.getInventory().setStack(0, createTool(
                Items.COMPASS,
                Text.literal("Point Placer: " + this.definition.displayName()).formatted(Formatting.GOLD, Formatting.BOLD),
                List.of(
                    Text.literal("Left-Click a block: ").formatted(Formatting.YELLOW).append(Text.literal(this.definition.type() == MarkerType.MULTI_POINT || this.definition.maxCount() > 1 ? "Add point" : "Set point position").formatted(Formatting.WHITE)),
                    Text.literal("Right-Click [Lime Dye]: ").formatted(Formatting.GREEN).append(Text.literal("Save and finish").formatted(Formatting.WHITE)),
                    Text.literal("Right-Click [Barrier]: ").formatted(Formatting.RED).append(Text.literal("Cancel without saving").formatted(Formatting.WHITE))
                )
            ));

            // Slot 4: Cancel (Barrier)
            player.getInventory().setStack(4, createTool(
                Items.BARRIER,
                Text.literal("Cancel Placement").formatted(Formatting.DARK_RED, Formatting.BOLD),
                List.of(
                    Text.literal("Right-Click: ").formatted(Formatting.RED).append(Text.literal("Exit without saving").formatted(Formatting.WHITE))
                )
            ));

            // Slot 6: Undo (Red Dye)
            player.getInventory().setStack(6, createTool(
                Items.RED_DYE,
                Text.literal("Undo").formatted(Formatting.RED, Formatting.BOLD),
                List.of(
                    Text.literal("Right-Click: ").formatted(Formatting.YELLOW).append(Text.literal("Revert previous point change").formatted(Formatting.WHITE))
                )
            ));

            // Slot 7: Redo (Green Dye)
            player.getInventory().setStack(7, createTool(
                Items.GREEN_DYE,
                Text.literal("Redo").formatted(Formatting.GREEN, Formatting.BOLD),
                List.of(
                    Text.literal("Right-Click: ").formatted(Formatting.YELLOW).append(Text.literal("Restore undone point change").formatted(Formatting.WHITE))
                )
            ));

            // Slot 8: Confirm & Save (Lime Dye)
            player.getInventory().setStack(8, createTool(
                Items.LIME_DYE,
                Text.literal("Confirm & Save").formatted(Formatting.GREEN, Formatting.BOLD),
                List.of(
                    Text.literal("Right-Click: ").formatted(Formatting.GREEN).append(Text.literal("Save marker and finish").formatted(Formatting.WHITE))
                )
            ));
        }

        private void pushHistory() {
            List<RegionPart> copy = new ArrayList<>();
            for (RegionPart p : this.regionParts) {
                copy.add(new RegionPart(p.min(), p.max()));
            }
            this.undoHistory.add(copy);
            this.redoHistory.clear();
        }

        private void pushPointHistory() {
            List<MapPosition> copy = new ArrayList<>(this.selectedPoints);
            this.pointUndoHistory.add(copy);
            this.pointRedoHistory.clear();
        }

        public boolean handleLeftClick(ServerPlayerEntity player, BlockPos pos) {
            long now = System.currentTimeMillis();
            if (now - this.lastLeftClickTime < 300) {
                return true; // Debounce duplicate packets
            }
            this.lastLeftClickTime = now;

            if (this.definition.type() == MarkerType.REGION) {
                ItemStack hand = player.getMainHandStack();

                // 1. Eraser Tool (Shears)
                if (hand.isOf(Items.SHEARS)) {
                    this.carveBlock(player, pos);
                    return true;
                }

                // 2. Region Wand (Blaze Rod)
                if (hand.isOf(Items.BLAZE_ROD)) {
                    MapPosition blockPos = new MapPosition(pos.getX(), pos.getY(), pos.getZ(), 0.0F, 0.0F);

                    // Shift + Left Click: Add single 1x1x1 block
                    if (player.isSneaking()) {
                        this.pushHistory();
                        this.regionParts.add(new RegionPart(blockPos, blockPos));
                        this.regionCorner1 = null;
                        player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
                        this.sendSummary(player, "§a+ Added Block (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
                        return true;
                    }

                    // Normal Left Click: Set Corner 1
                    this.regionCorner1 = blockPos;
                    player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, 1.2f);
                    this.sendSummary(player, "§b✓ Corner 1 set (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") — Right-click Corner 2!");
                    return true;
                }
                return false;
            }

            // POINT PLACEMENT
            boolean isNpc = (this.definition.key() != null && this.definition.key().toLowerCase().contains("npc"))
                || (this.definition.displayName() != null && this.definition.displayName().toLowerCase().contains("npc"));
            float markerYaw = isNpc ? net.minecraft.util.math.MathHelper.wrapDegrees(player.getYaw() + 180.0F) : player.getYaw();
            MapPosition position = new MapPosition(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, markerYaw, 0.0F);

            if (this.definition.type() == MarkerType.MULTI_POINT) {
                this.pushPointHistory();
                this.selectedPoints.add(position);
                player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f + (this.selectedPoints.size() * 0.05f));
                this.sendPointSummary(player);
                player.sendMessage(Text.literal("§aAdded point #" + this.selectedPoints.size() + " at (" + (pos.getX() + 0.5) + ", " + (pos.getY() + 1.0) + ", " + (pos.getZ() + 0.5) + ") — §eRight-click [Lime Dye] to save!").formatted(Formatting.GREEN), true);
            } else if (this.definition.maxCount() > 1) {
                List<MapMarker> existing = MapEditorMarkerStore.load(this.mapId, this.extension, this.definition);
                int totalPoints = existing.size() + this.selectedPoints.size();
                if (totalPoints >= this.definition.maxCount()) {
                    player.sendMessage(Text.literal("§cYou already placed " + totalPoints + " points.").formatted(Formatting.RED), false);
                    player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8f, 1.0f);
                    return true;
                }
                this.pushPointHistory();
                this.selectedPoints.add(position);
                int currentTotal = existing.size() + this.selectedPoints.size();
                player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f + (this.selectedPoints.size() * 0.05f));
                this.sendPointSummary(player);
                if (currentTotal >= this.definition.maxCount()) {
                    player.sendMessage(Text.literal("§cYou already placed " + currentTotal + " points.").formatted(Formatting.RED), false);
                }
                player.sendMessage(Text.literal("§aAdded point #" + currentTotal + " (" + this.selectedPoints.size() + " in session) — §eRight-click [Lime Dye] to save!").formatted(Formatting.GREEN), true);
            } else {
                this.pushPointHistory();
                this.selectedPoints.clear();
                this.selectedPoints.add(position);
                player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
                this.sendPointSummary(player);
                player.sendMessage(Text.literal("§aPosition set (" + (pos.getX() + 0.5) + ", " + (pos.getY() + 1.0) + ", " + (pos.getZ() + 0.5) + ") — §eRight-click [Lime Dye] to save!").formatted(Formatting.GREEN), true);
            }
            return true;
        }

        public boolean handleRightClick(ServerPlayerEntity player, BlockPos pos) {
            long now = System.currentTimeMillis();
            if (now - this.lastRightClickTime < 250) {
                return true; // Debounce
            }
            this.lastRightClickTime = now;

            ItemStack stack = player.getMainHandStack();

            // 1. Confirm & Save (Lime Dye)
            if (stack.isOf(Items.LIME_DYE)) {
                if (this.definition.type() == MarkerType.REGION) {
                    if (this.regionParts.isEmpty()) {
                        player.sendMessage(Text.literal("§cCannot confirm an empty region.").formatted(Formatting.RED), true);
                        player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.7f, 1.0f);
                    } else {
                        this.saveRegion(player);
                    }
                } else {
                    if (this.selectedPoints.isEmpty()) {
                        player.sendMessage(Text.literal("§cSet a point first by Left-Clicking a block!").formatted(Formatting.RED), true);
                        player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.7f, 1.0f);
                    } else {
                        this.savePoints(player);
                    }
                }
                return true;
            }

            // 2. Cancel (Barrier)
            if (stack.isOf(Items.BARRIER)) {
                this.cancel(player);
                return true;
            }

            // 3. Undo (Red Dye)
            if (stack.isOf(Items.RED_DYE)) {
                if (this.definition.type() == MarkerType.REGION) {
                    this.undo(player);
                } else {
                    this.undoPoint(player);
                }
                return true;
            }

            // 4. Redo (Green Dye)
            if (stack.isOf(Items.GREEN_DYE)) {
                if (this.definition.type() == MarkerType.REGION) {
                    this.redo(player);
                } else {
                    this.redoPoint(player);
                }
                return true;
            }

            // 5. Eraser Tool (Shears)
            if (stack.isOf(Items.SHEARS)) {
                if (pos != null) {
                    this.carveBlock(player, pos);
                }
                return true;
            }

            // 6. Region Wand (Blaze Rod)
            if (stack.isOf(Items.BLAZE_ROD)) {
                if (pos == null) {
                    return false;
                }

                // Shift + Right-Click: Targeted Block Removal
                if (player.isSneaking()) {
                    this.carveBlock(player, pos);
                    return true;
                }

                // Normal Right-Click: Set Corner 2 & Form Box
                if (this.regionCorner1 == null) {
                    player.sendMessage(Text.literal("§cSet Corner 1 first by Left-Clicking a block!").formatted(Formatting.RED), true);
                    player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.7f, 1.0f);
                    return true;
                }

                MapPosition blockPos = new MapPosition(pos.getX(), pos.getY(), pos.getZ(), 0.0F, 0.0F);
                MapPosition a = this.regionCorner1;
                MapPosition b = blockPos;
                MapPosition min = new MapPosition(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()), 0.0F, 0.0F);
                MapPosition max = new MapPosition(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()), 0.0F, 0.0F);

                this.pushHistory();
                this.regionParts.add(new RegionPart(min, max));
                this.regionCorner1 = null;

                int dx = (int) (max.x() - min.x() + 1);
                int dy = (int) (max.y() - min.y() + 1);
                int dz = (int) (max.z() - min.z() + 1);
                player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.8f);
                this.sendSummary(player, "§a✓ Added Box (" + dx + "x" + dy + "x" + dz + ")");
                return true;
            }

            return false;
        }

        private boolean carveBlock(ServerPlayerEntity player, BlockPos pos) {
            double px = pos.getX();
            double py = pos.getY();
            double pz = pos.getZ();
            MapPosition target = new MapPosition(px, py, pz, 0, 0);

            boolean found = false;
            List<RegionPart> updated = new ArrayList<>();

            for (RegionPart part : this.regionParts) {
                if (!part.contains(target)) {
                    updated.add(part);
                    continue;
                }

                found = true;
                // If this is a 1x1x1 single block part, removing it leaves 0 sub-boxes
                if (part.min().x() == part.max().x() &&
                    part.min().y() == part.max().y() &&
                    part.min().z() == part.max().z()) {
                    continue;
                }

                // Subdivide the box around the carved block (3D CSG subtraction)
                // 1. -X side
                if (part.min().x() < px) {
                    updated.add(new RegionPart(
                        part.min(),
                        new MapPosition(px - 1, part.max().y(), part.max().z(), 0, 0)
                    ));
                }
                // 2. +X side
                if (part.max().x() > px) {
                    updated.add(new RegionPart(
                        new MapPosition(px + 1, part.min().y(), part.min().z(), 0, 0),
                        part.max()
                    ));
                }
                // 3. -Y side (for X == px)
                if (part.min().y() < py) {
                    updated.add(new RegionPart(
                        new MapPosition(px, part.min().y(), part.min().z(), 0, 0),
                        new MapPosition(px, py - 1, part.max().z(), 0, 0)
                    ));
                }
                // 4. +Y side (for X == px)
                if (part.max().y() > py) {
                    updated.add(new RegionPart(
                        new MapPosition(px, py + 1, part.min().z(), 0, 0),
                        new MapPosition(px, part.max().y(), part.max().z(), 0, 0)
                    ));
                }
                // 5. -Z side (for X == px, Y == py)
                if (part.min().z() < pz) {
                    updated.add(new RegionPart(
                        new MapPosition(px, py, part.min().z(), 0, 0),
                        new MapPosition(px, py, pz - 1, 0, 0)
                    ));
                }
                // 6. +Z side (for X == px, Y == py)
                if (part.max().z() > pz) {
                    updated.add(new RegionPart(
                        new MapPosition(px, py, pz + 1, 0, 0),
                        new MapPosition(px, py, part.max().z(), 0, 0)
                    ));
                }
            }

            if (found) {
                this.pushHistory();
                this.regionParts.clear();
                this.regionParts.addAll(updated);
                player.playSoundToPlayer(SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 0.7f, 1.5f);
                this.sendSummary(player, "§c- Removed Block (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
                return true;
            } else {
                player.sendMessage(Text.literal("§cTargeted block is not part of the active selection.").formatted(Formatting.RED), true);
                player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6f, 1.2f);
                return false;
            }
        }

        private void undo(ServerPlayerEntity player) {
            if (this.undoHistory.isEmpty()) {
                player.sendMessage(Text.literal("§cNothing to undo.").formatted(Formatting.RED), true);
                player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6f, 1.0f);
                return;
            }

            List<RegionPart> currentCopy = new ArrayList<>();
            for (RegionPart p : this.regionParts) {
                currentCopy.add(new RegionPart(p.min(), p.max()));
            }
            this.redoHistory.add(currentCopy);

            List<RegionPart> previous = this.undoHistory.remove(this.undoHistory.size() - 1);
            this.regionParts.clear();
            this.regionParts.addAll(previous);
            this.regionCorner1 = null;

            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 1.0f);
            this.sendSummary(player, "§e↩ Undo successful");
        }

        private void redo(ServerPlayerEntity player) {
            if (this.redoHistory.isEmpty()) {
                player.sendMessage(Text.literal("§cNothing to redo.").formatted(Formatting.RED), true);
                player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6f, 1.0f);
                return;
            }

            List<RegionPart> currentCopy = new ArrayList<>();
            for (RegionPart p : this.regionParts) {
                currentCopy.add(new RegionPart(p.min(), p.max()));
            }
            this.undoHistory.add(currentCopy);

            List<RegionPart> next = this.redoHistory.remove(this.redoHistory.size() - 1);
            this.regionParts.clear();
            this.regionParts.addAll(next);
            this.regionCorner1 = null;

            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
            this.sendSummary(player, "§a↪ Redo successful");
        }

        private void sendSummary(ServerPlayerEntity player, String prefix) {
            player.sendMessage(Text.literal(prefix + " §8| §7Parts: §b" + this.regionParts.size() + " §8| §a[Lime Dye] Save").formatted(Formatting.AQUA), true);

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

        private void undoPoint(ServerPlayerEntity player) {
            if (this.pointUndoHistory.isEmpty()) {
                player.sendMessage(Text.literal("§7Nothing to undo.").formatted(Formatting.GRAY), true);
                player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.2f);
                return;
            }
            List<MapPosition> prev = this.pointUndoHistory.remove(this.pointUndoHistory.size() - 1);
            this.pointRedoHistory.add(new ArrayList<>(this.selectedPoints));
            this.selectedPoints.clear();
            this.selectedPoints.addAll(prev);
            player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.1f);
            this.sendPointSummary(player);
            player.sendMessage(Text.literal("§eUndid point action (Points: " + this.selectedPoints.size() + ")").formatted(Formatting.YELLOW), true);
        }

        private void redoPoint(ServerPlayerEntity player) {
            if (this.pointRedoHistory.isEmpty()) {
                player.sendMessage(Text.literal("§7Nothing to redo.").formatted(Formatting.GRAY), true);
                player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.2f);
                return;
            }
            List<MapPosition> next = this.pointRedoHistory.remove(this.pointRedoHistory.size() - 1);
            this.pointUndoHistory.add(new ArrayList<>(this.selectedPoints));
            this.selectedPoints.clear();
            this.selectedPoints.addAll(next);
            player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.3f);
            this.sendPointSummary(player);
            player.sendMessage(Text.literal("§aRedid point action (Points: " + this.selectedPoints.size() + ")").formatted(Formatting.GREEN), true);
        }

        private void cleanup(ServerPlayerEntity player) {
            this.restoreInventory(player);
            SESSIONS.remove(player.getUuid());
            // Send an empty payload to clear the client's placement preview
            net.minecraft.nbt.NbtCompound emptyPayload = new net.minecraft.nbt.NbtCompound();
            emptyPayload.put("regions", new net.minecraft.nbt.NbtList());
            emptyPayload.put("points", new net.minecraft.nbt.NbtList());
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.frost.miniverse.common.NetworkConstants.SYNC_BUILDER_SELECTION_ID)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.frost.miniverse.common.NetworkConstants.SyncBuilderSelectionPayload(emptyPayload));
            }
        }

        public void cancel(ServerPlayerEntity player) {
            this.cleanup(player);
            player.sendMessage(Text.literal("§cCancelled marker placement.").formatted(Formatting.RED), false);
            player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.7f, 1.0f);
        }

        public void finish(ServerPlayerEntity player) {
            this.cleanup(player);
            player.sendMessage(Text.literal("§aSaved and finished marker placement.").formatted(Formatting.GREEN), false);
            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.9f, 1.2f);
            dev.frost.miniverse.session.SessionListSerializer.sendSessionList(player.server, player);
        }

        private void savePoints(ServerPlayerEntity player) {
            if (this.selectedPoints.isEmpty()) {
                player.sendMessage(Text.literal("§cCannot save: no point has been set.").formatted(Formatting.RED), true);
                player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.7f, 1.0f);
                return;
            }

            List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(this.mapId, this.extension, this.definition));
            if (this.definition.type() == MarkerType.MULTI_POINT) {
                if (!markers.isEmpty()) {
                    MapMarker route = markers.getFirst();
                    List<MapPosition> points = new ArrayList<>(route.points());
                    points.addAll(this.selectedPoints);
                    markers.set(0, new MapMarker(route.id(), route.definitionKey(), route.name(), route.type(), points, List.of(), this.properties));
                    this.save(player, markers, "Added " + this.selectedPoints.size() + " points to " + this.definition.displayName() + ".");
                } else {
                    String markerName = this.name != null && !this.name.isBlank() ? this.name : this.computeMarkerName(markers);
                    String id = UUID.randomUUID().toString();
                    markers.add(new MapMarker(id, this.definition.key(), markerName, this.definition.type(), new ArrayList<>(this.selectedPoints), List.of(), this.properties));
                    this.save(player, markers, "Saved " + markerName + " with " + this.selectedPoints.size() + " points.");
                }
            } else if (this.definition.maxCount() > 1) {
                // Multi-allowed POINT marker (e.g. Player Spawns)
                int addedCount = 0;
                int startIdx = 1;
                for (MapPosition pt : this.selectedPoints) {
                    if (markers.size() >= this.definition.maxCount()) {
                        break;
                    }
                    String markerName;
                    if (this.name != null && !this.name.isBlank()) {
                        markerName = this.selectedPoints.size() == 1 ? this.name : this.name + " #" + startIdx++;
                    } else {
                        markerName = this.computeMarkerName(markers);
                    }
                    String id = UUID.randomUUID().toString();
                    markers.add(new MapMarker(id, this.definition.key(), markerName, this.definition.type(), List.of(pt), List.of(), this.properties));
                    addedCount++;
                }
                this.save(player, markers, "Saved " + addedCount + " " + this.definition.displayName() + " point(s).");
            } else {
                if (this.definition.single()) {
                    markers.clear();
                } else if (markers.size() >= this.definition.maxCount() && this.definition.maxCount() > 0) {
                    markers.remove(markers.size() - 1);
                }
                String markerName = this.name != null && !this.name.isBlank() ? this.name : this.computeMarkerName(markers);
                String id = UUID.randomUUID().toString();
                MapPosition finalPos = this.selectedPoints.get(this.selectedPoints.size() - 1);
                markers.add(new MapMarker(id, this.definition.key(), markerName, this.definition.type(), List.of(finalPos), List.of(), this.properties));
                this.save(player, markers, "Saved " + markerName + ".");
            }
            this.finish(player);
        }

        private void saveRegion(ServerPlayerEntity player) {
            List<MapMarker> markers = new ArrayList<>(MapEditorMarkerStore.load(this.mapId, this.extension, this.definition));
            if (this.definition.single()) {
                markers.clear();
            } else if (markers.size() >= this.definition.maxCount() && this.definition.maxCount() > 0) {
                markers.remove(markers.size() - 1);
            }
            String markerName = this.name;
            if (markerName == null || markerName.isBlank()) {
                markerName = this.computeMarkerName(markers);
            }
            markers.add(new MapMarker(UUID.randomUUID().toString(), this.definition.key(), markerName, MarkerType.REGION, List.of(), new ArrayList<>(this.regionParts), this.properties));
            this.save(player, markers, "Created " + markerName + " with " + this.regionParts.size() + " parts.");
            this.finish(player);
        }

        private String computeMarkerName(List<MapMarker> markers) {
            if (this.definition.single()) {
                return this.definition.displayName();
            }

            // Check if this is a child marker of a logical parent (e.g. level_config)
            if (this.definition.grouping() != null && "LOGICAL".equals(this.definition.grouping().type())) {
                String propKey = this.definition.grouping().propertyKey();
                if (propKey != null && this.properties.has(propKey)) {
                    String parentId = this.properties.get(propKey).getAsString();
                    String parentKey = this.definition.grouping().parentKey();
                    Optional<MarkerDefinition> parentDef = this.extension.markers().stream()
                        .filter(m -> m.key().equals(parentKey))
                        .findFirst();
                    if (parentDef.isPresent()) {
                        List<MapMarker> parents = MapEditorMarkerStore.load(this.mapId, this.extension, parentDef.get());
                        for (MapMarker parent : parents) {
                            if (parent.id().equals(parentId)) {
                                return parent.name() + " " + this.definition.displayName();
                            }
                        }
                    }
                }
            }

            // Smart lowest-unused numbering for general multi-markers:
            // Extract existing numbers from: "DisplayName #1", "DisplayName #2", etc.
            Set<Integer> usedNumbers = new HashSet<>();
            String prefix = this.definition.displayName() + " #";
            for (MapMarker m : markers) {
                if (m.name().startsWith(prefix)) {
                    try {
                        int num = Integer.parseInt(m.name().substring(prefix.length()).trim());
                        usedNumbers.add(num);
                    } catch (NumberFormatException ignored) {}
                }
            }
            int index = 1;
            while (usedNumbers.contains(index)) {
                index++;
            }
            return this.definition.displayName() + " #" + index;
        }

        private void save(ServerPlayerEntity player, List<MapMarker> markers, String success) {
            try {
                MapEditorUndoManager.push(this.mapId, this.extension.gameId());
                MapEditorMarkerStore.save(this.mapId, this.extension, this.definition, markers);
                player.sendMessage(Text.literal(success).formatted(Formatting.GREEN), false);
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.frost.miniverse.common.NetworkConstants.HIDE_MAP_EDITOR_OVERLAY_ID)) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.frost.miniverse.common.NetworkConstants.HideMapEditorOverlayPayload(this.extension.gameId(), this.definition.key()));
                }
                dev.frost.miniverse.session.SessionListSerializer.sendSessionList(player.server, player);
            } catch (IOException e) {
                player.sendMessage(Text.literal("Failed to save marker: " + e.getMessage()).formatted(Formatting.RED), false);
            }
        }
    }
}
