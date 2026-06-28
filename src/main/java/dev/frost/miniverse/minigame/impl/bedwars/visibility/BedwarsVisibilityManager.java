package dev.frost.miniverse.minigame.impl.bedwars.visibility;

import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamDescriptor;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BedwarsVisibilityManager {
    private final VanillaTeamAdapter teamAdapter;
    private final BedwarsMinigame minigame;

    public BedwarsVisibilityManager(BedwarsMinigame minigame) {
        this.teamAdapter = new VanillaTeamAdapter("bedwars");
        this.minigame = minigame;
        this.teamAdapter.setFriendlyFireAllowed(false);
        this.teamAdapter.setTeammateCollisionAllowed(true);
    }

    public void sync(MinecraftServer server) {
        if (minigame.getContext() == null || minigame.getContext().roster() == null) return;
        List<ServerPlayerEntity> participants = minigame.getContext().roster().onlinePlayers(server);
        
        List<VanillaTeamDescriptor> descriptors = new ArrayList<>();
        
        // Group players by their bedwars team
        Map<String, List<ServerPlayerEntity>> grouped = participants.stream()
            .filter(p -> minigame.teamManager().teamId(p.getUuid()) != null)
            .collect(Collectors.groupingBy(p -> minigame.teamManager().teamId(p.getUuid())));
            
        for (Map.Entry<String, List<ServerPlayerEntity>> entry : grouped.entrySet()) {
            String teamId = entry.getKey();
            dev.frost.miniverse.team.GameTeam td = minigame.teamManager().ensureTeam(teamId, teamId);
            
            VanillaTeamOptions options = VanillaTeamOptions.defaults()
                .withColor(td.color())
                .withNameTagVisibility(AbstractTeam.VisibilityRule.HIDE_FOR_OTHER_TEAMS)
                .withShowFriendlyInvisibles(true)
                .withFriendlyFireAllowed(false);
                
            descriptors.add(new VanillaTeamDescriptor(
                teamId,
                Text.literal(td.label()),
                entry.getValue(),
                options
            ));
            
            // Give glowing effect to teammates
            for (ServerPlayerEntity p : entry.getValue()) {
                p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.GLOWING, 60, 0, false, false, false));
            }
        }
        
        teamAdapter.sync(server, descriptors);
    }

    public void clear(MinecraftServer server) {
        teamAdapter.clear(server);
        if (minigame.getContext() != null && minigame.getContext().roster() != null) {
            for (ServerPlayerEntity p : minigame.getContext().roster().onlinePlayers(server)) {
                p.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.GLOWING);
            }
        }
    }
}
