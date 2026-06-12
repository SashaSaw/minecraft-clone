package craft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** User settings, persisted to settings.properties next to the saves folder. */
public final class Settings {
    public static int guiScale = 4;          // 2..6
    public static float brightness = 0.25f;  // 0..1

    private static final File FILE = new File("settings.properties");

    public static void load() {
        if (!FILE.exists()) return;
        try (FileInputStream in = new FileInputStream(FILE)) {
            Properties p = new Properties();
            p.load(in);
            guiScale = clampScale(Integer.parseInt(p.getProperty("guiScale", "3")));
            brightness = clamp01(Float.parseFloat(p.getProperty("brightness", "0.25")));
        } catch (Exception e) {
            System.err.println("Failed to load settings: " + e);
        }
    }

    public static void save() {
        try (FileOutputStream out = new FileOutputStream(FILE)) {
            Properties p = new Properties();
            p.setProperty("guiScale", String.valueOf(guiScale));
            p.setProperty("brightness", String.valueOf(brightness));
            p.store(out, "Craft settings");
        } catch (Exception e) {
            System.err.println("Failed to save settings: " + e);
        }
    }

    /** Minimum light level the renderer clamps to (brightness slider). */
    public static float minLight() {
        return 0.035f + brightness * 0.22f;
    }

    public static int clampScale(int s) {
        return Math.max(2, Math.min(6, s));
    }

    public static float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private Settings() {
    }
}
