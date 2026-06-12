package craft.util;

/** Minimal growable float array for mesh building. */
public class FloatList {
    private float[] data;
    private int size;

    public FloatList(int initial) {
        data = new float[initial];
    }

    public void add(float v) {
        if (size == data.length) {
            float[] next = new float[data.length * 2];
            System.arraycopy(data, 0, next, 0, size);
            data = next;
        }
        data[size++] = v;
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
    }

    public float[] raw() {
        return data;
    }

    public float[] toArray() {
        float[] out = new float[size];
        System.arraycopy(data, 0, out, 0, size);
        return out;
    }
}
