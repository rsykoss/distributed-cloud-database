package de.tum.i13.server.ECS;

import de.tum.i13.server.kv.KVjwt;
import de.tum.i13.shared.CommandProcessor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class ECSCommandProcessor implements CommandProcessor {
    public static Logger logger = Logger.getLogger(ECSCommandProcessor.class.getName());
    private ECS ecs;
    private KVjwt ecsJWT = new KVjwt();

    public ECSCommandProcessor(ECS ecs) {
        this.ecs = ecs;
    }

    @Override
    public String process(String command) {
        String jws = command.trim().split(" ", 2)[0];
        String msgStr = command.trim().split(" ", 2)[1];
        logger.info("received command: " + msgStr.trim());
        try {
            String[] msg = msgStr.trim().split(" ");
            String decodedMsg = ecsJWT.decodeJWT(jws);
            if (decodedMsg.equals("invalidToken")) {
                return decodedMsg;
            } else {
                switch (msg[0].toLowerCase()) {
                    case "start": {
                        String listenaddr = msg[1];
                        int port = Integer.valueOf(msg[2]);
                        String message = this.ecs.start(listenaddr, port);
                        if (message == null) {
                            return "Initialised Server on Hashring";
                        }
                        /*
                         * else if (this.ecs.serverList.size() == 3) { return
                         * "Initialised Server on Hashring & Starting Replication " + message; }
                         */
                        else {
                            return "Initialised Server on Hashring & Replicating " + message;
                        }

                        // return "Initialised Server on Hashring";
                    }
                    case "keyrange": {
                        return this.ecs.keyrange();
                    }
                    case "keyrange_read": {
                        return this.ecs.keyrangeRead();
                    }
                    case "goroute": {
                        return this.ecs.goRoute(msg[1]);
                    }
                    case "shutdown": {
                        String addr = msg[1];
                        String cache = command.split(" ", 3)[2];
                        return this.ecs.serverShutdown(addr, cache);
                    }
                    case "sendserver": {
                        String cache = command.split(" ", 2)[1];
                        this.ecs.sendServer(cache);
                        return "done";
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
        logger.info("new connection: " + remoteAddress.toString());
        return "Connection to ECS server established: " + address.toString() + "\r\n";
    }

    @Override
    public void connectionClosed(InetAddress address) {
        logger.info("connection closed: " + address.toString());
    }
}
