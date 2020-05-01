package de.tum.i13;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.tum.i13.client.ActiveConnection;
import de.tum.i13.client.EchoConnectionBuilder;
import de.tum.i13.server.ECS.ECS;
import de.tum.i13.server.ECS.ECSCommandProcessor;
import de.tum.i13.server.kv.KVCommandProcessor;
import de.tum.i13.server.kv.KVServer;
import de.tum.i13.server.nio.NioLoop;
import de.tum.i13.shared.CommandProcessor;
import de.tum.i13.shared.Config;

import org.apache.commons.lang.time.StopWatch;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestEnronData {
    private static final String enronFilePath = "data/emails.csv";
    private static Map<String, String> list = new HashMap<>();
    // private static Map<String, Integer> load = new HashMap<>();

    public static Integer port = 5152;
    private static NioLoop sn, sn1, sn2, sn3, sn4, sn5;
    private static Thread th, th1, th2, th3, th4, th5;

    private static ECS ecs;

    public static StopWatch timer = new StopWatch();
    public static Long putTime;
    public static Long getTime;

    private static String jwt = null;

    @BeforeAll
    public static void beforeAll() throws IOException, InterruptedException {
        final Reader reader = Files.newBufferedReader(Paths.get(enronFilePath));
        final CSVParser csvParser = new CSVParser(reader,
                CSVFormat.DEFAULT.withHeader("file", "message").withSkipHeaderRecord());
        int count = 0;
        final int kvPairLimit = 100;
        for (final CSVRecord csvRecord : csvParser) {
            if (count < kvPairLimit) {
                final String key = csvRecord.get("file");
                final String value = csvRecord.get("message").replace("\n", " ").trim();
                list.put(key, value);
            } else {
                break;
            }
            count++;
        }
        System.out.println(list.size());
        csvParser.close();

        ecs = new ECS();
        CommandProcessor ecsserver = new ECSCommandProcessor(ecs);
        sn = new NioLoop();
        sn.bindSockets("127.0.1.1", 5152, ecsserver);
        th = new Thread() {
            @Override
            public void run() {
                try {
                    sn.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        th.start();
        Thread.sleep(2000);

        Config config1 = new Config();
        config1.bootstrap = new InetSocketAddress("127.0.1.1", 5152);
        config1.listenaddr = "127.0.0.1";
        config1.port = 5153;
        config1.cachesize = 3;
        config1.cachedisplacement = "FIFO";
        String fileName = "data/";
        config1.dataDir = Paths.get(fileName);
        config1.logfile = Paths.get("echo.log");
        config1.loglevel = "OFF";
        if (!Files.exists(config1.dataDir)) {
            try {
                Files.createDirectory(config1.dataDir);
            } catch (IOException e) {
                System.out.println("Could not create directory");
                e.printStackTrace();
                System.exit(-1);
            }
        }
        KVServer kv1 = new KVServer(config1);
        CommandProcessor kvcp1 = new KVCommandProcessor(kv1);
        sn1 = new NioLoop();

        sn1.bindSockets(config1.listenaddr, config1.port, kvcp1);
        th1 = new Thread() {
            @Override
            public void run() {
                try {
                    kv1.start(config1);
                    sn1.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        th1.start();
        Thread.sleep(2000);

        Config config2 = new Config();
        config2.bootstrap = new InetSocketAddress("127.0.1.1", 5152);
        config2.listenaddr = "127.0.0.1";
        config2.port = 5155;
        config2.cachesize = 3;
        config2.cachedisplacement = "FIFO";
        config2.dataDir = Paths.get(fileName);
        config2.logfile = Paths.get("echo.log");
        config2.loglevel = "OFF";
        if (!Files.exists(config2.dataDir)) {
            try {
                Files.createDirectory(config2.dataDir);
            } catch (IOException e) {
                System.out.println("Could not create directory");
                e.printStackTrace();
                System.exit(-1);
            }
        }
        KVServer kv2 = new KVServer(config2);
        CommandProcessor kvcp2 = new KVCommandProcessor(kv2);
        sn2 = new NioLoop();

        sn2.bindSockets(config2.listenaddr, config2.port, kvcp2);
        th2 = new Thread() {
            @Override
            public void run() {
                try {
                    kv2.start(config2);
                    sn2.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        th2.start();
        Thread.sleep(2000);

        Config config3 = new Config();
        config3.bootstrap = new InetSocketAddress("127.0.1.1", 5152);
        config3.listenaddr = "127.0.0.1";
        config3.port = 5157;
        config3.cachesize = 3;
        config3.cachedisplacement = "FIFO";
        config3.dataDir = Paths.get(fileName);
        config3.logfile = Paths.get("echo.log");
        config3.loglevel = "OFF";
        if (!Files.exists(config3.dataDir)) {
            try {
                Files.createDirectory(config3.dataDir);
            } catch (IOException e) {
                System.out.println("Could not create directory");
                e.printStackTrace();
                System.exit(-1);
            }
        }
        KVServer kv3 = new KVServer(config3);
        CommandProcessor kvcp3 = new KVCommandProcessor(kv3);
        sn3 = new NioLoop();

        sn3.bindSockets(config3.listenaddr, config3.port, kvcp3);
        th3 = new Thread() {
            @Override
            public void run() {
                try {
                    kv3.start(config3);
                    sn3.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        th3.start();
        Thread.sleep(2000);

        Config config4 = new Config();
        config4.bootstrap = new InetSocketAddress("127.0.1.1", 5152);
        config4.listenaddr = "127.0.0.1";
        config4.port = 5159;
        config4.cachesize = 3;
        config4.cachedisplacement = "FIFO";
        config4.dataDir = Paths.get(fileName);
        config4.logfile = Paths.get("echo.log");
        config4.loglevel = "OFF";
        if (!Files.exists(config4.dataDir)) {
            try {
                Files.createDirectory(config4.dataDir);
            } catch (IOException e) {
                System.out.println("Could not create directory");
                e.printStackTrace();
                System.exit(-1);
            }
        }
        KVServer kv4 = new KVServer(config4);
        CommandProcessor kvcp4 = new KVCommandProcessor(kv4);
        sn4 = new NioLoop();

        sn4.bindSockets(config4.listenaddr, config4.port, kvcp4);
        th4 = new Thread() {
            @Override
            public void run() {
                try {
                    kv4.start(config4);
                    sn4.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        th4.start();
        Thread.sleep(2000);

        Config config5 = new Config();
        config5.bootstrap = new InetSocketAddress("127.0.1.1", 5152);
        config5.listenaddr = "127.0.0.1";
        config5.port = 5151;
        config5.cachesize = 3;
        config5.cachedisplacement = "FIFO";
        config5.dataDir = Paths.get(fileName);
        config5.logfile = Paths.get("echo.log");
        config5.loglevel = "OFF";
        if (!Files.exists(config5.dataDir)) {
            try {
                Files.createDirectory(config5.dataDir);
            } catch (IOException e) {
                System.out.println("Could not create directory");
                e.printStackTrace();
                System.exit(-1);
            }
        }
        KVServer kv5 = new KVServer(config5);
        CommandProcessor kvcp5 = new KVCommandProcessor(kv5);
        sn5 = new NioLoop();

        sn5.bindSockets(config5.listenaddr, config5.port, kvcp5);
        th5 = new Thread() {
            @Override
            public void run() {
                try {
                    kv5.start(config5);
                    sn5.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        th5.start();
        Thread.sleep(2000);

    }

    @AfterAll
    public static void afterAll() {
        System.out.println("Put time: " + putTime);
        System.out.println("Get time: " + getTime);
        th.interrupt();
        th1.interrupt();
        th2.interrupt();
        th3.interrupt();
        th4.interrupt();
        th5.interrupt();
        sn.close();
        sn1.close();
        sn2.close();
        sn3.close();
        sn4.close();
        sn5.close();
    }

    @Test
    public void putTest() throws Exception {
        ActiveConnection login = new EchoConnectionBuilder("127.0.0.1", 5153).connect();
        assertThat(login.readline(), is(containsString("Connection to KVCP server established")));
        login.write(jwt + " login admin password");
        String ans = login.readline();
        assertThat(ans, is(containsString("LoginSuccess")));
        login.close();
        jwt = ans.trim().split(" ", 2)[1];

        timer.start();
        for (Map.Entry<String, String> entry : list.entrySet()) {
            String addr = ecs.goRoute(entry.getKey());
            // if (!load.containsKey(addr)) {
            // load.put(addr, 0);
            // }
            // int value = (load.get(addr)) + 1;
            // load.replace(addr, value);
            ActiveConnection ac = new EchoConnectionBuilder(addr.split(":")[0], Integer.valueOf(addr.split(":")[1]))
                    .connect();
            assertThat(ac.readline(), is(containsString("Connection to KVCP server established")));
            ac.write(jwt + " put " + entry.getKey() + " " + entry.getValue());
            String res = ac.readline();
            assertThat(res, is(containsString("PUT_SUCCESS " + entry.getKey())));
            ac.close();
        }
        // System.out.println(load);
        timer.stop();
        putTime = timer.getTime();
    }

    @Test
    public void getTest() throws Exception {
        timer.reset();
        timer.start();

        for (Map.Entry<String, String> entry : list.entrySet()) {
            String addr = ecs.goRoute(entry.getKey());
            // if (!load.containsKey(addr)) {
            // load.put(addr, 0);
            // }
            // int value = (load.get(addr)) + 1;
            // load.replace(addr, value);
            ActiveConnection ac = new EchoConnectionBuilder(addr.split(":")[0], Integer.valueOf(addr.split(":")[1]))
                    .connect();
            assertThat(ac.readline(), is(containsString("Connection to KVCP server established")));
            ac.write(jwt + " get " + entry.getKey());
            String res = ac.readline();
            assertThat(res, is(containsString("GET_SUCCESS " + entry.getKey())));
            ac.close();
        }
        // System.out.println(load);
        timer.stop();
        getTime = timer.getTime();
    }
}