package br.com.craftpanel.console;

import io.javalin.websocket.WsMessageContext;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WebSocketSenderTest {

    @Test
    void testDeliversToOpenSession() {
        List<String> sent = new ArrayList<>();
        assertTrue(WebSocketSender.send(context(true, false, sent), "{\"type\":\"telemetry\"}"));
        assertEquals(List.of("{\"type\":\"telemetry\"}"), sent);
    }

    @Test
    void testClosedSessionIsADisconnect() {
        List<String> sent = new ArrayList<>();
        assertFalse(WebSocketSender.send(context(false, false, sent), "{}"));
        assertTrue(sent.isEmpty());
    }

    @Test
    void testClosedChannelDuringSendIsADisconnect() {
        // The race seen in production: the browser left between the open check and the write.
        assertFalse(WebSocketSender.send(context(true, true, new ArrayList<>()), "{}"));
    }

    private static WsMessageContext context(boolean open, boolean failOnSend, List<String> sent) {
        RemoteEndpoint remote = (RemoteEndpoint) Proxy.newProxyInstance(RemoteEndpoint.class.getClassLoader(),
                new Class<?>[]{RemoteEndpoint.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendString")) {
                        if (failOnSend) {
                            throw new IOException(new ClosedChannelException());
                        }
                        sent.add((String) args[0]);
                    }
                    return null;
                });
        Session session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
                new Class<?>[]{Session.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isOpen" -> open;
                    case "getRemote" -> remote;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        return new WsMessageContext("test-session", session, "");
    }
}
