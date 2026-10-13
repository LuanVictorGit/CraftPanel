package br.com.craftpanel.processes;

import java.time.Instant;

public record ConsoleLine(
        String line,
        Instant timestamp,
        String level
) {}
