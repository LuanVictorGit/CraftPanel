package br.com.craftpanel.console;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class ConsoleCapture {
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static boolean installed = false;
    private static volatile Consumer<String> lineConsumer;

    public static synchronized void install(Consumer<String> consumer) {
        lineConsumer = consumer;
        if (installed) return;

        originalOut = System.out;
        originalErr = System.err;

        System.setOut(new InterceptingPrintStream(originalOut, true));
        System.setErr(new InterceptingPrintStream(originalErr, false));
        installed = true;
    }

    public static synchronized void setConsumer(Consumer<String> consumer) {
        lineConsumer = consumer;
    }

    public static synchronized void uninstall() {
        if (!installed) return;
        installed = false;
        if (originalOut != null) {
            System.setOut(originalOut);
            originalOut = null;
        }
        if (originalErr != null) {
            System.setErr(originalErr);
            originalErr = null;
        }
        lineConsumer = null;
    }

    private static class InterceptingPrintStream extends PrintStream {
        private final PrintStream delegate;
        private final ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(256);

        public InterceptingPrintStream(PrintStream delegate, boolean isOut) {
            super(delegate, true, StandardCharsets.UTF_8);
            this.delegate = delegate;
        }

        @Override
        public void write(int b) {
            delegate.write(b);
            synchronized (lineBuf) {
                if (b == '\n') {
                    flushCapturedLine();
                } else if (b != '\r') {
                    lineBuf.write(b);
                }
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            delegate.write(buf, off, len);
            synchronized (lineBuf) {
                for (int i = off; i < off + len; i++) {
                    byte b = buf[i];
                    if (b == '\n') {
                        flushCapturedLine();
                    } else if (b != '\r') {
                        lineBuf.write(b);
                    }
                }
            }
        }

        @Override
        public void flush() {
            delegate.flush();
            synchronized (lineBuf) {
                if (lineBuf.size() > 0) {
                    flushCapturedLine();
                }
            }
        }

        private void flushCapturedLine() {
            if (lineBuf.size() == 0) {
                Consumer<String> c = lineConsumer;
                if (c != null) {
                    c.accept("");
                }
                return;
            }
            byte[] bytes = lineBuf.toByteArray();
            lineBuf.reset();
            String line = new String(bytes, StandardCharsets.UTF_8);
            Consumer<String> c = lineConsumer;
            if (c != null) {
                c.accept(line);
            }
        }
    }
}
