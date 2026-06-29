package dev.frost.miniverse.map.editor;

public record MarkerGrouping(
    String parentKey,
    GroupingType type,
    String propertyKey
) {
    public enum GroupingType {
        LOGICAL,
        SPATIAL
    }

    public static MarkerGrouping logical(String parentKey, String propertyKey) {
        return new MarkerGrouping(parentKey, GroupingType.LOGICAL, propertyKey);
    }

    public static MarkerGrouping spatial(String parentKey) {
        return new MarkerGrouping(parentKey, GroupingType.SPATIAL, null);
    }
}
