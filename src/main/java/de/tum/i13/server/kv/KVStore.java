package de.tum.i13.server.kv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import de.tum.i13.shared.Config;
import de.tum.i13.shared.Constants;

public class KVStore {
    public Queue<String> queue;
    public Map<String, String> cache = new HashMap<>();
    public Map<String, Integer> count;
    private Config config;
    private Path filePath, tempPath, userPath, userTempPath;
    public static Logger logger = Logger.getLogger(KVStore.class.getName());

    public KVStore(Config config) {
        this.config = config;
    }

    /**
     * First in first out displacement strategy.
     * <p>
     * When the cache is full, put the first key inside the cache in the disk and
     * deletes it in the cache.
     */
    public void FIFO() {
        try {
            String removedKey = queue.poll();
            logger.info("FIFO - displace to disk: " + removedKey);
            putToDisk(removedKey, cache.get(removedKey));
            cache.remove(removedKey);
        } catch (Exception e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
    }

    public void putFIFO(String key, String value) {
        logger.info("FIFO - put key: " + key);
        cache.put(key, value);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<String>();
        }
        queue.add(key);
    }

    public void getFIFO(String key) {
        logger.info("FIFO - get key: " + key);
    }

    public String deleteFIFO(String key) {
        logger.info("FIFO - delete key: " + key);
        queue.remove(key);
        return deleteFromDisk(key);

    }

    /**
     * Least Recently Used displacement strategy.
     * <p>
     * When the key exists in the cache, deletes it and adds it back in the cache so
     * that it will be the last in line. When the cache is full, move the least
     * recently used key in the disk and deltes in from the cache.
     */
    public void LRU() {
        try {
            String removedKey = queue.poll();
            logger.info("LRU - displace to disk: " + removedKey);
            putToDisk(removedKey, cache.get(removedKey));
            cache.remove(removedKey);
        } catch (Exception e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
    }

    public void putLRU(String key, String value) {
        logger.info("LRU - put key: " + key);
        cache.put(key, value);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<String>();
        }
        queue.add(key);
    }

    public void getLRU(String key) {
        logger.info("LRU - get key: " + key);
        String value = cache.get(key);
        cache.put(key, value);
        if (cache.containsKey(key)) {
            queue.remove(key);
            queue.add(key);
        }
    }

    public String deleteLRU(String key) {
        logger.info("LRU - delete key: " + key);
        queue.remove(key);
        return deleteFromDisk(key);
    }

    /**
     * Least Frequently used displacement strategy.
     * <p>
     * Creates another list to store the number of time it is used. When the cache
     * is full, move the least frequently used key in the disk and deletes it from
     * the cache.
     */
    public void LFU() {
        try {
            Map.Entry<String, Integer> min = null;
            for (Map.Entry<String, Integer> entry : count.entrySet()) {
                if ((min == null || min.getValue() > entry.getValue()) && cache.containsKey(entry.getKey())) {
                    min = entry;
                }
            }
            String removedKey = min.getKey();
            logger.info("LFU - displace to disk: " + removedKey);
            putToDisk(removedKey, cache.get(removedKey));
            cache.remove(removedKey);
        } catch (Exception e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
    }

    public void putLFU(String key, String value) {
        logger.info("LFU - put key: " + key);
        if (count == null) {
            count = new ConcurrentHashMap<String, Integer>();
        }
        count.compute(key, (k, v) -> v == null ? 1 : v + 1);
        cache.put(key, value);
    }

    public void getLFU(String key) {
        logger.info("LFU - get key: " + key);
        if (count == null) {
            count = new ConcurrentHashMap<String, Integer>();
        }
        count.compute(key, (k, v) -> v == null ? 1 : v + 1);
        cache.get(key);
    }

    public String deleteLFU(String key) {
        logger.info("LFU - delete key: " + key);
        count.remove(key);
        return deleteFromDisk(key);
    }

    /**
     * 3 different cache displacement strategies.
     * <p>
     * Displaces a key-value pair from cache to disk.
     */
    public void displacementStrat() {
        // FIFO, LRU, LFU
        logger.finest(this.config.cachedisplacement);
        logger.fine(String.format("Displacement strategy: %s", this.config.cachedisplacement));
        switch (this.config.cachedisplacement) {
            case "FIFO":
                FIFO();
                break;
            case "LRU":
                LRU();
                break;
            case "LFU":
                LFU();
                break;
            default:
                logger.severe(this.config.cachedisplacement
                        + " is not correct displacement strategy. Please restart the server with a correct strategy");
        }
    }

    /**
     * Put key and value in cache using respective strategy.
     *
     * @param key   from user input
     * @param value from user input
     */
    public void putStrategy(String key, String value) {
        // FIFO, LRU, LFU
        logger.fine(String.format("Displacement strategy: %s", this.config.cachedisplacement));
        switch (this.config.cachedisplacement) {
            case "FIFO":
                putFIFO(key, value);
                break;
            case "LRU":
                putLRU(key, value);
                break;
            case "LFU":
                putLFU(key, value);
                break;
            default:
                logger.severe(this.config.cachedisplacement
                        + " is not correct displacement strategy. Please restart the server with a correct strategy");
        }
    }

    /**
     * Gets value from the given key using the respective strategy.
     *
     * @param key from user input
     */
    public void getStrategy(String key) {
        // FIFO, LRU, LFU
        logger.fine(String.format("Displacement strategy: %s", this.config.cachedisplacement));
        switch (this.config.cachedisplacement) {
            case "FIFO":
                getFIFO(key);
                break;
            case "LRU":
                getLRU(key);
                break;
            case "LFU":
                getLFU(key);
                break;
            default:
                logger.severe(this.config.cachedisplacement
                        + " is not correct displacement strategy. Please restart the server with a correct strategy");
        }
    }

    /**
     * Deletes value for given key using the respective strategy.
     *
     * @param key from user input
     */
    public String deleteStrategy(String key) {
        // FIFO, LRU, LFU
        logger.fine(String.format("Delete strategy: %s", this.config.cachedisplacement));
        switch (this.config.cachedisplacement) {
            case "FIFO":
                return deleteFIFO(key);
            case "LRU":
                return deleteLRU(key);
            case "LFU":
                return deleteLFU(key);
            default:
                logger.severe(this.config.cachedisplacement
                        + " is not correct displacement strategy. Please restart the server with a correct strategy");
                return null;
        }
    }

    /**
     * Creates a persistent storage.
     */
    public void initDisk() {
        // When there is no disk created yet. Need to create one
        try {
            // Create file
            this.filePath = (this.config.dataDir.resolve("KeyValuePairStorage.txt"));
            if (Files.notExists(this.filePath)) {
                logger.info("Creating disk file");
                Files.createFile(this.filePath);
            }
        } catch (InvalidPathException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        } catch (FileAlreadyExistsException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        } catch (Exception e) {
            logger.warning(e.toString());
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Search if key exists in persistent storage.
     *
     * @param key from user input
     * @return If <b>key</b> exists, returns value then deletes key
     *         <li>If <b>key</b> does not exists, return null
     */
    public String searchDisk(String key) {
        initDisk();
        String value = null;
        try {
            BufferedReader reader = Files.newBufferedReader(this.filePath, Charset.forName("UTF-8"));
            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                String firstWord = currentLine.split(",", 2)[0];
                if (firstWord.equals(key)) {
                    value = currentLine.trim().split(",", 2)[1];
                    logger.info("Key: " + key + " found in disk");
                }
            }
            reader.close();

        } catch (NoSuchFileException e) {
        } catch (FileAlreadyExistsException e) {
        } catch (IOException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        } catch (InvalidPathException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
        return value;
    }

    public List<String> searchDiskPartial(String key, List<String> list, Integer count) {
        initDisk();
        try {
            BufferedReader reader = Files.newBufferedReader(this.filePath, Charset.forName("UTF-8"));
            String currentLine;
            while ((currentLine = reader.readLine()) != null && count < Constants.MAX_PARTIAL_GET) {
                String firstWord = currentLine.split(",", 2)[0];
                if (firstWord.contains(key)) {
                    list.add(firstWord);
                    count++;
                }
            }
            reader.close();
        } catch (IOException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
        return list;
    }

    public String deleteFromDisk(String key) {
        initDisk();
        String value = null;
        try {
            this.tempPath = (this.config.dataDir
                    .resolve(this.config.listenaddr + this.config.port + "KeyValuePairTemp.txt"));
            if (Files.notExists(this.tempPath)) {
                Files.createFile(this.tempPath);
            }
            BufferedReader reader = Files.newBufferedReader(this.filePath, Charset.forName("UTF-8"));
            BufferedWriter writer = Files.newBufferedWriter(this.tempPath, Charset.forName("UTF-8"));
            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                String firstWord = currentLine.split(",", 2)[0];
                if (firstWord.equals(key)) {
                    value = currentLine.trim().split(",", 2)[1];
                    logger.info("Key: " + key + " deleted successfully from disk");
                    continue;
                }
                writer.write(currentLine + System.getProperty("line.separator"));
            }
            writer.close();
            reader.close();
            Files.move(this.tempPath, this.filePath, StandardCopyOption.REPLACE_EXISTING);

        } catch (NoSuchFileException e) {
        } catch (FileAlreadyExistsException e) {
        } catch (IOException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        } catch (InvalidPathException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
        return value;
    }

    /**
     * Writes to persistent storage.
     *
     * @param key   from user input
     * @param value from user input
     */
    private void putToDisk(String key, String value) {
        try (BufferedWriter writer = Files.newBufferedWriter(this.filePath, Charset.forName("UTF-8"),
                StandardOpenOption.APPEND)) {
            writer.append(key + "," + value + "\n");
            writer.flush();
        } catch (IOException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
    }

    public void initUser() {
        // When there is no disk created yet. Need to create one
        try {
            // Create file
            this.userPath = (this.config.dataDir.resolve("UserList.txt"));
            if (Files.notExists(this.userPath)) {
                logger.info("Creating user file");
                Files.createFile(this.userPath);
                BufferedWriter writer = Files.newBufferedWriter(this.userPath, Charset.forName("UTF-8"));
                writer.write("admin,admin,password" + "\n");
                writer.flush();
            }
        } catch (InvalidPathException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        } catch (FileAlreadyExistsException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        } catch (Exception e) {
            logger.warning(e.toString());
            System.err.println("Error: " + e.getMessage());
        }
    }

    public String searchUser(String key) {
        initUser();
        String value = null;
        String level = null;
        try {
            BufferedReader reader = Files.newBufferedReader(this.userPath, Charset.forName("UTF-8"));
            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                String firstWord = currentLine.split(",", 3)[1];
                System.out.println(firstWord);
                if (firstWord.equals(key)) {
                    value = currentLine.trim().split(",", 3)[2];
                    level = currentLine.trim().split(",", 3)[0];
                    logger.info("Key: " + key + " found in User List.");
                    continue;
                }
            }
            reader.close();

        } catch (NoSuchFileException e) {
        } catch (FileAlreadyExistsException e) {
        } catch (IOException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        } catch (InvalidPathException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
        return value + "," + level;
    }

    public String createUser(String level, String user, String password) {
        initUser();
        if (!level.equals("admin") && !level.equals("user")) {
            return "Invalid credential rights. Please define \"admin\" or \"user\".";
        }
        if (!searchUser(user).equals(null + "," + null)) {
            return "User already exists. Choose a different username.";
        }
        this.userPath = (this.config.dataDir.resolve("UserList.txt"));
        BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(this.userPath, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
            writer.append(level + "," + user + "," + password + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "User created.";
    }

    public String deleteUser(String user) {
        initDisk();
        int lines = 0;
        try {
            this.userTempPath = (this.config.dataDir
                    .resolve(this.config.listenaddr + this.config.port + "UserListTemp.txt"));
            if (Files.notExists(this.userTempPath)) {
                Files.createFile(this.userTempPath);
            }
            BufferedReader reader = Files.newBufferedReader(this.userPath, Charset.forName("UTF-8"));
            BufferedWriter writer = Files.newBufferedWriter(this.userTempPath, Charset.forName("UTF-8"));
            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                String firstWord = currentLine.split(",", 3)[1];
                if (firstWord.equals(user)) {
                    logger.info("User: " + user + " deleted successfully from disk");
                    continue;
                }
                writer.write(currentLine + System.getProperty("line.separator"));
                lines++;
            }
            writer.close();
            reader.close();
            if (lines <= 1) {
                Files.deleteIfExists(this.userTempPath);
                return "Cannot delete the only user in system!";
            }
            Files.move(this.userTempPath, this.userPath, StandardCopyOption.REPLACE_EXISTING);

        } catch (NoSuchFileException e) {
        } catch (FileAlreadyExistsException e) {
        } catch (IOException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        } catch (InvalidPathException e) {
            logger.warning(e.toString());
            e.printStackTrace();
        }
        return user + " deleted";
    }

    public String auth(String user, String password) {
        System.out.println(searchUser(user));
        if (searchUser(user).trim().split(",", 2)[0].equals(password)) {
            if (searchUser(user).trim().split(",", 2)[1].equals("admin")) {
                return "adminLoginSuccess";
            } else {
                return "userLoginSuccess";
            }
        }
        return "loginFail";
    }
}