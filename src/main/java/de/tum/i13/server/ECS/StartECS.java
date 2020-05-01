package de.tum.i13.server.ECS;

import de.tum.i13.server.nio.NioLoop;
import de.tum.i13.shared.Config;
import static de.tum.i13.shared.Config.parseCommandlineArgs;
import static de.tum.i13.shared.LogSetup.setupLogging;

import java.io.IOException;
import java.util.logging.Logger;

public class StartECS {
    public static Logger logger = Logger.getLogger(StartECS.class.getName());

    public static void main(String[] args) throws IOException {
        Config cfg = parseCommandlineArgs(args); // Do not change this
        setupLogging(cfg.logfile, cfg.loglevel);
        logger.info("Config: " + cfg.toString());
        logger.info("starting server");

        NioLoop sn = new NioLoop();
        ECS ecs = new ECS();
        ECSCommandProcessor ecsCommandProcessor = new ECSCommandProcessor(ecs);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Closing ECS");
            sn.close();
        }));

        // sn.bindSockets("127.0.1.1", 5152, ecsCommandProcessor);
        sn.bindSockets(cfg.listenaddr, cfg.port, ecsCommandProcessor);

        System.out.println("ECS Server started");
        sn.start();

    }
}