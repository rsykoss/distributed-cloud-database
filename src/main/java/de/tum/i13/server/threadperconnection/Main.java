package de.tum.i13.server.threadperconnection;

import de.tum.i13.server.echo.EchoLogic;
import de.tum.i13.shared.CommandProcessor;
import de.tum.i13.shared.Config;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static de.tum.i13.shared.Config.parseCommandlineArgs;
import static de.tum.i13.shared.LogSetup.setupLogging;

public class Main {

    public static void main(String[] args) throws IOException {
        Config cfg = parseCommandlineArgs(args);  //Do not change this
        setupLogging(cfg.logfile, cfg.loglevel);

        final ServerSocket serverSocket = new ServerSocket();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Closing thread per connection kv server");
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //bind to localhost only
        serverSocket.bind(new InetSocketAddress(cfg.listenaddr, cfg.port));

        //Replace with your Key value server logic.
        // If you use multithreading you need locking
        CommandProcessor logic = new EchoLogic();

        while (true) {
            Socket clientSocket = serverSocket.accept();

            //When we accept a connection, we start a new Thread for this connection
            Thread th = new ConnectionHandleThread(logic, clientSocket);
            th.start();
        }
    }
}
