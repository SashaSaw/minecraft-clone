package craft;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    public final long handle;
    public int fbWidth, fbHeight;
    public boolean resized;

    public Window(int width, int height, String title) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1);
        GL.createCapabilities();

        int[] fw = new int[1], fh = new int[1];
        glfwGetFramebufferSize(handle, fw, fh);
        fbWidth = fw[0];
        fbHeight = fh[0];
        glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
            fbWidth = w;
            fbHeight = h;
            resized = true;
        });

        glViewport(0, 0, fbWidth, fbHeight);
        glfwShowWindow(handle);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void swap() {
        glfwSwapBuffers(handle);
    }

    public void setTitle(String title) {
        glfwSetWindowTitle(handle, title);
    }

    public void destroy() {
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
