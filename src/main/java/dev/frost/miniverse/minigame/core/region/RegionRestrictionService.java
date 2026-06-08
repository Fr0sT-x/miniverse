package dev.frost.miniverse.minigame.core.region;

import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.map.runtime.RuntimeMarkerCache;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.EnumSet;
import java.util.List;

public class RegionRestrictionService {
    private static final RegionRestrictionService INSTANCE = new RegionRestrictionService();

    private RegionRestrictionService() {
    }

    public static RegionRestrictionService getInstance() {
        return INSTANCE;
    }

    public boolean hasRestriction(Box box, RegionRestriction restriction) {
        List<MapMarker> markers = RuntimeMarkerCache.getInstance().getRegionsIntersecting(box);
        for (MapMarker marker : markers) {
            EnumSet<RegionRestriction> restrictions = RegionRestriction.parse(marker.properties());
            if (restrictions.contains(restriction)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRestriction(ServerPlayerEntity player, RegionRestriction restriction) {
        return hasRestriction(player.getBoundingBox(), restriction);
    }
}
