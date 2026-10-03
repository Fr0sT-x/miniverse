package dev.frost.miniverse.chat;

import org.junit.Assert;
import org.junit.Test;

import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsSettings;
import dev.frost.miniverse.minigame.impl.bridge.BridgeMinigame;
import dev.frost.miniverse.minigame.impl.bridge.BridgeSettings;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntSettings;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;

public class ChatRoutingTest {

    @Test
    public void testChatRoutingAwareDefaults() {
        ChatRoutingAware defaultAware = new ChatRoutingAware() {};
        Assert.assertTrue("Default isChatRoutingEnabled should be true", defaultAware.isChatRoutingEnabled());
    }

    @Test
    public void testMicroPartyDoesNotImplementChatRoutingAware() {
        MicroPartyMinigame microParty = new MicroPartyMinigame();
        Assert.assertFalse("MicroParty must not implement ChatRoutingAware", microParty instanceof ChatRoutingAware);
    }

    @Test
    public void testManhuntChatRouting() {
        ManhuntMinigame manhunt = new ManhuntMinigame();
        Assert.assertTrue("Manhunt must implement ChatRoutingAware", manhunt instanceof ChatRoutingAware);
        Assert.assertTrue("Manhunt team chat should be enabled by default", ((ChatRoutingAware) manhunt).isChatRoutingEnabled());

        // Toggle team chat off
        ManhuntSettings settingsNoTeamChat = manhunt.getSettings().withTeamChatEnabled(false);
        manhunt.applySettings(settingsNoTeamChat);
        Assert.assertFalse("Manhunt team chat should be disabled when setting is false", ((ChatRoutingAware) manhunt).isChatRoutingEnabled());

        // Toggle team chat on
        ManhuntSettings settingsTeamChat = manhunt.getSettings().withTeamChatEnabled(true);
        manhunt.applySettings(settingsTeamChat);
        Assert.assertTrue("Manhunt team chat should be enabled when setting is true", ((ChatRoutingAware) manhunt).isChatRoutingEnabled());
    }

    @Test
    public void testBedwarsChatRouting() {
        BedwarsMinigame bedwars = new BedwarsMinigame();
        Assert.assertTrue("Bedwars must implement ChatRoutingAware", bedwars instanceof ChatRoutingAware);
        Assert.assertFalse("Bedwars team chat should be disabled by default", ((ChatRoutingAware) bedwars).isChatRoutingEnabled());

        // Enable team chat via settings
        BedwarsSettings settingsWithTeamChat = new BedwarsSettings(
            "", 5, 20, 160, 500, 700, 64, 32, 8, 4, true
        );
        bedwars.applySettings(settingsWithTeamChat, null);
        Assert.assertTrue("Bedwars team chat should be enabled when setting is true", ((ChatRoutingAware) bedwars).isChatRoutingEnabled());
    }

    @Test
    public void testBridgeChatRouting() {
        BridgeMinigame bridge = new BridgeMinigame();
        Assert.assertTrue("Bridge must implement ChatRoutingAware", bridge instanceof ChatRoutingAware);
        Assert.assertFalse("Bridge team chat should be disabled by default", ((ChatRoutingAware) bridge).isChatRoutingEnabled());

        // Enable team chat via settings
        BridgeSettings settingsWithTeamChat = new BridgeSettings(
            "", 5, 3, 5, 60, 0, true, true, true, true, true
        );
        bridge.applySettings(settingsWithTeamChat, null);
        Assert.assertTrue("Bridge team chat should be enabled when setting is true", ((ChatRoutingAware) bridge).isChatRoutingEnabled());
    }

    @Test
    public void testChatChannelStateTracking() {
        java.util.UUID testUuid = java.util.UUID.randomUUID();
        ChatRouter.clearPlayerChannels();

        Assert.assertEquals("Default channel must be TEAM", ChatChannel.TEAM, ChatRouter.getPlayerChannel(testUuid));

        ChatRouter.setPlayerChannel(testUuid, ChatChannel.GLOBAL);
        Assert.assertEquals("Channel should update to GLOBAL", ChatChannel.GLOBAL, ChatRouter.getPlayerChannel(testUuid));

        ChatRouter.clearPlayerChannels();
        Assert.assertEquals("After clearing, channel should revert to default TEAM", ChatChannel.TEAM, ChatRouter.getPlayerChannel(testUuid));
    }

    @Test
    public void testChatChannelPrefixes() {
        Assert.assertEquals("ALL", ChatChannel.GLOBAL.label());
        Assert.assertEquals("[ALL]", ChatChannel.GLOBAL.prefix());
        Assert.assertEquals("TEAM", ChatChannel.TEAM.label());
        Assert.assertEquals("[TEAM]", ChatChannel.TEAM.prefix());
    }
}
