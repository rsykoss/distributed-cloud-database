package de.tum.i13;

import java.io.IOException;
import java.net.ServerSocket;

public class Util {

    public static int getFreePort() throws IOException {

        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
