package dev.frost.miniverse.client.gui.map;

import java.io.File;

/**
 * Client-side utility that validates whether a given folder is a real Minecraft world.
 *
 * <p>Validation rules (all must pass):
 * <ul>
 *   <li>{@code level.dat} must exist as a regular file — the master world metadata.</li>
 *   <li>{@code region/} must exist as a directory — the overworld chunk region files.</li>
 * </ul>
 * Additional directories (DIM-1, DIM1, playerdata, data, etc.) are optional and not checked.
 */
public final class WorldFolderValidator {

    private WorldFolderValidator() {}

    /**
     * Validates the given folder as a Minecraft world.
     *
     * @param folder the folder to validate (must be non-null)
     * @return {@code null} if the folder is a valid world, or a human-readable error message if it is not
     */
    public static String validate(File folder) {
        if (folder == null) {
            return "No folder selected.";
        }
        if (!folder.exists()) {
            return "Selected path does not exist.";
        }
        if (!folder.isDirectory()) {
            return "Selected path is not a folder.";
        }
        if (!new File(folder, "level.dat").isFile()) {
            return "No level.dat found — this does not appear to be a valid Minecraft world folder.";
        }
        if (!new File(folder, "region").isDirectory()) {
            return "No region/ folder found — this does not appear to be a valid Minecraft world folder.";
        }
        return null; // valid
    }
}

