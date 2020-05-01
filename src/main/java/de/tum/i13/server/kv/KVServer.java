package de.tum.i13.server.kv;

import de.tum.i13.server.ECS.HashNode;
import de.tum.i13.shared.Config;
import de.tum.i13.shared.Constants;

import static de.tum.i13.shared.Cmd.*;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class KVServer {
    // No locking, unsafe, do not use!! replace with your own

    public static Logger logger = Logger.getLogger(KVServer.class.getName());
    public Config config;
    private String state;
    private KVStore kvStore;
    private PrintWriter outputECS, outputKV;
    private BufferedReader inputECS, inputKV;
    private Socket ecsS, kvS;
    public HashNode[] replicas = new HashNode[2];
    public KVBackup backup;
    public ExecutorService executor = Executors.newSingleThreadExecutor();
    String jws = new KVjwt().createJWT("server", "server");

    public KVServer(Config config) throws UnknownHostException, IOException {
        this.state = SERVER_STOPPED.toString();
        this.config = config;
        kvStore = new KVStore(config);
        connectECS();
        inputECS.readLine();
        this.state = "normal";
    }

    public void start(Config config) throws IOException {
        // logger.info(sendECS("start " + config.listenaddr + " " + config.port));
        String rp = sendECS("start " + config.listenaddr + " " + config.port);
        logger.info(rp);

        if (rp.contains("Replicating ")) {
            // catch when the split do not contain & etc
            rp = rp.split("Replicating ")[1];
            HashNode[] affected = new HashNode[2];
            String[] res = rp.split("//");
            // System.out.println(rp.split("//")[0]);
            this.replicas[0] = new HashNode(res[0].split("&")[0].split(":")[0],
                    Integer.parseInt(res[0].split("&")[0].split(":")[1]));
            this.replicas[1] = new HashNode(res[0].split("&")[1].split(":")[0],
                    Integer.parseInt(res[0].split("&")[1].split(":")[1]));
            logger.info("Linked to replica 1 - " + this.replicas[0].getKey() + " & replica 2 - "
                    + this.replicas[1].getKey());
            affected[0] = new HashNode(res[1].split("&")[0].split(":")[0],
                    Integer.parseInt(res[1].split("&")[0].split(":")[1]));
            affected[1] = new HashNode(res[1].split("&")[1].split(":")[0],
                    Integer.parseInt(res[1].split("&")[1].split(":")[1]));
            logger.info("Affected - " + affected[0].getKey() + " & " + affected[1].getKey());
            // retrieve data from A & B and update C & A about new node(itself)
            // - do periodic replication
            if (this.replicas[0].getKey().equals(affected[1].getKey())
                    && this.replicas[1].getKey().equals(affected[0].getKey())) {
                // 3 servers active => initialise
                logger.info(initRep(this.replicas));
            } else {
                logger.info(updateAffected(affected[1], 2, "add"));
                logger.info(updateAffected(affected[0], 1, "add"));
                this.backup = new KVBackup(this, jws);
                executor.submit(this.backup);
            }
        }
    }

    /**
     * Rebalancing the affected node
     *
     * @param affected
     * @throws IOException
     */
    public void serverToServer(HashNode affected) throws IOException {
        kvS = new Socket(affected.ip, affected.port);
        outputKV = new PrintWriter(kvS.getOutputStream());
        inputKV = new BufferedReader(new InputStreamReader(kvS.getInputStream()));
    }

    /**
     * Initiate replication
     *
     * @param affected
     * @return
     */
    public String initRep(HashNode[] affected) {
        try {
            serverToServer(affected[0]);
            outputKV.write(jws + " kvserver initialise " + this.replicas[1].getKey() + " " + this.config.listenaddr
                    + ":" + this.config.port + "\r\n");
            outputKV.flush();
            String msg = inputKV.readLine();
            serverToServer(affected[1]);
            outputKV.write(jws + " kvserver initialise " + this.config.listenaddr + ":" + this.config.port + " "
                    + this.replicas[0].getKey() + "\r\n");
            outputKV.flush();
            msg += "\n" + inputKV.readLine();
            kvS.close();
            this.backup = new KVBackup(this, jws);
            executor.submit(this.backup);
            return msg;

        } catch (ConnectException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Initiate the replicas
     *
     * @param replica1
     * @param replica2
     */
    public void init(String replica1, String replica2) {
        // System.out.println(replica1 + " and " + replica2);
        this.replicas[0] = new HashNode(replica1.split(":")[0], Integer.parseInt(replica1.split(":")[1]));
        this.replicas[1] = new HashNode(replica2.split(":")[0], Integer.parseInt(replica2.split(":")[1]));
        this.backup = new KVBackup(this, jws);
        this.backup.updateAddr();
        logger.info("Replica 1 set to " + this.replicas[0].getKey());
        logger.info("Replica 2 set to " + this.replicas[1].getKey());
        executor.submit(this.backup);
    }

    /**
     * Reallocating the data on the coordinator node when a node is added or removed
     *
     * @param affected node
     * @param n        replica number
     * @return String
     */
    public String updateAffected(HashNode affected, int n, String state) {
        try {
            serverToServer(affected);
            if (state.equals("add")) {
                outputKV.write(jws + " kvserver update_replica_ip " + state + " " + n + " " + this.config.listenaddr
                        + ":" + this.config.port + "\r\n");
            } else {
                outputKV.write(jws + " kvserver update_replica_ip " + state + " " + n + " " + this.config.listenaddr
                        + ":" + this.config.port + "//" + this.replicas[0].getKey() + "\r\n");
            }
            outputKV.flush();
            String msg = inputKV.readLine();
            kvS.close();
            return msg;

        } catch (ConnectException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Reallocating the data on the replicas when a node is added or removed
     *
     * @param replicaNum
     * @param addr
     */
    public void updateReplica(String replicaNum, String addr, String state) {
        HashNode node = null;
        if (state.equals("add")) {
            node = new HashNode(addr.split(":")[0], Integer.parseInt(addr.split(":")[1]));
        } else {
            node = new HashNode(addr.split("//")[0].split(":")[0], Integer.parseInt(addr.split("//")[0].split(":")[1]));
        }
        if (Integer.parseInt(replicaNum) == 1 && state.equals("add")) {
            this.replicas[1] = this.replicas[0];
            this.replicas[0] = node;
            logger.info("Replica 1 updated to " + this.replicas[0].getKey());
            logger.info("Replica 2 updated to " + this.replicas[1].getKey());
        } else if (Integer.parseInt(replicaNum) == 1 && state.equals("remove")) {
            HashNode nxtNode = new HashNode(addr.split("//")[1].split(":")[0],
                    Integer.parseInt(addr.split("//")[1].split(":")[1]));
            this.replicas[0] = node;
            this.replicas[1] = nxtNode;
            logger.info("Replica 1 updated to " + this.replicas[0].getKey());
            logger.info("Replica 2 updated to " + this.replicas[1].getKey());
        } else if (Integer.parseInt(replicaNum) == 2) {
            this.replicas[1] = node;
            logger.info("Replica 2 updated to " + this.replicas[1].getKey());
        } else {
            logger.warning("wrong replica number");
        }
        this.backup.updateAddr();
        // System.out.println("updating Replica done");
    }

    public void updateNodes(String nodeInfo) {
        HashNode[] affected = new HashNode[2];
        String[] msg = nodeInfo.split("//");
        this.replicas[0] = new HashNode(msg[0].split("&")[0].split(":")[0],
                Integer.parseInt(msg[0].split("&")[0].split(":")[1]));
        this.replicas[1] = new HashNode(msg[0].split("&")[1].split(":")[0],
                Integer.parseInt(msg[0].split("&")[1].split(":")[1]));
        affected[0] = new HashNode(msg[1].split("&")[0].split(":")[0],
                Integer.parseInt(msg[1].split("&")[0].split(":")[1]));
        affected[1] = new HashNode(msg[1].split("&")[1].split(":")[0],
                Integer.parseInt(msg[1].split("&")[1].split(":")[1]));
        logger.info(updateAffected(affected[1], 2, "remove"));
        logger.info(updateAffected(affected[0], 1, "remove"));
    }

    public void backup(String data) throws IOException {
        // receives command to back up data
        // System.out.println(data);
        // Map<String, String> specificData = new HashMap<String,String>();
        if (data.length() == 2) {
            return;
        }
        String cached = data.replace("{", "").replace("}", "");
        String[] kvPair = cached.split(", ");
        // System.out.println(kvPair);
        for (int i = 0; i < kvPair.length; i++) {
            String key = kvPair[i].split("=")[0];
            String value = kvPair[i].split("=")[1];
            putAll(key, value);
        }
    }

    public void connectECS() {
        try {
            this.ecsS = new Socket();
            this.ecsS.connect(config.bootstrap);
            // this.ecsS = new Socket("127.0.1.1", 5152);
            this.outputECS = new PrintWriter(ecsS.getOutputStream());
            this.inputECS = new BufferedReader(new InputStreamReader(ecsS.getInputStream()));
        } catch (ConnectException e) {
            logger.warning("Start ECS Server first before starting KVServer");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() throws IOException {
        sendECS("shutdown " + config.listenaddr + ":" + config.port + " " + kvStore.cache.toString());
        this.ecsS.close();
    }

    /**
     * Rebalancing Shifting the existing data from the server that is shutting down
     * to the reallocated server from the hash ring
     *
     * @throws IOException
     */
    public void sendServer() throws IOException {
        this.state = SERVER_WRITE_LOCK.toString();
        if (!kvStore.cache.isEmpty()) {
            for (Map.Entry<String, String> entry : kvStore.cache.entrySet()) {
                String addr = sendECS("goRoute " + entry.getKey());
                if (!addr.equals(config.listenaddr + ":" + config.port)) {
                    // System.out.println("Sending to "+ addr);
                    // sendECS("sendserver "+ entry.toString() +"{}");
                    Socket s = new Socket(addr.split(":")[0], Integer.valueOf(addr.split(":")[1]));
                    PrintWriter output = new PrintWriter(s.getOutputStream());
                    BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    output.write(jws + " put" + " " + entry.getKey() + " " + entry.getValue() + "\r\n");
                    output.flush();
                    System.out.println(input.readLine());
                    System.out.println(input.readLine());
                    s.close();
                    // kvStore.cache.remove(entry.getKey());
                }
            }
        }
        this.state = "normal";
    }

    /**
     * Inserts or updates key-value pair in storage.
     * <p>
     * Calls Config.java file for passing of displacement strategy
     * <p>
     * Calls initDisk, displacementStrat and putStrategy functions
     *
     * @param key   Arbitary string with max length of 20 bytes
     * @param value Arbitary string with max length of 20 bytes
     * @return whether insertion or update is successful
     * @throws IOException
     */
    public String put(String key, String value) throws IOException {
        // System.out.println(getHash(key));
        if (checkAllState(key) != null) {
            return this.state;
        } else {
            try {
                if (kvStore.cache == null) {
                    // create cache
                    logger.info("New cache created");
                    kvStore.cache = new ConcurrentHashMap<String, String>();
                }
                String exists = kvStore.searchDisk(key);
                if (!kvStore.cache.containsKey(key) && exists == null) {
                    // when there are no existing key in cache & disk
                    logger.info("No existing key: " + key);
                    if (kvStore.cache.size() >= this.config.cachesize) {
                        // cache is filled
                        logger.info("Cache is full - Size allocated is " + this.config.cachesize);
                        kvStore.initDisk();
                        kvStore.displacementStrat();
                    }
                    kvStore.putStrategy(key, value);
                    logger.info("Cache: " + kvStore.cache);
                    return PUT_SUCCESS + " " + key;

                } else if (!kvStore.cache.containsKey(key) && exists != null) {
                    // when there are existing key in disk
                    logger.info("Key: " + key + " exists in the disk");
                    if (kvStore.cache.size() >= this.config.cachesize) {
                        // cache is filled
                        logger.info("Cache is full - Size allocated is " + this.config.cachesize);
                        kvStore.displacementStrat();
                    }
                    kvStore.deleteFromDisk(key);
                    kvStore.putStrategy(key, value);

                } else {
                    // when there are existing key in cache
                    logger.info("Key: " + key + " exists in cache");
                    kvStore.cache.replace(key, value);
                    kvStore.getStrategy(key);

                }
                logger.info("Cache: " + kvStore.cache);
                return PUT_UPDATE + " " + key;

            } catch (Exception e) {
                e.printStackTrace();
                logger.warning(e.toString());
                return PUT_ERROR + " " + key + " " + value;
            }
        }
    }

    public String putAll(String key, String value) throws IOException {
        if (checkWriteLock() && checkStopped()) {
            return this.state;
        } else {
            try {
                if (kvStore.cache == null) {
                    // create cache
                    logger.info("New cache created");
                    kvStore.cache = new ConcurrentHashMap<String, String>();
                }
                String exists = kvStore.searchDisk(key);
                if (!kvStore.cache.containsKey(key) && exists == null) {
                    // when there are no existing key in cache & disk
                    logger.info("No existing key: " + key);
                    if (kvStore.cache.size() >= this.config.cachesize) {
                        // cache is filled
                        logger.info("Cache is full - Size allocated is " + this.config.cachesize);
                        kvStore.initDisk();
                        kvStore.displacementStrat();
                    }
                    kvStore.putStrategy(key, value);
                    logger.info("Cache: " + kvStore.cache);
                    return PUT_SUCCESS + " " + key;

                } else if (!kvStore.cache.containsKey(key) && exists != null) {
                    // when there are existing key in disk
                    logger.info("Key: " + key + " exists in the disk");
                    if (exists.equals(value)) {
                        return "no need to update";
                    }
                    if (kvStore.cache.size() >= this.config.cachesize) {
                        // cache is filled
                        logger.info("Cache is full - Size allocated is " + this.config.cachesize);
                        kvStore.displacementStrat();
                    }
                    kvStore.deleteFromDisk(key);
                    kvStore.putStrategy(key, value);

                } else {
                    // when there are existing key in cache
                    logger.info("Key: " + key + " exists in cache");
                    if (kvStore.cache.get(key).equals(value)) {
                        return "no need to update";
                    }
                    kvStore.cache.replace(key, value);
                    kvStore.getStrategy(key);

                }
                logger.info("Cache: " + kvStore.cache);
                return PUT_UPDATE + " " + key;

            } catch (Exception e) {
                e.printStackTrace();
                logger.warning(e.toString());
                return PUT_ERROR + " " + key + " " + value;
            }
        }
    }

    /**
     * Retrieves the value for the given key from the storage server.
     * <p>
     * Calls searchDisk, displacementStrat, putStrategy, checkStopped,
     * checkNotResonsible functions
     *
     * @param key indicates the desired value with not more than 20 bytes
     * @return value of given key
     * @throws IOException
     */
    public String get(String key) throws IOException {
        String value = kvStore.cache.get(key);
        if (value == null) {
            // does not exist in cache
            value = kvStore.searchDisk(key);

            if (value == null) {
                // does not exist in disk & cache
                logger.info("Key: " + key + " not found");
                logger.info("Cache: " + kvStore.cache);
                if (checkNotResponsibleAll(key)) {
                    return SERVER_NOT_RESPONSIBLE + " " + sendECS("keyrange");
                }
                List<String> partialGet = new ArrayList<String>();
                Integer count = 0;
                for (Entry<String, String> e : kvStore.cache.entrySet()) {
                    if (e.getKey().startsWith(key)) {
                        partialGet.add(e.getKey());
                        count++;
                    }
                    if (count >= Constants.MAX_PARTIAL_GET) {
                        break;
                    }
                }
                if (count < Constants.MAX_PARTIAL_GET) {
                    partialGet = kvStore.searchDiskPartial(key, partialGet, count);
                }
                if (partialGet.size() == 0) {
                    return GET_ERROR + " " + key + " Key not found";
                } else {
                    return GET_ERROR + " " + key + " not found. Did you mean " + partialGet.toString();
                }
            } else {
                // exists in disk
                logger.info("Key: " + key + " found in disk");
                if (kvStore.cache.size() >= this.config.cachesize) {
                    // exists in disk + cache full
                    kvStore.displacementStrat();
                }
                kvStore.putStrategy(key, value);
            }
        } else {
            // exists in cache
            kvStore.getStrategy(key);
        }
        logger.info("Cache: " + kvStore.cache);
        return GET_SUCCESS + " " + key + " " + value;
    }

    /**
     * Gets all of its key-value pair
     *
     * @param node desired key-value pair
     * @return description of specific data
     * @throws IOException Signals that an I/O exception of some sort has occurred.
     *                     This class is the general class of exceptions produced by
     *                     failed or interrupted I/O operations.
     */
    public String getAll(HashNode node) throws IOException {
        Map<String, String> specificData = new HashMap<String, String>();
        // System.out.println(kvStore.cache);
        for (Map.Entry<String, String> entry : kvStore.cache.entrySet()) {
            if (!checkNotResponsible(entry.getKey())) {
                specificData.put(entry.getKey(), entry.getValue());
            }
        }
        return specificData.toString().replace("{", "").replace("}", "");
    }

    /**
     * Deletes the value for given key.
     *
     * @param key desired value that wish to be deleted.
     * @return whether deletion is successful.
     * @throws IOException Signals that an I/O exception of some sort has occurred.
     *                     This class is the general class of exceptions produced by
     *                     failed or interrupted I/O operations.
     */
    public String delete(String key) throws IOException {
        if (checkAllState(key) != null) {
            return this.state;
        } else {
            try {
                String returned = kvStore.deleteStrategy(key);
                String exists = kvStore.cache.remove(key);
                if (this.replicas[0] != null && this.replicas[1] != null) {
                    serverToServer(this.replicas[0]);
                    outputKV.write(jws + " deleteall " + key + "\r\n");
                    outputKV.flush();
                    inputKV.readLine();
                    kvS.close();
                    serverToServer(this.replicas[1]);
                    outputKV.write(jws + " deleteall " + key + "\r\n");
                    outputKV.flush();
                    inputKV.readLine();
                    kvS.close();
                }
                if (exists == null && returned == null) {
                    // No key found to delete
                    throw new NullPointerException();
                } else {
                    // deleted from cache
                    logger.info("Key: " + key + " deleted successfully from cache");
                }
                logger.info("Cache: " + kvStore.cache);
                return DELETE_SUCCESS + " " + key;

            } catch (NullPointerException e) {
                logger.info("Key: " + key + " no key found to be deleted");
                return DELETE_ERROR + " " + key;

            } catch (Exception e) {
                e.printStackTrace();
                logger.info("Key: " + key + " deleted unsuccessfully");
                return DELETE_ERROR + " " + key;
            }
        }
    }

    public String deleteAll(String key) throws IOException {
        if (checkWriteLock() && checkStopped()) {
            return this.state;
        } else {
            try {
                String returned = kvStore.deleteStrategy(key);
                String exists = kvStore.cache.remove(key);
                if (exists == null && returned == null) {
                    // No key found to delete
                    throw new NullPointerException();
                } else {
                    // deleted from cache
                    logger.info("Key: " + key + " deleted successfully from cache");
                }
                logger.info("Cache: " + kvStore.cache);
                return DELETE_SUCCESS + " " + key;

            } catch (NullPointerException e) {
                logger.info("Key: " + key + " no key found to be deleted");
                return DELETE_ERROR + " " + key;

            } catch (Exception e) {
                e.printStackTrace();
                logger.info("Key: " + key + " deleted unsuccessfully");
                return DELETE_ERROR + " " + key;
            }
        }
    }

    /**
     * Gives keyrange of the servers.
     *
     * @return key range if successful
     * @throws IOException Signals that an I/O exception of some sort has occurred.
     *                     This class is the general class of exceptions produced by
     *                     failed or interrupted I/O operations.
     */
    public String keyrange() throws IOException {
        // return a range of hash value - the start index and the end index - hash value
        if (checkStopped()) {
            return this.state;
        } else {
            return sendECS(KEYRANGE.toString());
        }
    }

    /**
     * Gives all ranges and corresponding kvstores which can fullfill get requests
     *
     * @return a range of hash value
     * @throws IOException Signals that an I/O exception of some sort has occurred.
     *                     This class is the general class of exceptions produced by
     *                     failed or interrupted I/O operations.
     */
    public String keyrangeRead() throws IOException {
        // return a range of hash value - the start index and the end index - hash value
        if (checkStopped()) {
            return this.state;
        } else {
            return sendECS(KEYRANGE_READ.toString());
        }
    }

    /**
     * Parse the given message to ECS
     *
     * @param msg to be send to ECS
     * @return A string of text read from ECS
     * @throws IOException Signals that an I/O exception of some sort has occurred.
     *                     class is the general class of exceptions produced by
     *                     failed or interrupted I/O operations.
     */
    public String sendECS(String msg) throws IOException {
        outputECS.write(jws + " " + msg + "\r\n");
        outputECS.flush();
        return inputECS.readLine();
    }

    /**
     * Arbitrary request
     *
     * @return error description
     */
    public String any() {
        String description = "Message format unknown";
        logger.info("Arbitrary request");
        return "ERROR! " + description;
    }

    private boolean checkStopped() throws IOException {
        if (this.state.equals(SERVER_STOPPED.toString())) {
            return true;
        } else {
            return false;
        }
    }

    private boolean checkNotResponsible(String key) throws IOException {
        if (!correctKeyRange(key)) {
            this.state = SERVER_NOT_RESPONSIBLE + " " + sendECS(KEYRANGE.toString());
            return true;
        } else {
            this.state = "normal";
            return false;
        }
    }

    private boolean checkNotResponsibleAll(String key) throws IOException {
        if (!correctKeyRangeAll(key)) {
            this.state = SERVER_NOT_RESPONSIBLE + " " + sendECS(KEYRANGE_READ.toString());
            return true;
        } else {
            this.state = "normal";
            return false;
        }
    }

    private boolean checkWriteLock() throws IOException {
        if (this.state.equals(SERVER_WRITE_LOCK.toString())) {
            return true;
        } else {
            return false;
        }
    }

    private boolean correctKeyRange(String key) throws NumberFormatException, IOException {
        // when the hashrouter function is defined in ecs call the hash() function
        // through that.
        String addr = sendECS("goRoute " + key);
        // System.out.println(addr);
        String hash = getHash(key);
        if (hash == "") {
            logger.severe("Hash of key error");
            throw new NullPointerException("Hash error");
        }
        if (addr.equals(config.listenaddr + ":" + config.port)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean correctKeyRangeAll(String key) throws NumberFormatException, IOException {
        // when the hashrouter function is defined in ecs call the hash() function
        // through that.
        String addr = sendECS("goRoute " + key);
        String hash = getHash(key);
        if (hash == "") {
            logger.severe("Hash of key error");
            throw new NullPointerException("Hash error");
        }
        if (addr.equals(config.listenaddr + ":" + config.port)) {
            return true;
        } else if (addr.equals(this.replicas[0].getKey()) || addr.equals(this.replicas[1].getKey())) {
            return true;
        } else {
            return false;
        }
    }

    private String checkAllState(String key) throws IOException {
        // determine the server state here
        checkWriteLock();
        boolean responsible = checkNotResponsible(key);
        checkStopped();
        // System.out.println(this.state);

        if (this.state == SERVER_STOPPED.toString()) {
            // check if server stopped
            // later use cmd.java
            return this.state;
        } else if (responsible) {
            return this.state;
        } else if (this.state == SERVER_WRITE_LOCK.toString()) {
            return this.state;
        } else
            return null;

    }

    private String getHash(String key) {
        StringBuffer hexString = new StringBuffer();
        try {
            byte[] msgByte = key.getBytes(StandardCharsets.ISO_8859_1);
            MessageDigest md;
            md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(msgByte);
            // for (int i = 0; i < 4; i++) {
            // hash <<= 8;
            // hash |= ((int) digest[i]) & 0xFF;
            // }
            for (int i = 0; i < digest.length; i++) {
                String hex = Integer.toHexString(0xFF & digest[i]);
                if (hex.length() == 1)
                    hexString.append('0');

                hexString.append(hex);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return hexString.toString();
    }

    public String login(String user, String password) {
        return kvStore.auth(user, password);
    }

    public String createUser(String level, String user, String password) {
        return kvStore.createUser(level, user, password);
    }

    public String deleteUser(String user) {
        return kvStore.deleteUser(user);
    }
}
