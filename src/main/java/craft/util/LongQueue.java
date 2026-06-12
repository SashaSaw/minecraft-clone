package craft.util;

/** Minimal growable ring buffer of longs (avoids boxing in light BFS). */
public class LongQueue {
    private long[] data = new long[1024];
    private int head, tail, size;

    public void add(long v) {
        if (size == data.length) grow();
        data[tail] = v;
        tail = (tail + 1) & (data.length - 1);
        size++;
    }

    public long poll() {
        long v = data[head];
        head = (head + 1) & (data.length - 1);
        size--;
        return v;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    private void grow() {
        long[] next = new long[data.length * 2];
        for (int i = 0; i < size; i++) next[i] = data[(head + i) & (data.length - 1)];
        data = next;
        head = 0;
        tail = size;
    }
}
