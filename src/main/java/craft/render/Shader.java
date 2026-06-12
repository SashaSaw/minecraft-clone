package craft.render;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class Shader {
    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public Shader(String vertexSrc, String fragmentSrc) {
        int vs = compile(GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSrc);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Shader link failed: " + glGetProgramInfoLog(program));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Shader compile failed: " + glGetShaderInfoLog(id) + "\n" + src);
        }
        return id;
    }

    public void bind() {
        glUseProgram(program);
    }

    private int loc(String name) {
        return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    public void setMat4(String name, Matrix4f mat) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            mat.get(fb);
            glUniformMatrix4fv(loc(name), false, fb);
        }
    }

    public void setVec3(String name, float x, float y, float z) {
        glUniform3f(loc(name), x, y, z);
    }

    public void setVec2(String name, float x, float y) {
        glUniform2f(loc(name), x, y);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        glUniform4f(loc(name), x, y, z, w);
    }

    public void setFloat(String name, float v) {
        glUniform1f(loc(name), v);
    }

    public void setInt(String name, int v) {
        glUniform1i(loc(name), v);
    }
}
