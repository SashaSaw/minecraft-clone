package craft;

import java.util.Properties;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rebindable key bindings for gameplay actions, persisted in settings.properties
 * under "key.&lt;ACTION&gt;". Hotbar 1-9, Esc, mouse buttons and menu navigation are
 * fixed and not listed here.
 */
public final class Keybinds {
    public enum Action {
        FORWARD("Forward", GLFW_KEY_W),
        BACK("Back", GLFW_KEY_S),
        LEFT("Left", GLFW_KEY_A),
        RIGHT("Right", GLFW_KEY_D),
        JUMP("Jump", GLFW_KEY_SPACE),
        SNEAK("Sneak", GLFW_KEY_LEFT_SHIFT),
        SPRINT("Sprint", GLFW_KEY_LEFT_CONTROL),
        INVENTORY("Inventory", GLFW_KEY_E),
        DROP("Drop", GLFW_KEY_Q),
        PERSPECTIVE("Perspective", GLFW_KEY_F5),
        DEBUG("Debug Info", GLFW_KEY_F3),
        SCREENSHOT("Screenshot", GLFW_KEY_F2);

        public final String label;
        public final int def;
        public int key;

        Action(String label, int def) {
            this.label = label;
            this.def = def;
            this.key = def;
        }
    }

    /** True while the action's key is held; safe for unbound (NONE) actions. */
    public static boolean down(Input in, Action a) {
        return a.key != GLFW_KEY_UNKNOWN && in.isDown(a.key);
    }

    /** Binds key to action, clearing it from any other action it was assigned to. */
    public static void bind(Action a, int key) {
        for (Action o : Action.values()) {
            if (o != a && o.key == key) o.key = GLFW_KEY_UNKNOWN;
        }
        a.key = key;
    }

    public static void resetDefaults() {
        for (Action a : Action.values()) a.key = a.def;
    }

    static void load(Properties p) {
        for (Action a : Action.values()) {
            String v = p.getProperty("key." + a.name());
            if (v == null) continue;
            try {
                a.key = Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    static void save(Properties p) {
        for (Action a : Action.values()) {
            p.setProperty("key." + a.name(), String.valueOf(a.key));
        }
    }

    /** Short display name for a GLFW key code, using only glyphs the font supports. */
    public static String keyName(int k) {
        if (k == GLFW_KEY_UNKNOWN) return "NONE";
        if (k >= GLFW_KEY_A && k <= GLFW_KEY_Z) return String.valueOf((char) k);
        if (k >= GLFW_KEY_0 && k <= GLFW_KEY_9) return String.valueOf((char) k);
        if (k >= GLFW_KEY_F1 && k <= GLFW_KEY_F25) return "F" + (k - GLFW_KEY_F1 + 1);
        return switch (k) {
            case GLFW_KEY_SPACE -> "SPACE";
            case GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW_KEY_LEFT_SUPER -> "LSUPER";
            case GLFW_KEY_RIGHT_SUPER -> "RSUPER";
            case GLFW_KEY_TAB -> "TAB";
            case GLFW_KEY_ENTER -> "ENTER";
            case GLFW_KEY_BACKSPACE -> "BKSP";
            case GLFW_KEY_LEFT -> "LEFT";
            case GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW_KEY_UP -> "UP";
            case GLFW_KEY_DOWN -> "DOWN";
            case GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW_KEY_MINUS -> "-";
            case GLFW_KEY_EQUAL -> "=";
            case GLFW_KEY_COMMA -> ",";
            case GLFW_KEY_PERIOD -> ".";
            case GLFW_KEY_SLASH -> "/";
            case GLFW_KEY_APOSTROPHE -> "'";
            case GLFW_KEY_SEMICOLON -> "SEMI";
            case GLFW_KEY_LEFT_BRACKET -> "LBRKT";
            case GLFW_KEY_RIGHT_BRACKET -> "RBRKT";
            case GLFW_KEY_BACKSLASH -> "BSLASH";
            case GLFW_KEY_GRAVE_ACCENT -> "GRAVE";
            default -> "KEY " + k;
        };
    }

    private Keybinds() {
    }
}
