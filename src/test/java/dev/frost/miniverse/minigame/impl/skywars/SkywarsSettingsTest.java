package dev.frost.miniverse.minigame.impl.skywars;

import net.minecraft.nbt.NbtCompound;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class SkywarsSettingsTest {

    @Test
    public void testDefaults() {
        SkywarsSettings settings = SkywarsSettings.defaults();
        Assert.assertEquals(SkywarsSettings.MODE_NORMAL, settings.mode());
        Assert.assertFalse(settings.isInsaneMode());
        Assert.assertEquals(180, settings.refillIntervalSeconds());
        Assert.assertEquals(10, settings.cageTimerSeconds());
        Assert.assertEquals(600, settings.timeLimitSeconds());
        Assert.assertTrue(settings.instantVoidDeath());
        Assert.assertFalse(settings.teamChatEnabled());
    }

    @Test
    public void testNbtRoundTrip() {
        SkywarsSettings original = new SkywarsSettings("insane", 120, 15, 900, false, true);
        NbtCompound nbt = new NbtCompound();
        original.writeTo(nbt);

        SkywarsSettings parsed = SkywarsSettings.fromNbt(nbt);
        Assert.assertEquals("insane", parsed.mode());
        Assert.assertTrue(parsed.isInsaneMode());
        Assert.assertEquals(120, parsed.refillIntervalSeconds());
        Assert.assertEquals(15, parsed.cageTimerSeconds());
        Assert.assertEquals(900, parsed.timeLimitSeconds());
        Assert.assertFalse(parsed.instantVoidDeath());
        Assert.assertTrue(parsed.teamChatEnabled());
    }

    @Test
    public void testPropertiesRoundTrip() {
        SkywarsSettings original = new SkywarsSettings("insane", 240, 8, 1200, true, true);
        Properties props = new Properties();
        original.writeTo(props);

        SkywarsSettings parsed = SkywarsSettings.fromProperties(props);
        Assert.assertEquals("insane", parsed.mode());
        Assert.assertTrue(parsed.isInsaneMode());
        Assert.assertEquals(240, parsed.refillIntervalSeconds());
        Assert.assertEquals(8, parsed.cageTimerSeconds());
        Assert.assertEquals(1200, parsed.timeLimitSeconds());
        Assert.assertTrue(parsed.instantVoidDeath());
        Assert.assertTrue(parsed.teamChatEnabled());
    }
}
