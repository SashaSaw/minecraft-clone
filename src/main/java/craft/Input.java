package craft;

import java.util.ArrayDeque;

import static org.lwjgl.glfw.GLFW.*;

public class Input {
    private final long window;
    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] mouseButtons = new boolean[8];
    private final ArrayDeque<Integer> keyPresses = new ArrayDeque<>();
    private final ArrayDeque<Integer> mousePresses = new ArrayDeque<>();

    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    public double mouseDX, mouseDY;   // accumulated since last consume
    public double scrollY;            // accumulated since last consume
    private boolean cursorCaptured;

    public Input(long window) {
        this.window = window;
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key < 0 || key > GLFW_KEY_LAST) return;
            if (action == GLFW_PRESS) {
                keys[key] = true;
                keyPresses.add(key);
            } else if (action == GLFW_RELEASE) {
                keys[key] = false;
            }
        });
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button < 0 || button >= 8) return;
            if (action == GLFW_PRESS) {
                mouseButtons[button] = true;
                mousePresses.add(button);
            } else if (action == GLFW_RELEASE) {
                mouseButtons[button] = false;
            }
        });
        glfwSetCursorPosCallback(window, (win, x, y) -> {
            if (firstMouse) {
                lastMouseX = x;
                lastMouseY = y;
                firstMouse = false;
            }
            mouseDX += x - lastMouseX;
            mouseDY += y - lastMouseY;
            lastMouseX = x;
            lastMouseY = y;
        });
        glfwSetScrollCallback(window, (win, sx, sy) -> scrollY += sy);
    }

    public boolean isDown(int key) {
        return keys[key];
    }

    public boolean isMouseDown(int button) {
        return mouseButtons[button];
    }

    /** Drains one buffered key-press event, or -1 if none. */
    public int nextKeyPress() {
        Integer k = keyPresses.poll();
        return k == null ? -1 : k;
    }

    public int nextMousePress() {
        Integer b = mousePresses.poll();
        return b == null ? -1 : b;
    }

    public void captureCursor(boolean capture) {
        cursorCaptured = capture;
        glfwSetInputMode(window, GLFW_CURSOR, capture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        firstMouse = true;
        mouseDX = 0;
        mouseDY = 0;
    }

    public boolean isCursorCaptured() {
        return cursorCaptured;
    }

    /** Cursor position in window coordinates (valid when not captured). */
    public double cursorX() {
        return lastMouseX;
    }

    public double cursorY() {
        return lastMouseY;
    }

    /** Returns and clears the accumulated mouse delta. */
    public double[] consumeMouseDelta() {
        double[] d = {mouseDX, mouseDY};
        mouseDX = 0;
        mouseDY = 0;
        return d;
    }

    public double consumeScroll() {
        double s = scrollY;
        scrollY = 0;
        return s;
    }
}
