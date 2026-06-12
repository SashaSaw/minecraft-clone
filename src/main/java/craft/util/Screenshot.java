package craft.util;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

public final class Screenshot {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    public static void capture(int width, int height, String path) {
        ByteBuffer buf = MemoryUtil.memAlloc(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = ((height - 1 - y) * width + x) * 4;
                int r = buf.get(i) & 0xFF, g = buf.get(i + 1) & 0xFF, b = buf.get(i + 2) & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        MemoryUtil.memFree(buf);
        try {
            ImageIO.write(img, "png", new File(path));
            System.out.println("Screenshot saved: " + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Screenshot() {
    }
}
