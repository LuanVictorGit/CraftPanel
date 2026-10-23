package br.com.craftpanel.console;

import io.javalin.websocket.WsContext;

/**
 * Sends WebSocket text frames one at a time per session: Jetty rejects a blocking send while another one is pending,
 * and panel sockets are written from several threads (console lines, task updates, metrics, command replies).
 * A session that already closed, such as a browser that just left the page, counts as a disconnect, not an error.
 */
public final class WebSocketSender {

    private WebSocketSender() {
    }

    /**
     * @return {@code false} when the client is gone and should be forgotten
     */
    public static boolean send(WsContext ctx, String json) {
        if (!ctx.session.isOpen()) {
            return false;
        }
        try {
            synchronized (ctx.session) {
                ctx.send(json);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
