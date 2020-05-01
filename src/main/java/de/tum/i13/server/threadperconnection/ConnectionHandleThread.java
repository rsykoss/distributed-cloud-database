package de.tum.i13.server.threadperconnection;

import de.tum.i13.shared.CommandProcessor;
import de.tum.i13.shared.Constants;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class ConnectionHandleThread extends Thread {
    private CommandProcessor cp;
    private Socket clientSocket;

    public ConnectionHandleThread(CommandProcessor commandProcessor, Socket clientSocket) {
        this.cp = commandProcessor;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), Constants.TELNET_ENCODING));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), Constants.TELNET_ENCODING));


            // InetAddress remoteAddress = this.clientSocket.getInetAddress();
            // InetAddress localAddress = this.clientSocket.getLocalAddress();

            //this.cp.connected(localAddress., remoteAddress);


            String firstLine;
            while ((firstLine = in.readLine()) != null) {
                String res = cp.process(firstLine);
                out.write(res);
                out.flush();
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }
}
