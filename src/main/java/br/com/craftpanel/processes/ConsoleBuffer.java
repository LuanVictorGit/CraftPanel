package br.com.craftpanel.processes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ConsoleBuffer {
    private final int capacity;
    private final Deque<ConsoleLine> buffer;

    public ConsoleBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    public synchronized void add(ConsoleLine line) {
        if (buffer.size() >= capacity) {
            buffer.removeFirst();
        }
        buffer.addLast(line);
    }

    public synchronized List<ConsoleLine> getLines() {
        return new ArrayList<>(buffer);
    }

    public synchronized void clear() {
        buffer.clear();
    }
}
