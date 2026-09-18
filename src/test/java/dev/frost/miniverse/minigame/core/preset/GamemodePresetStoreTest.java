package dev.frost.miniverse.minigame.core.preset;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

public class GamemodePresetStoreTest {

    @Test
    public void testSanitizeFileName() {
        Assert.assertEquals("preset", GamemodePresetStore.sanitizeFileName(null));
        Assert.assertEquals("preset", GamemodePresetStore.sanitizeFileName("   "));
        Assert.assertEquals("Normal_Test", GamemodePresetStore.sanitizeFileName("Normal Test"));
        Assert.assertEquals("My_Special__Name", GamemodePresetStore.sanitizeFileName("My/Special: Name"));
    }

    @Test
    public void testPresetSaveLoadOverwriteDelete() {
        String gameId = "test_gamemode";
        String presetName = "Unit Test Preset";

        // Clean up first if needed
        GamemodePresetStore.deletePreset(gameId, presetName);

        NbtCompound settings = new NbtCompound();
        settings.putInt("startGold", 1234);
        settings.putString("difficulty", "HARD");
        settings.putBoolean("friendlyFire", true);

        GamemodePreset preset = new GamemodePreset(gameId, presetName, 1000L, 2000L, settings);

        // 1. Initial save should succeed
        boolean saved = GamemodePresetStore.savePreset(preset, false);
        Assert.assertTrue("Save should succeed", saved);

        // 2. Save without overwrite flag should fail
        boolean secondSaveNoOverwrite = GamemodePresetStore.savePreset(preset, false);
        Assert.assertFalse("Save without overwrite should fail when file exists", secondSaveNoOverwrite);

        // 3. Save with overwrite flag should succeed
        settings.putInt("startGold", 9999);
        GamemodePreset updatedPreset = new GamemodePreset(gameId, presetName, 1000L, 3000L, settings);
        boolean secondSaveWithOverwrite = GamemodePresetStore.savePreset(updatedPreset, true);
        Assert.assertTrue("Save with overwrite should succeed", secondSaveWithOverwrite);

        // 4. Retrieve preset
        Optional<GamemodePreset> loadedOpt = GamemodePresetStore.getPreset(gameId, presetName);
        Assert.assertTrue("Loaded preset should be present", loadedOpt.isPresent());
        GamemodePreset loaded = loadedOpt.get();
        Assert.assertEquals(presetName, loaded.name());
        Assert.assertEquals(gameId, loaded.gameId());
        Assert.assertEquals(9999, loaded.settings().getInt("startGold"));
        Assert.assertEquals("HARD", loaded.settings().getString("difficulty"));
        Assert.assertTrue(loaded.settings().getBoolean("friendlyFire"));

        // 5. Listing presets should include it
        List<GamemodePreset> presets = GamemodePresetStore.getPresets(gameId);
        Assert.assertTrue("Presets list should contain the preset", presets.stream().anyMatch(p -> p.name().equals(presetName)));

        // 6. NBT serialization
        NbtList nbtList = GamemodePresetStore.presetsToNbt(gameId);
        Assert.assertFalse("NBT list should not be empty", nbtList.isEmpty());

        // 7. Delete preset
        boolean deleted = GamemodePresetStore.deletePreset(gameId, presetName);
        Assert.assertTrue("Delete should succeed", deleted);
        Assert.assertFalse("Preset should no longer exist", GamemodePresetStore.getPreset(gameId, presetName).isPresent());
    }

    @Test
    public void testGamemodePresetNbtRoundtrip() {
        NbtCompound settings = new NbtCompound();
        settings.putInt("maxRounds", 50);
        settings.putString("mapId", "rooftop");

        GamemodePreset original = new GamemodePreset("zombies", "Custom 50", 5000L, 6000L, settings);
        NbtCompound nbt = original.toNbt();

        GamemodePreset restored = GamemodePreset.fromNbt(nbt);
        Assert.assertEquals(original.gameId(), restored.gameId());
        Assert.assertEquals(original.name(), restored.name());
        Assert.assertEquals(original.createdAt(), restored.createdAt());
        Assert.assertEquals(original.updatedAt(), restored.updatedAt());
        Assert.assertEquals(50, restored.settings().getInt("maxRounds"));
        Assert.assertEquals("rooftop", restored.settings().getString("mapId"));
    }
}
