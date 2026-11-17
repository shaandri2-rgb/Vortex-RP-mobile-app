package com.vortexrp.mobile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Small SA-MP/open.mp UDP information query implementation.
 * A timeout means "unverified", not necessarily "offline": some servers disable queries.
 */
public final class ServerQuery {
    private ServerQuery() {}

    public static final class Info {
        public final boolean passworded;
        public final int players;
        public final int maxPlayers;
        public final String hostname;
        public final String gameMode;
        public final String language;
        public final long pingMs;

        Info(boolean passworded, int players, int maxPlayers, String hostname,
             String gameMode, String language, long pingMs) {
            this.passworded = passworded;
            this.players = players;
            this.maxPlayers = maxPlayers;
            this.hostname = hostname;
            this.gameMode = gameMode;
            this.language = language;
            this.pingMs = pingMs;
        }
    }

    public static Info query(String host, int port, int timeoutMs) throws IOException {
        if (host == null || host.trim().isEmpty()) throw new IOException("Server host is empty.");
        if (port < 1 || port > 65535) throw new IOException("Server port is invalid.");

        InetAddress address = InetAddress.getByName(host.trim());
        if (!(address instanceof Inet4Address)) {
            throw new IOException("The SA-MP information query protocol requires an IPv4 address.");
        }

        byte[] ip = address.getAddress();
        byte[] request = new byte[] {
                'S', 'A', 'M', 'P', ip[0], ip[1], ip[2], ip[3],
                (byte) (port & 0xff), (byte) ((port >>> 8) & 0xff), 'i'
        };

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            socket.connect(new InetSocketAddress(address, port));
            long started = System.nanoTime();
            socket.send(new DatagramPacket(request, request.length));

            byte[] response = new byte[4096];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;

            if (packet.getLength() < 5) throw new IOException("Server returned an incomplete query response.");
            ByteBuffer data = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength())
                    .slice().order(ByteOrder.LITTLE_ENDIAN);

            boolean passworded = (data.get() & 0xff) != 0;
            int players = unsignedShort(data);
            int maxPlayers = unsignedShort(data);
            String hostname = readString(data, 1024);
            String mode = readString(data, 512);
            String language = readString(data, 512);

            return new Info(passworded, players, maxPlayers, hostname, mode, language, elapsed);
        }
    }

    private static int unsignedShort(ByteBuffer data) throws IOException {
        if (data.remaining() < 2) throw new IOException("Server response ended before player counts.");
        return data.getShort() & 0xffff;
    }

    private static String readString(ByteBuffer data, int maxLength) throws IOException {
        if (data.remaining() < 4) throw new IOException("Server response ended before a text field.");
        long length = data.getInt() & 0xffffffffL;
        if (length > maxLength || length > data.remaining()) {
            throw new IOException("Server returned an invalid text field.");
        }
        byte[] bytes = new byte[(int) length];
        data.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
