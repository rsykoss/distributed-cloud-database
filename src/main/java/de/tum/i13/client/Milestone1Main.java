package de.tum.i13.client;

import de.tum.i13.shared.LogSetup;
import de.tum.i13.shared.LogeLevelChange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.logging.Level;

import java.util.logging.Logger;

public class Milestone1Main {

    public static Logger logger = Logger.getLogger(Milestone1Main.class.getName());
    HidePasswordFromCommandLine hideThread = new HidePasswordFromCommandLine();
    ActiveConnection activeConnection = null;
    String loginState = "";
    String username = null;
    String password = null;
    Boolean admin = false;
    String jws = null;

    public static void main(String[] args) throws IOException {

        Milestone1Main mm = new Milestone1Main();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Closing Client");
            mm.close();
        }));
        // Login login = new Login();
        // login.login();
        mm.start();
    }

    public void close() {
        if (activeConnection != null) {
            try {
                activeConnection.close();
            } catch (Exception e) {
            }
        }
    }

    public void start() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        this.hideThread.start();

        for (;;) {
            System.out.print("EchoClient> ");
            String line = reader.readLine().toLowerCase();
            // String url =
            // "file:///C:/Users/User/gr10/src/main/java/de/tum/i13/client/index.html";
            // Document doc = Jsoup.parse(new URL(url), 3000);
            // Elements command = doc.select("input[name=commands]");
            // Elements input = doc.select("input[name=input]");
            // String line = command.attr("value") + " " + input.attr("value");
            logger.finest(line);
            if (!line.isEmpty()) {
                process(line);
            }
        }
    }

    public void process(String line) {

        String[] command = line.split(" ");
        logger.fine(String.format("command: %s", line));
        if (loginState.equals("username") && command.length == 1) {
            this.username = command[0];
            loginState = "password";
            printEchoLine("Please type in your password: ");
            this.hideThread.hideInput = true;
        } else if (loginState.equals("password") && command.length == 1) {
            this.hideThread.hideInput = false;
            this.password = command[0];
            loginState = "";
            keyValueOp(activeConnection, "login".split(" "), "login " + this.username + " " + this.password);
        } else if (command.length != 1 && (loginState.equals("username") || loginState.equals("password"))) {
            printEchoLine("Error! Should not have any space! Try again:");
        } else {
            switch (command[0].toLowerCase()) {
                case "connect":
                    activeConnection = buildConnection(command);

                    break;
                case "disconnect":
                    if (command.length == 1) {
                        closeConnection(activeConnection);
                        admin = false;
                    } else {
                        retUnknownCommand();
                    }
                    break;
                case "put":
                    if (command.length > 2) {
                        keyValueOp(activeConnection, command, line);
                    } else {
                        retUnknownCommand();
                    }
                    break;
                case "get":
                    if (command.length == 2) {
                        keyValueOp(activeConnection, command, line);
                    } else {
                        retUnknownCommand();
                    }
                    break;
                case "delete":
                    if (command.length == 2) {
                        keyValueOp(activeConnection, command, line);
                    } else {
                        retUnknownCommand();
                    }
                    break;
                case "loglevel":
                    changeLogLevel(command);
                    break;
                case "help":
                    printHelp();
                    break;
                case "keyrange":
                    keyRange(activeConnection, command, line);
                    break;
                case "keyrange_read":
                    keyRange(activeConnection, command, line);
                    break;
                case "quit":
                    if (command.length == 1) {
                        jws = null;
                        printEchoLine("Application exit!");
                        System.exit(0);
                        return;
                    } else {
                        retUnknownCommand();
                    }
                    break;
                case "createuser":
                    if (command.length == 4) {
                        if (admin == true) {
                            keyValueOp(activeConnection, command, line);
                            break;
                        } else {
                            System.out.println("Only Administrators can create users.");
                            break;
                        }
                    } else {
                        retUnknownCommand();
                    }
                case "deleteuser":
                    if (command.length == 2) {
                        if (admin == true) {
                            keyValueOp(activeConnection, command, line);
                            break;
                        } else {
                            System.out.println("Only Administrators can delete users.");
                            break;
                        }
                    } else {
                        retUnknownCommand();
                    }
                default:
                    retUnknownCommand();
            }
        }
    }

    /**
     * Changes loglevel to specified loglevel. Returns current loglevel if not
     * specified.
     *
     * @param command
     * @return loglevel
     */
    public void changeLogLevel(String[] command) {
        if (command.length != 2) {
            retUnknownCommand();
            return;
        }
        try {
            Level level = Level.parse(command[1]);
            LogeLevelChange logeLevelChange = LogSetup.changeLoglevel(level);
            printEchoLine(String.format("loglevel changed from: %s to: %s", logeLevelChange.getPreviousLevel(),
                    logeLevelChange.getNewLevel()));

        } catch (IllegalArgumentException ex) {
            printEchoLine("Unknown loglevel");
        }

    }

    /**
     * Shows help text for available commands.
     */
    public void printHelp() {
        System.out.println("Available commands:");
        System.out.println(
                "connect <address> <port> - Tries to establish a TCP- connection to the echo server based on the given server address and the port number of the echo service.");
        System.out.println("disconnect - Tries to disconnect from the connected server.");
        System.out.println(
                "put <key> <value> - Inserts a key-value pair into storage data structure; Updates current value if key for current entry is already specified; Deletes entry for given key if <value> = null");
        System.out.println("get <key> - Retrieves value for given key from storage server");
        System.out.println(String.format(
                "logLevel <level> - Sets the logger to the specified log level (%s | DEBUG | INFO | WARN | ERROR | FATAL | OFF)",
                Level.ALL.getName()));
        System.out.println("help - Display this help");
        System.out.println("quit - Tears down the active connection to the server and exits the program execution.");
    }

    private void retUnknownCommand() {
        printEchoLine("Unknown command");
    }

    private static void printEchoLine(String msg) {
        System.out.println("EchoClient> " + msg);
    }

    private static void closeConnection(ActiveConnection activeConnection) {
        if (activeConnection != null) {
            try {
                printEchoLine("Connection terminated");
                activeConnection.close();
            } catch (NullPointerException e) {
                logger.warning(e.toString());
                printEchoLine("Error! No connection to be terminated");
            } catch (Exception e) {
                activeConnection = null;
                logger.severe(e.toString());
            }
        }
    }

    /**
     * Sends a message to the server.
     *
     * @param activeConnection
     * @param command
     * @param line
     */
    public void sendmessage(ActiveConnection activeConnection, String[] command, String line) {
        if (command.length > 1) {
            if (activeConnection == null) {
                printEchoLine("Error! Not connected!");
                return;
            }
            int firstSpace = line.indexOf(" ");
            if (firstSpace == -1 || firstSpace + 1 >= line.length()) {
                printEchoLine("Error! Nothing to send!");
                return;
            }

            String cmd = line.substring(firstSpace + 1);
            activeConnection.write(cmd);

            try {
                printEchoLine(activeConnection.readline());
            } catch (IOException e) {
                printEchoLine("Error! Not connected!");
            }
        } else {
            printEchoLine("Please enter a message to be sent.");
        }

    }

    public ActiveConnection buildConnection(String[] command) {
        if (command.length == 3) {
            try {
                var kvcb = new EchoConnectionBuilder(command[1], Integer.parseInt(command[2]));
                logger.info("begin connecting");
                ActiveConnection ac = kvcb.connect();
                logger.info("connected");
                String confirmation = ac.readline();
                // printEchoLine(confirmation);
                if (confirmation.contains("Connection to KVCP server established:") && jws == null) {
                    printEchoLine("Please type in your username: ");
                    loginState = "username";
                } else if (jws == null) {
                    closeConnection(activeConnection);
                    loginState = "";
                    admin = false;
                }

                return ac;
            } catch (ConnectException e) {
                logger.severe(e.toString());
                printEchoLine("Could not connect to server");
            } catch (UnknownHostException e) {
                logger.severe(e.toString());
                printEchoLine("Unknown host. Check the host name or the ip address of the server");
            } catch (NumberFormatException e) {
                logger.severe(e.toString());
                printEchoLine("Port number should be a integer");
            } catch (IllegalArgumentException e) {
                logger.severe(e.toString());
                printEchoLine("The port number needs to be less than 65536");
            } catch (IOException e) {
                printEchoLine("Try again");
            }
        } else {
            printEchoLine(
                    "Correct Syntax should be: connect <address> <port> - no spaces allowed in address and port should be integer.");
            logger.warning("Wrong Syntax");
        }
        return null;
    }

    /**
     * Sends Key-Value Pair command to KVServer.
     * <p>
     * <code>put</code> Inserts a key-value pair into the storage server data
     * structures. Updates (overwrites) the current value with the given value if
     * the server already contains the specified key.Deletes the entry for the given
     * key if value equals null.
     * <p>
     * <code>get</code> Retrieves the value for the given key from the storage
     * server.
     *
     * @param activeConnection check wether the server is connected
     * @param command          command string given by the user
     * @param line             string of the whole sentence
     */
    private void keyValueOp(ActiveConnection activeConnection, String[] command, String line) {
        if (activeConnection == null) {
            printEchoLine("Error! Not connected!");
            return;
        }
        int firstSpace = line.indexOf(" ");
        if (firstSpace == -1 || firstSpace + 1 >= line.length()) {
            printEchoLine("Error! Nothing to put!");
            return;
        }

        activeConnection.write(jws + " " + line);

        try {
            String returned = activeConnection.readline();
            if (returned.equals("loginFail")) {
                jws = null;
                closeConnection(activeConnection);
                admin = false;
                printEchoLine("Login Fail!");
            } else if (returned.contains("adminLoginSuccess")) {
                admin = true;
                jws = returned.trim().split(" ", 2)[1];
                printEchoLine("Admin Login Success!");
            } else if (returned.contains("userLoginSuccess")) {
                admin = false;
                jws = returned.trim().split(" ", 2)[1];
                printEchoLine("User Login Success");
            } else {
                printEchoLine(returned);
            }
        } catch (IOException e) {
            printEchoLine("Error! Not connected!");
        }
    }

    private void keyRange(ActiveConnection activeConnection, String[] command, String line) {
        if (activeConnection == null) {
            printEchoLine("Error! Not connected!");
            return;
        } else if (command.length != 1) {
            retUnknownCommand();
            return;
        }
        activeConnection.write(jws + " " + line);

        try {
            printEchoLine(activeConnection.readline());
        } catch (IOException e) {
            printEchoLine("Error! Not connected!");
        }
    }
}
