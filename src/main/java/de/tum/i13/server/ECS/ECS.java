package de.tum.i13.server.ECS;

import static de.tum.i13.shared.Cmd.*;
import de.tum.i13.shared.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.*;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import de.tum.i13.server.kv.KVjwt;
import de.tum.i13.shared.Cmd;

public class ECS {
    ConcurrentSkipListMap<String, Node> serverList = new ConcurrentSkipListMap<String, Node>();
    public static Logger logger = Logger.getLogger(ECS.class.getName());
    HashNode node;
    private HashRouter<Node> consistentHashRouter = new HashRouter<>();
    boolean status = false;
    Cmd cmd;
    Ping ping = new Ping(this);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    NavigableMap<Long, Object[]> metadata = new TreeMap<>();
    PrintWriter output;
    BufferedReader input;
    String jws = new KVjwt().createJWT("server", "ecs");

    /**
     * Start External Configuration Service to initialize and control the storage
     * system. Start hashing when the first server is started.
     * <p>
     *
     * @param listenaddr
     * @param port
     * @throws InterruptedException     Thrown when a thread is waiting, sleeping,
     *                                  or otherwise occupied, and the thread is
     *                                  interrupted, either before or during the
     *                                  activity.
     * @throws ExecutionException       Exception thrown when attempting to retrieve
     *                                  the result of a task that aborted by
     *                                  throwing an exception.
     * @throws NoSuchAlgorithmException This exception is thrown when a particular
     *                                  cryptographic algorithm is requested but is
     *                                  not available in the environment.
     * @throws IOException
     */
    public String start(String listenaddr, int port)
            throws InterruptedException, ExecutionException, NoSuchAlgorithmException, IOException {
        // this is to start hashing
        logger.info("Start hashing");
        this.node = new HashNode(listenaddr, port);
        String key = this.consistentHashRouter.getHash(listenaddr + ":" + port);
        this.serverList.put(key, this.node);
        // System.out.println(serverList);
        try {
            this.ping.addPing(listenaddr, port);
            executor.submit(ping);
        } catch (Exception e) {
            e.printStackTrace();
            // failed to ping
        }
        this.consistentHashRouter.addNode(this.node, 1);
        if (this.serverList.size() > 1) {
            // System.out.println(serverList);
            Entry<String, Node> newServer = higherNode(key);
            // System.out.println(newServer.getValue().getKey().split(":")[0]+":"+Integer.valueOf(newServer.getValue().getKey().split(":")[1]));
            Socket s = new Socket(newServer.getValue().getKey().split(":")[0],
                    Integer.valueOf(newServer.getValue().getKey().split(":")[1]));
            this.output = new PrintWriter(s.getOutputStream());
            this.input = new BufferedReader(new InputStreamReader(s.getInputStream()));
            output.write(jws + " sendserver\r\n");
            output.flush();
            System.out.println(input.readLine());
            s.close();
        }
        if (this.serverList.size() >= 3) {
            // starts to replicate
            // String msg = higherNode(key).getValue().getKey().split(":")[0] + ":" +
            // higherNode(key).getValue().getKey().split(":")[1];
            // msg += "&" +
            // higherNode(higherNode(key).getKey()).getValue().getKey().split(":")[0] + ":"
            // + higherNode(higherNode(key).getKey()).getValue().getKey().split(":")[1];
            // msg += "//" + lowerNode(key).getValue().getKey().split(":")[0] + ":" +
            // lowerNode(key).getValue().getKey().split(":")[1];
            // msg += "&" +
            // lowerNode(lowerNode(key).getKey()).getValue().getKey().split(":")[0] + ":" +
            // lowerNode(lowerNode(key).getKey()).getValue().getKey().split(":")[1];
            String msg = updateNodes(key);
            return msg;
        } else {
            return null;
        }
    }

    public Entry<String, Node> higherNode(String key) {
        if (this.serverList.higherEntry(key) == null) {
            return this.serverList.firstEntry();
        } else
            return this.serverList.higherEntry(key);
    }

    public Entry<String, Node> lowerNode(String key) {
        if (this.serverList.lowerEntry(key) == null) {
            return this.serverList.lastEntry();
        } else
            return this.serverList.lowerEntry(key);
    }

    public void stop(String stopAddr) throws NumberFormatException, UnknownHostException, IOException {
        String addr = stopAddr.split("/")[1];
        // Entry<Long, Node> lNode = lowerNode(this.consistentHashRouter.getHash(addr));
        Entry<String, Node> newCoordNode = higherNode(this.consistentHashRouter.getHash(addr));
        this.consistentHashRouter.removeNode(serverList.get(this.consistentHashRouter.getHash(addr)));
        serverList.remove(this.consistentHashRouter.getHash(addr));
        logger.info(stopAddr + " crashed. " + newCoordNode.getValue().getKey().split(":")[0] + ":"
                + Integer.parseInt(newCoordNode.getValue().getKey().split(":")[1]) + "is the new coordinator node.");
        Socket s = new Socket(newCoordNode.getValue().getKey().split(":")[0],
                Integer.parseInt(newCoordNode.getValue().getKey().split(":")[1]));
        this.output = new PrintWriter(s.getOutputStream());
        this.input = new BufferedReader(new InputStreamReader(s.getInputStream()));
        output.write(jws + " kvserver updatenodes " + updateNodes(newCoordNode.getKey()) + "\r\n");
        output.flush();
        System.out.println(input.readLine());
        s.close();
        // long hNode = higherNode(this.consistentHashRouter.getHash(addr)).getKey();
        /*
         * for (Map.Entry<Long, Node> entry : serverList.entrySet()){ if
         * (entry.getKey().equals(lNode)){ System.out.println(entry.getKey()); Socket s
         * = new
         * Socket(entry.getValue().getKey().split(":")[0],Integer.valueOf(entry.getValue
         * ().getKey().split(":")[1])); this.output = new
         * PrintWriter(s.getOutputStream()); this.input = new BufferedReader(new
         * InputStreamReader(s.getInputStream())); output.write("kvserver updatenodes "
         * + updateNodes(entry.getKey()) + "\r\n"); output.flush();
         * System.out.println(input.readLine()); s.close(); } }
         */
    }

    public void sendServer(String cache) throws NumberFormatException, UnknownHostException, IOException {
        // System.out.println(cache);
        if (cache.length() == 2) {
            return;
        }
        String cached = cache.replace("{", "").replace("}", "");
        String[] kvPair = cached.split(", ");
        // System.out.println(kvPair);
        for (int i = 0; i < kvPair.length; i++) {
            String key = kvPair[i].split("=")[0];
            String value = kvPair[i].split("=")[1];
            String addr = String.valueOf(this.consistentHashRouter.routeNode(key));
            Socket s = new Socket(addr.split(":")[0], Integer.valueOf(addr.split(":")[1]));
            this.output = new PrintWriter(s.getOutputStream());
            this.input = new BufferedReader(new InputStreamReader(s.getInputStream()));
            output.write(jws + " put" + " " + key + " " + value + "\r\n");
            output.flush();
            System.out.println(input.readLine());
            s.close();
        }
    }

    /**
     * Returns the key range corresponding to this entry.
     * <p>
     *
     * @return String of key range that it is responsible for.
     */
    public String keyrange() {
        String msg = KEYRANGE_SUCCESS.toString();
        String hashVal = serverList.lastKey();
        for (Map.Entry<String, Node> entry : serverList.entrySet()) {
            String allButLast = hashVal.substring(0, hashVal.length() - 1);
            hashVal = allButLast + (char) (hashVal.charAt(hashVal.length() - 1) + 1);
            msg += " " + (hashVal) + "," + entry.getKey() + "," + entry.getValue() + ";";
            hashVal = entry.getKey();
        }
        return msg;
    }

    /**
     * Return a string when keyrange is successfully read
     *
     * @return a message string
     */
    public String keyrangeRead() {
        String msg = KEYRANGE_READ_SUCCESS.toString();
        String hashVal = lowerNode(lowerNode(serverList.lastKey()).getKey()).getKey();
        for (Map.Entry<String, Node> entry : serverList.entrySet()) {
            String allButLast = hashVal.substring(0, hashVal.length() - 1);
            hashVal = allButLast + (char) (hashVal.charAt(hashVal.length() - 1) + 1);
            msg += " " + (hashVal) + "," + entry.getKey() + "," + entry.getValue() + ";";
            hashVal = lowerNode(lowerNode(entry.getKey()).getKey()).getKey();
        }
        return msg;
    }

    public String updateNodes(String key) {
        String msg = higherNode(key).getValue().getKey().split(":")[0] + ":"
                + higherNode(key).getValue().getKey().split(":")[1];
        msg += "&" + higherNode(higherNode(key).getKey()).getValue().getKey().split(":")[0] + ":"
                + higherNode(higherNode(key).getKey()).getValue().getKey().split(":")[1];
        msg += "//" + lowerNode(key).getValue().getKey().split(":")[0] + ":"
                + lowerNode(key).getValue().getKey().split(":")[1];
        msg += "&" + lowerNode(lowerNode(key).getKey()).getValue().getKey().split(":")[0] + ":"
                + lowerNode(lowerNode(key).getKey()).getValue().getKey().split(":")[1];
        return msg;
    }

    /**
     * Returns the hash value corresponding to this entry.
     * <p>
     *
     * @param addr
     * @param port
     * @return long hash
     * @throws NoSuchAlgorithmException This exception is thrown when a particular
     *                                  cryptographic algorithm is requested but is
     *                                  not available in the environment.
     */
    public String getHash(String addr, int port) throws NoSuchAlgorithmException {
        // need to find the address of the correct prev node
        String hash = null;
        hash = this.consistentHashRouter.getHash(addr + ":" + port);
        return hash;
    }

    public String goRoute(String requestIp) {
        return String.valueOf(this.consistentHashRouter.routeNode(requestIp));
    }

    public String serverShutdown(String addr, String cache)
            throws NumberFormatException, UnknownHostException, IOException {
        this.consistentHashRouter.removeNode(serverList.get(this.consistentHashRouter.getHash(addr)));
        serverList.remove(this.consistentHashRouter.getHash(addr));
        this.ping.removePing(addr);
        sendServer(cache);
        return String.format("%s shutdown", addr);
    }
}