package de.tum.i13.server.kv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.tum.i13.server.ECS.HashNode;

public class KVBackup implements Runnable {
    private static List<InetSocketAddress> servers = new ArrayList<InetSocketAddress>();
    // private String isReachable;
    KVServer kvServer;
    String cacheData;
    String jws;

    public KVBackup(KVServer kvServer, String jws) {
        this.jws = jws;
        this.kvServer = kvServer;
        InetSocketAddress replica1 = new InetSocketAddress(kvServer.replicas[0].ip, kvServer.replicas[0].port);
        InetSocketAddress replica2 = new InetSocketAddress(kvServer.replicas[1].ip, kvServer.replicas[1].port);
        servers.add(replica1);
        servers.add(replica2);
    }

    public void updateAddr() {
        System.out.println("updating addresses");
        InetSocketAddress replica1 = new InetSocketAddress(this.kvServer.replicas[0].ip,
                this.kvServer.replicas[0].port);
        InetSocketAddress replica2 = new InetSocketAddress(this.kvServer.replicas[1].ip,
                this.kvServer.replicas[1].port);
        servers.clear();
        servers.add(replica1);
        servers.add(replica2);
    }

    public void run() {

        BlockingQueue<Runnable> work = new ArrayBlockingQueue<Runnable>(5);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(5, // corePoolSize
                10, // maximumPoolSize
                5000, // keepAliveTime
                TimeUnit.MILLISECONDS, // TimeUnit
                work // workQueue
        );

        pool.prestartAllCoreThreads();

        pool.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                System.out.println("Work queue is currently full");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignore) {

                }
                executor.submit(r);
            }
        });

        Collection<Future<?>> futures = new LinkedList<Future<?>>();

        while (true) {
            HashNode node = new HashNode(this.kvServer.config.listenaddr, this.kvServer.config.port);
            try {
                this.cacheData = this.kvServer.getAll(node);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            for (InetSocketAddress server : servers) {
                futures.add(pool.submit(new ReplicationWork(server, this.cacheData, this.jws)));
            }
            for (Future<?> future : futures) {
                try {
                    String res = (String) future.get();
                    if (res != null) {
                        System.out.println(res);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            futures.clear();
            System.out.println("All servers checked. Will wait for 3000ms until next round");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public static class ReplicationWork implements Callable<String> {
        private static final int TIMEOUT = 5000;
        private InetSocketAddress target;
        private String data;
        private String jws;

        private ReplicationWork(InetSocketAddress target, String data, String jws) {
            this.target = target;
            this.data = data;
            this.jws = jws;
        }

        @Override
        public String call() {
            Socket connection = new Socket();
            // boolean reachable;
            try {
                if (this.data.length() == 0) {
                    return null;
                }
                connection.connect(target, TIMEOUT);
                PrintWriter output = new PrintWriter(connection.getOutputStream());
                BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                output.write(jws + " kvserver replicate " + this.data + "\r\n");
                output.flush();
                if (input.readLine().equals("success")) {
                    return "Back up success to " + target.getAddress().toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    connection.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }
}