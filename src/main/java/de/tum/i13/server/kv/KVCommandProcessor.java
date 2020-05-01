package de.tum.i13.server.kv;

import de.tum.i13.shared.CommandProcessor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class KVCommandProcessor implements CommandProcessor {
    public static Logger logger = Logger.getLogger(KVCommandProcessor.class.getName());
    private KVServer kvServer;
    private KVjwt kvJWT = new KVjwt();

    public KVCommandProcessor(KVServer kv) {
        this.kvServer = kv;
    }

    @Override
    public String process(String command) {
        String jws = command.trim().split(" ", 2)[0];
        String msgStr = command.trim().split(" ", 2)[1];
        logger.info("received command: " + msgStr.trim());
        try {
            String[] msg = msgStr.trim().split(" ");
            String decodedMsg = kvJWT.decodeJWT(jws);
            if (decodedMsg.equals("invalidToken")) {
                switch (msg[0].toLowerCase()) {
                    case "login": {
                        String user = msg[1];
                        String password = msg[2];
                        if (this.kvServer.login(user, password).equals("adminLoginSuccess")) {
                            return "adminLoginSuccess " + this.kvJWT.createJWT("admin", user);
                        } else if (this.kvServer.login(user, password).equals("userLoginSuccess")) {
                            return "userLoginSuccess " + this.kvJWT.createJWT("admin", user);
                        } else {
                            return "loginFail";
                        }
                    }
                    default: {
                        return command;
                    }
                }
            } else {
                switch (msg[0].toLowerCase()) {
                    case "get": {
                        String key = msg[1];
                        return this.kvServer.get(key);
                    }
                    case "put": {
                        if (msg.length > 2) {
                            String key = msg[1];
                            String val = (msgStr.trim().split(" ", 3)[2]);
                            return this.kvServer.put(key, val);
                        }
                    }
                    case "delete": {
                        String key = msg[1];
                        return this.kvServer.delete(key);
                    }
                    case "deleteall": {
                        String key = msg[1];
                        return this.kvServer.deleteAll(key);
                    }
                    case "keyrange": {
                        return this.kvServer.keyrange();
                    }
                    case "keyrange_read": {
                        return this.kvServer.keyrangeRead();
                    }
                    case "sendserver": {
                        this.kvServer.sendServer();
                        return "done";
                    }
                    case "kvserver": {
                        if (msg[1].equals("update_replica_ip")) {
                            System.out.println("kvserver update_replica");
                            String state = msg[2];
                            String replica = msg[3];
                            String addr = msg[4];
                            this.kvServer.updateReplica(replica, addr, state);
                            return "success";
                        } else if (msg[1].equals("initialise")) {
                            String replica1 = msg[2];
                            String replica2 = msg[3];
                            this.kvServer.init(replica1, replica2);
                            return "success";
                        } else if (msg[1].equals("replicate")) {
                            String data = msgStr.trim().split("replicate ")[1];
                            this.kvServer.backup(data);
                            return "success";
                        } else if (msg[1].equals("updatenodes")) {
                            String nodeInfo = msg[2];
                            this.kvServer.updateNodes(nodeInfo);
                            return "success";
                        } else
                            return "fail";
                    }
                    case "createuser": {
                        String level = msg[1];
                        String user = msg[2];
                        String password = msg[3];
                        return this.kvServer.createUser(level, user, password);
                    }
                    case "deleteuser": {
                        String user = msg[1];
                        return this.kvServer.deleteUser(user);
                    }
                    default: {
                        return "Unknown command";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning(e.toString());
            return e.toString();
        }
    }

    @Override
    public String connected(InetSocketAddress address, InetSocketAddress remoteAddress) {
        return "Connection to KVCP server established: " + address.toString() + "\r\n";
    }

    @Override
    public void connectionClosed(InetAddress address) {
        // logger.info("connection closed: " + address.toString());
    }
}
