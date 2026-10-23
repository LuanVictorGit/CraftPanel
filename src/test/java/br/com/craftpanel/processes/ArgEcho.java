package br.com.craftpanel.processes;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Prints its arguments separated by NUL characters, for HostRestarterTest. */
public final class ArgEcho {
    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        for (String arg : args) {
            out.print(arg);
            out.print('\u0000');
        }
        out.flush();
    }
}
