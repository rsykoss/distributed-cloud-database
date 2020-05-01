package de.tum.i13.shared;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.*;

public class LogSetup {
    public static void setupLogging(Path logfile, String loglevel) {
        Logger logger = LogManager.getLogManager().getLogger("");
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] %5$s %6$s%n");

        FileHandler fileHandler = null;
        try {
            fileHandler = new FileHandler(logfile.getFileName().toString(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        fileHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(fileHandler);

        Level parsedLevel = Level.parse(loglevel);
        for (Handler h : logger.getHandlers()) {
            h.setLevel(parsedLevel);
        }
        logger.setLevel(parsedLevel); // we want log everything
    }

    public static LogeLevelChange changeLoglevel(Level newLevel) {
        Logger logger = LogManager.getLogManager().getLogger("");
        Level previousLevel = logger.getLevel();

        for (Handler h : logger.getHandlers()) {
            h.setLevel(newLevel);
        }
        logger.setLevel(newLevel);
        return new LogeLevelChange(previousLevel, newLevel);

    }
}