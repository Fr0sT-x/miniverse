package dev.frost.miniverse.client.gui.map;

import net.minecraft.client.MinecraftClient;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Opens a folder-selection dialog for the admin to pick an existing Minecraft world folder.
 *
 * <p><b>Windows:</b> spawns a {@code powershell.exe} subprocess that shows a native
 * {@code FolderBrowserDialog}. This is a true Win32 window — it appears in the taskbar,
 * gains focus over the LWJGL game window, and looks like the modern Windows file explorer.
 *
 * <p><b>macOS / Linux:</b> falls back to a Swing {@link JFileChooser} running on the AWT
 * Event Dispatch Thread (EDT), with AWT force-initialized so the EDT is running.
 */
public final class MapImportHelper {

    private MapImportHelper() {}

    /**
     * Opens the OS folder picker (non-blocking on the MC main thread).
     *
     * <p>Callbacks are always invoked on Minecraft's main thread:
     * <ul>
     *   <li>{@code onResult} — valid world {@link File} was selected.</li>
     *   <li>{@code onError}  — validation failed or user cancelled (empty string = cancelled).</li>
     * </ul>
     */
    public static void openFolderPicker(Consumer<File> onResult, Consumer<String> onError) {
        Thread pickerThread = new Thread(() -> {
            try {
                File selected = pickFolder();
                if (selected == null) {
                    // User cancelled
                    MinecraftClient.getInstance().execute(() -> onError.accept(""));
                } else {
                    String error = WorldFolderValidator.validate(selected);
                    if (error != null) {
                        MinecraftClient.getInstance().execute(() -> onError.accept(error));
                    } else {
                        MinecraftClient.getInstance().execute(() -> onResult.accept(selected));
                    }
                }
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(
                    () -> onError.accept("Failed to open file picker: " + e.getMessage())
                );
            }
        });
        pickerThread.setName("miniverse-world-picker");
        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    /** Dispatches to the correct platform picker. */
    private static File pickFolder() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            return pickFolderWindows();
        }
        // macOS / Linux: AWT/Swing path (EDT must be running)
        java.awt.Toolkit.getDefaultToolkit(); // force AWT + EDT initialisation
        File[] result = {null};
        SwingUtilities.invokeAndWait(() -> result[0] = pickFolderSwing());
        return result[0];
    }

    // ── Windows ──────────────────────────────────────────────────────────────

    /**
     * Spawns {@code powershell.exe} with a {@code FolderBrowserDialog}.
     *
     * <p>Key flags used:
     * <ul>
     *   <li>{@code -STA} — Single-Threaded Apartment, required by WinForms COM dialogs.</li>
     *   <li>{@code EnableVisualStyles()} — activates Windows visual themes.</li>
     *   <li>{@code UseDescriptionForTitle = $true} — Vista-style dialog (modern look).</li>
     * </ul>
     *
     * <p>The selected path is written to stdout; nothing is written on cancel.
     * The PowerShell console window is hidden by redirecting its streams.
     */
    private static File pickFolderWindows() throws IOException, InterruptedException {
        // Build a one-liner PowerShell script. Semicolons separate statements.
        String script = String.join(" ; ",
            "Add-Type -AssemblyName System.Windows.Forms",
            "[System.Windows.Forms.Application]::EnableVisualStyles()",
            "$d = New-Object System.Windows.Forms.FolderBrowserDialog",
            "$d.Description = 'Select Minecraft World Folder'",
            "$d.UseDescriptionForTitle = $true",
            "$d.ShowNewFolderButton = $false",
            "if ($d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $d.SelectedPath }"
        );

        ProcessBuilder pb = new ProcessBuilder(
            "powershell.exe",
            "-NoProfile",       // skip profile scripts for faster startup
            "-NonInteractive",  // no stdin prompts (GUI dialog is unaffected)
            "-STA",             // Single-Threaded Apartment — required for WinForms
            "-Command", script
        );
        // Do not inherit the parent console window; avoids a flash of a terminal
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        Process proc = pb.start();

        // Drain stderr in a separate thread to prevent the process from blocking
        Thread stderrDrain = new Thread(() -> {
            try { proc.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream()); }
            catch (IOException ignored) {}
        });
        stderrDrain.setDaemon(true);
        stderrDrain.start();

        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        proc.waitFor();

        if (output.isEmpty()) {
            return null; // user cancelled
        }
        File f = new File(output);
        // Guard against any stray error text in stdout
        return f.isDirectory() ? f : null;
    }

    // ── macOS / Linux fallback ────────────────────────────────────────────────

    /**
     * Swing {@link JFileChooser} in directory-only mode.
     * <p><b>Must be called on the AWT EDT.</b>
     */
    private static File pickFolderSwing() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Minecraft World Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        int result = chooser.showOpenDialog(null);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }
}

