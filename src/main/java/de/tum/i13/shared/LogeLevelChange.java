package de.tum.i13.shared;

import java.util.logging.Level;

public class LogeLevelChange {
    private Level previousLevel;
    private Level newLevel;

    public LogeLevelChange(Level previousLevel, Level newLevel) {

        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }

    public Level getPreviousLevel() {
        return previousLevel;
    }

    public Level getNewLevel() {
        return newLevel;
    }
}
