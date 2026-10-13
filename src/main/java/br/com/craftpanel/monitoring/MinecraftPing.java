package br.com.craftpanel.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MinecraftPing {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record PingResult(
            boolean online,
            int onlinePlayers,
            int maxPlayers,
            String version,
            String motd,
            long pingMs
    ) {
        public static PingResult offline() {
            return new PingResult(false, 0, 0, "", "", -1);
        }
    }

    public static PingResult ping(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeoutMs);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
            DataOutputStream handshakeOut = new DataOutputStream(handshakeBytes);
            writeVarInt(handshakeOut, 0x00);
            writeVarInt(handshakeOut, 47);
            writeString(handshakeOut, host);
            handshakeOut.writeShort(port);
            writeVarInt(handshakeOut, 1);

            writeVarInt(out, handshakeBytes.size());
            out.write(handshakeBytes.toByteArray());

            out.writeByte(0x01);
            out.writeByte(0x00);
            out.flush();

            readVarInt(in);
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                return PingResult.offline();
            }

            int length = readVarInt(in);
            byte[] data = new byte[length];
            in.readFully(data);
            long pingMs = System.currentTimeMillis() - start;

            String json = new String(data, StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(json);

            int onlinePlayers = root.path("players").path("online").asInt(0);
            int maxPlayers = root.path("players").path("max").asInt(0);
            String version = root.path("version").path("name").asText("");

            String motd = "";
            JsonNode desc = root.path("description");
            if (desc.isTextual()) {
                motd = desc.asText();
            } else if (desc.isObject() && desc.has("text")) {
                motd = desc.path("text").asText();
            }

            return new PingResult(true, onlinePlayers, maxPlayers, version, motd, pingMs);
        } catch (Exception e) {
            return PingResult.offline();
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int i = 0;
        int j = 0;
        while (true) {
            int k = in.readByte();
            i |= (k & 0x7F) << j++ * 7;
            if (j > 5) throw new RuntimeException("VarInt too big");
            if ((k & 0x80) != 128) break;
        }
        return i;
    }

    private static void writeString(DataOutputStream out, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}
