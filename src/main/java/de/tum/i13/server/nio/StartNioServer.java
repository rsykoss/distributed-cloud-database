package de.tum.i13.server.nio;

import de.tum.i13.server.kv.KVCommandProcessor;
import de.tum.i13.server.kv.KVServer;
import de.tum.i13.shared.CommandProcessor;
import de.tum.i13.shared.Config;

import java.io.IOException;
import java.util.logging.Logger;

import static de.tum.i13.shared.Config.parseCommandlineArgs;
import static de.tum.i13.shared.LogSetup.setupLogging;

public class StartNioServer {

    public static Logger logger = Logger.getLogger(StartNioServer.class.getName());
    private static KVServer kvServer;
    private static boolean bound = false;

    public static void main(String[] args) throws IOException {
        Config cfg = parseCommandlineArgs(args); // Do not change this
        setupLogging(cfg.logfile, cfg.loglevel);
        logger.info("Config: " + cfg.toString());
        logger.info("starting server");

        kvServer = new KVServer(cfg);
        CommandProcessor nioServer = new KVCommandProcessor(kvServer);
        NioLoop sn = new NioLoop();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Closing NioServer");
            try {
                if (bound) {
                    kvServer.shutdown();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            sn.close();
        }));

        sn.bindSockets(cfg.listenaddr, cfg.port, nioServer);
        bound = true;
        kvServer.start(cfg);
        System.out.println("KV Server started");
        sn.start();
    }
}
