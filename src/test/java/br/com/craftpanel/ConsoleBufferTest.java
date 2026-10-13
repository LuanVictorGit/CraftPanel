package br.com.craftpanel;

import br.com.craftpanel.processes.ConsoleBuffer;
import br.com.craftpanel.processes.ConsoleLine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConsoleBufferTest {

    @Test
    void testBufferCapacityAndEviction() {
        ConsoleBuffer buffer = new ConsoleBuffer(3);

        buffer.add(new ConsoleLine("Line 1", Instant.now(), "INFO"));
        buffer.add(new ConsoleLine("Line 2", Instant.now(), "INFO"));
        buffer.add(new ConsoleLine("Line 3", Instant.now(), "INFO"));

        List<ConsoleLine> lines = buffer.getLines();
        assertEquals(3, lines.size());
        assertEquals("Line 1", lines.get(0).line());

        buffer.add(new ConsoleLine("Line 4", Instant.now(), "INFO"));
        lines = buffer.getLines();
        assertEquals(3, lines.size());
        assertEquals("Line 2", lines.get(0).line());
        assertEquals("Line 4", lines.get(2).line());

        buffer.clear();
        assertTrue(buffer.getLines().isEmpty());
    }
}
