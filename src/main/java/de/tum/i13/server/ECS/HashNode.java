package de.tum.i13.server.ECS;

import de.tum.i13.shared.Node;

public class HashNode implements Node, Comparable<HashNode> {
    public final String ip;
    public final int port;

    public HashNode(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public String getKey() {
        return ip + ":" + port;
    }

    @Override
    public String toString() {
        return getKey();
    }

    public static void goRoute(HashRouter<HashNode> consistentHashRouter, String... requestIps) {
        for (String requestIp : requestIps) {
            System.out.println(requestIp + " is route to " + consistentHashRouter.routeNode(requestIp));
        }
    }

    @Override
    public int compareTo(HashNode o) {
        return 0;
    }
}