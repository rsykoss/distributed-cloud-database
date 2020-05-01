package de.tum.i13.server.ECS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
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

public class Ping implements Runnable {
    private static List<InetSocketAddress> servers = new ArrayList<InetSocketAddress>();
    private String isReachable;
    ECS ecs;

    public Ping(ECS ecs) {
        this.ecs = ecs;
    }

    public Boolean isReachable() {
        if (this.isReachable.equals("true")) {
            return true;
        }
        return false;
    }

    public void removePing(String addr) {
        Iterator<InetSocketAddress> itr = servers.iterator();
        while (itr.hasNext()) {
            if (itr.next().toString().equals(addr)) {
                itr.remove();
            }
        }
    }

    public void addPing(String addr, int port) {
        try {
            servers.add(new InetSocketAddress(InetAddress.getByName(addr), port));
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + addr);
        }

        System.out.println("Total number of target servers: " + servers.size());
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
            for (InetSocketAddress server : servers) {
                futures.add(pool.submit(new PingWork(server)));
            }
            for (Future<?> future : futures) {
                try {
                    this.isReachable = (String) future.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
                if (!this.isReachable.equals("true")) {
                    removePing(this.isReachable);
                    try {
                        this.ecs.stop(this.isReachable);
                    } catch (NumberFormatException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            futures.clear();
            // System.out.println("All servers checked. Will wait for 700ms until next
            // round");
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public static class PingWork implements Callable<String> {
        private static final int TIMEOUT = 5000;
        private InetSocketAddress target;

        private PingWork(InetSocketAddress target) {
            this.target = target;
        }

        @Override
        public String call() {
            Socket connection = new Socket();
            boolean reachable;
            try {
                try {
                    connection.connect(target, TIMEOUT);
                } finally {
                    connection.close();
                }
                reachable = true;
            } catch (Exception e) {
                reachable = false;
            }

            if (!reachable) {
                System.out.println(String.format("%s:%d was UNREACHABLE", target.getAddress(), target.getPort()));
                return String.format("%s:%d", target.getAddress(), target.getPort());
            }
            return "true";
        }
    }
}