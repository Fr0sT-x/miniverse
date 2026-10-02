package dev.frost.miniverse.minigame.impl.ctf;

import net.minecraft.nbt.NbtCompound;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class CaptureTheFlagSettingsTest {

    @Test
    public void testDefaults() {
        CaptureTheFlagSettings settings = CaptureTheFlagSettings.defaults();
        Assert.assertEquals("", settings.mapId());
        Assert.assertFalse(settings.eliminationMode());
        Assert.assertEquals(3, settings.targetCaptures());
        Assert.assertEquals(15, settings.matchDurationMinutes());
        Assert.assertEquals(5, settings.respawnDelaySeconds());
        Assert.assertEquals(15, settings.flagReturnDelaySeconds());
        Assert.assertTrue(settings.requireOwnFlagAtBase());
        Assert.assertTrue(settings.carrierGlowing());
        Assert.assertFalse(settings.teamChatEnabled());
        Assert.assertTrue(settings.allowInvisibilityPotion());
        Assert.assertTrue(settings.naturalRegeneration());
        Assert.assertTrue(settings.suddenDeath());
    }

    @Test
    public void testNbtRoundTrip() {
        CaptureTheFlagSettings original = new CaptureTheFlagSettings(
            "ctf_fortress",
            false,
            5,
            10,
            3,
            20,
            false,
            false,
            false,
            false,
            false,
            false
        );

        NbtCompound compound = new NbtCompound();
        original.writeTo(compound);

        CaptureTheFlagSettings parsed = CaptureTheFlagSettings.fromNbt(compound);
        Assert.assertEquals("ctf_fortress", parsed.mapId());
        Assert.assertFalse(parsed.eliminationMode());
        Assert.assertEquals(5, parsed.targetCaptures());
        Assert.assertEquals(10, parsed.matchDurationMinutes());
        Assert.assertEquals(3, parsed.respawnDelaySeconds());
        Assert.assertEquals(20, parsed.flagReturnDelaySeconds());
        Assert.assertFalse(parsed.requireOwnFlagAtBase());
        Assert.assertFalse(parsed.carrierGlowing());
        Assert.assertFalse(parsed.teamChatEnabled());
        Assert.assertFalse(parsed.allowInvisibilityPotion());
        Assert.assertFalse(parsed.naturalRegeneration());
        Assert.assertFalse(parsed.suddenDeath());
    }

    @Test
    public void testPropertiesRoundTrip() {
        CaptureTheFlagSettings original = new CaptureTheFlagSettings(
            "castle_wars",
            true,
            4,
            20,
            6,
            10,
            true,
            true,
            true,
            true,
            true,
            true
        );

        Properties props = new Properties();
        original.writeTo(props);

        CaptureTheFlagSettings parsed = CaptureTheFlagSettings.fromProperties(props);
        Assert.assertEquals("castle_wars", parsed.mapId());
        Assert.assertTrue(parsed.eliminationMode());
        Assert.assertEquals(4, parsed.targetCaptures());
        Assert.assertEquals(20, parsed.matchDurationMinutes());
        Assert.assertEquals(6, parsed.respawnDelaySeconds());
        Assert.assertEquals(10, parsed.flagReturnDelaySeconds());
        Assert.assertTrue(parsed.requireOwnFlagAtBase());
        Assert.assertTrue(parsed.carrierGlowing());
        Assert.assertTrue(parsed.teamChatEnabled());
        Assert.assertTrue(parsed.allowInvisibilityPotion());
        Assert.assertTrue(parsed.naturalRegeneration());
        Assert.assertTrue(parsed.suddenDeath());
    }
}
