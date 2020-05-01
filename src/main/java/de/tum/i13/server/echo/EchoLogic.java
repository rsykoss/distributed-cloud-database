package de.tum.i13.server.echo;

import de.tum.i13.shared.CommandProcessor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class EchoLogic implements CommandProcessor {
    public static Logger logger = Logger.getLogger(EchoLogic.class.getName());

    public String process(String command) {

        logger.info("received command: " + command.trim());

        return command;
    }

    @Override
    public String connected(InetSocketAddress address, InetSocketAddress remoteAddress) {
        logger.info("new connection: " + remoteAddress.toString());

        return "Connection to MSRG Echo server established: " + address.toString() + "\r\n";
    }

    @Override
    public void connectionClosed(InetAddress remoteAddress) {
        logger.info("connection closed: " + remoteAddress.toString());
    }
}
