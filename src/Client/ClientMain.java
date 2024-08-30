package Client;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import Commons.RMIRegistrationInterface;
import Commons.RMICallbackInterface;

public class ClientMain {
    private static String username;                          
    private static SocketChannel client;                     
    private static int ONLINE = 0;                           
    private static ClientNotifyInterface stub; // remote object of callbackObj
    private static RMICallbackInterface server; // server remote interface
    private static ClientNotifyInterface callbackObj; // client interface
    private static HashMap<String, MulticastInfos> projects;
    private static DatagramSocket ds;                        
    private static final int BASE_SIZE = 256; // default size of RX ByteBuffer
    private static final int MAX_SIZE = 2048; // maximum size of RX ByteBuffer

    final static String NOT_LOGGED = "Before performing any operation, you should be logged in.";
    private static boolean flag = true; // flag triggered on shutdown or errors
    private static int portS;
    private static String hostS;

    public static void main(String[] args) {
        projects = new HashMap<>();

        if (args.length == 0) {
            System.out.println("Usage: java ClientMain host [port]");
            return;
        }
        try {
            hostS = args[0];
            portS = Integer.parseInt(args[1]);
        } catch (RuntimeException ex) {
            System.out.println("Invalid port. Error");
            return;
        }

        /* DatagramSocket used to send messages to projects' chats */
        try {
            ds = new DatagramSocket(2000 + (new Random()).nextInt(8000));
        } catch (SocketException e) {
            e.printStackTrace();
        }

        /* Parse user input and send the request to the server */
        try {
            Scanner s = new Scanner(System.in);
            String input;
            System.out.println("Type \"help\" to show all the commands.");
            do {
                System.out.print("\n> ");
                input = s.nextLine();

                if (ONLINE == 1) checkNotifications();
                if (flag) parser(input);
            } while (flag);
            logout(1);
            System.out.println("Exiting...");
            s.close();
        } catch (IOException | NotBoundException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Parse user input
     * @param input user input
     * @throws IOException I/O error
     * @throws NotBoundException registry lookup error
     */
    private static void parser(String input) throws IOException, NotBoundException {
        String[] splitted = input.split(" ");
        if(splitted.length == 0) return;

        switch (splitted[0]) {
            case "login":
                if (ONLINE == 0) {
                    if (splitted.length != 3) {
                        System.out.println("Invalid input. (login user pass)");
                    } else login(splitted[1], splitted[2]);
                } else
                    System.out.println("You are already logged in.");
                break;
            case "register":
                if (ONLINE == 0) {
                    if (splitted.length != 3) {
                        System.out.println("Invalid input. (register user pass)");
                    } else register(splitted[1], splitted[2]);
                } else
                    System.out.println("You are logged in, log out before registering a new user.");
                break;
            case "logout":
                if (ONLINE == 1) {
                    logout(0);
                } else
                    System.out.println("Cannot log out, you are already offline.");
                break;
            case "list_users":
                if (ONLINE == 1) stub.getLocalUserList(0);
                else System.out.println(NOT_LOGGED);
                break;
            case "list_online_users":
                if (ONLINE == 1) stub.getLocalUserList(1);
                else System.out.println(NOT_LOGGED);
                break;
            case "list_projects":
                if (ONLINE == 1) listProjects();
                else System.out.println(NOT_LOGGED);
                break;
            case "create_project":
                if (ONLINE == 1) {
                    if (splitted.length != 2)
                        System.out.println("Invalid input. (create_project project_name)");
                    else createProject(splitted[1]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "add_member":
                if (ONLINE == 1) {
                    if (splitted.length != 3)
                        System.out.println("Invalid input. (add_member project_name username)");
                    else addMember(splitted[1], splitted[2]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "show_members":
                if (ONLINE == 1) {
                    if (splitted.length != 2) System.out.println("Invalid input. (show_members project_name)");
                    else showMembers(splitted[1]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "show_cards":
                if (ONLINE == 1) {
                    if (splitted.length != 2) System.out.println("Invalid input. (show_cards project_name)");
                    else showCards(splitted[1]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "show_card":
                if (ONLINE == 1) {
                    if (splitted.length != 3)
                        System.out.println("Invalid input. (show_card project_name card_name)");
                    else showCard(splitted[1], splitted[2]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "add_card":
                if (ONLINE == 1) {
                    if (splitted.length < 4)
                        System.out.println("Invalid input. (add_card project_name card_name description)");
                    else {
                        splitted = input.split(" ", 4);
                        addCard(splitted[1], splitted[2], splitted[3]);
                    }
                } else System.out.println(NOT_LOGGED);
                break;
            case "move_card":
                if (ONLINE == 1) {
                    if (splitted.length != 5)
                        System.out.println("Invalid input. (move_card project_name card_name source_list dest_list)");
                    else moveCard(splitted[1], splitted[2], splitted[3], splitted[4]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "read_chat":
                if (ONLINE == 1) {
                    if (splitted.length != 2)
                        System.out.println("Invalid input. (read_chat project_name)");
                    else readChat(splitted[1]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "send_chat_msg":
                if (ONLINE == 1) {
                    if (splitted.length < 3)
                        System.out.println("Invalid input. (send_chat project_name message)");
                    else {
                        splitted = input.split(" ", 3);
                        sendChatMsg(splitted[1], splitted[2]);
                    }
                } else System.out.println(NOT_LOGGED);
                break;
            case "get_card_history":
                if (ONLINE == 1) {
                    if (splitted.length != 3)
                        System.out.println("Invalid input. (get_card_history project_name card_name)");
                    else {
                        getCardHistory(splitted[1], splitted[2]);
                    }
                } else System.out.println(NOT_LOGGED);
                break;
            case "cancel_project":
                if (ONLINE == 1) {
                    if (splitted.length != 2)
                        System.out.println("Invalid input. (cancel_project project_name)");
                    else {
                        cancelProject(splitted[1]);
                    }
                } else System.out.println(NOT_LOGGED);
                break;
            case "help":
                help();
                break;
            case "exit":
                flag = false;
                break;
            default:
                System.out.println("Command does not exists, type \"help\" for the list of available commands.");
                break;
        }
    }

    /**
     * Send request to the server and obtain its response
     * @param msg  request message
     * @param size size of RX ByteBuffer
     * @return ByteBuffer storing server's response
     */
    private static ByteBuffer sendRequest(String msg, int size) {
        ByteBuffer request = ByteBuffer.wrap(msg.getBytes());
        ByteBuffer resp = ByteBuffer.allocate(size);

        while (request.hasRemaining()) {
            try {
                client.write(request);
            }
            catch (IOException e) {
                break;
            }
        }

        try {
            int read = client.read(resp);

            if (read == -1) {
                System.out.println("Read error. Server connection interrupted.");
                flag = false;
                return null;
            }
        } catch (IOException e) {
            System.out.println("Server connection closed.");
            flag = false;
        }

        return resp;
    }

    /**
     * Check for request to join projects or notifications about closed projects. If the
     * user looged in, it is connected to the projects' chats
     * @throws IOException I/O error on sendRequest or checkFirstTime
     */
    private static void checkNotifications() throws IOException {
        ByteBuffer resp = sendRequest("list_projects", BASE_SIZE);
        if (resp == null) return;

        String[] response = new String(resp.array()).trim().split("\n"); // response[0] contains server response
        ArrayList<String> aux = new ArrayList<>(Arrays.asList(response).subList(1, response.length));
        for (String key : projects.keySet()) {
            if (!aux.contains(key)) {
                projects.remove(key);
                System.out.println("The project " + key + " has been closed.");
            }
        }

        for (String check : aux) {
            /* User has been added to a new project, get multicast parameter */
            if (!projects.containsKey(check)) {
                resp = sendRequest("get_parameter " + check, BASE_SIZE);
                if (resp == null) return;

                String[] parameter = new String(resp.array()).split("\n");
                if (parameter[0].equals("200 OK"))
                    checkFirstTime(check, parameter[1]);
                else
                    System.out.println("Cannot obtain multicast parameters. The user has not " +
                        "been added to the project  " + check);
            }
        }
    }

    /**
     * Store project information in "projects" and create multicast socket
     * @param projName project name
     * @param addrPort address:port of the multicast socket
     * @throws IOException I/O error on MulticastSocket
     */
    private static void checkFirstTime(String projName, String addrPort) throws IOException {
        if (!projects.containsKey(projName)) {
            String[] infos = addrPort.split(":");
            String addr = infos[0];
            int port = Integer.parseInt(infos[1].trim());
            MulticastSocket localMS = new MulticastSocket(port);
            InetAddress group = InetAddress.getByName(addr);
            localMS.joinGroup(group);
            localMS.setSoTimeout(1000);

            MulticastInfos ms = new MulticastInfos(localMS, addr, port);
            projects.put(projName, ms);
            System.out.println("You have been added to project " + projName + ".");
        }
    }

    /**
     * Handle registration to callback and creation of ROC (client remote interface)
     * @throws RemoteException remote method error
     * @throws NotBoundException no registry linked to the name
     */
    private static void callbackImpl() throws RemoteException, NotBoundException {
        /* Retrieve ROS (server remote interface) */
        Registry registry = LocateRegistry.getRegistry(RMICallbackInterface.PORT);
        server = (RMICallbackInterface) registry.lookup(RMICallbackInterface.REMOTE_OBJECT_NAME);

        /* Register client to callback */
        System.out.println("Registering for callback");
        callbackObj = new ClientNotifyImpl();
        stub = (ClientNotifyInterface) UnicastRemoteObject.exportObject(callbackObj, 0);
        server.registerForCallback(stub);
        System.out.println(" ");
    }

    /**
     * Login the user through RMI
     * @param nickname username
     * @param pass password
     */
    private static void login(String nickname, String pass) {
        try {
            /* Open channel to the server */
            client = SocketChannel.open(new InetSocketAddress(hostS, portS));
        } catch (IOException e) {
            System.out.println("SocketChannel error. Check if the server is online");
            flag = false;
            return;
        } catch (UnresolvedAddressException e) {
            System.out.println("Server address error, check if the address is correct.");
            flag = false;
            return;
        }

        ByteBuffer resp = sendRequest("login " + nickname + " " + pass, BASE_SIZE);
        if (resp == null) return;

        String answer = new String(resp.array()).trim();
        System.out.println(answer);

        if (answer.equals("200 OK")) {
            ClientMain.username = nickname;
            /* Register to RMI Callback */
            try {
                callbackImpl();
            } catch (IOException | NotBoundException e) {
                System.out.println("Cannot register to callback.");
                flag = false;
                return;
            }
            ONLINE = 1;

            /* Retrieve multicast data and create multicast socket */
            try {
                checkNotifications();
            } catch (IOException e) {
                System.out.println("Cannot join multicast group.");
                flag = false;
            }
        }
    }

    /**
     * Register the user through RMI
     * @param nickname username
     * @param pass password
     */
    private static void register(String nickname, String pass) {
        RMIRegistrationInterface serverObject;
        String response;

        /* Retrieve server remote interface */
        try {
            Registry registry = LocateRegistry.getRegistry(RMIRegistrationInterface.PORT);
            serverObject = (RMIRegistrationInterface)
                    registry.lookup(RMIRegistrationInterface.REMOTE_OBJECT_NAME);
        } catch (NotBoundException e) {
            System.out.println("Cannot find binding to name: " +
                    RMIRegistrationInterface.REMOTE_OBJECT_NAME);
            return;
        }
        catch(IOException e) {
            System.out.println("Cannot register using RMI.");
            return;
        }

        /* User remote object to perform user registration */
        try {
            response = serverObject.register(nickname, pass);
            System.out.println(response);
        } catch (IOException e) {
            System.out.println("Binding is correct. Encountered errors during registration.");
        }
    }

    /**
     * List projects the user belongs to
     */
    private static void listProjects() {
        ByteBuffer resp = sendRequest("list_projects", BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);
    }

    /**
     * Create a new project
     * @param projName project name
     * @throws IOException I/O error on sendRequest
     */
    private static void createProject(String projName) throws IOException {
        ByteBuffer resp = sendRequest("create_project " + projName, BASE_SIZE);
        if (resp == null) return;

        String[] respArray = new String(resp.array()).split("\n");
        System.out.println(respArray[0]);
        /* Set up multicast socket, store address and port */
        if (respArray[0].equals("200 OK"))
            checkFirstTime(projName, respArray[1]);
    }

    /**
     * Add new member to project
     * @param projName project name
     * @param nickname username to add
     * @throws IOException I/O error on sendRequest
     */
    private static void addMember(String projName, String nickname) throws IOException {
        ByteBuffer resp = sendRequest("add_member " + projName + " " + nickname, BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);
        
        /* Send message on project chat */
        if (response.equals("200 OK")) sendSystemMsg(projName, username + " added user " +
                nickname + " to the project");
    }

    /**
     * Show project members
     * @param projName project name
     */
    private static void showMembers(String projName) {
        ByteBuffer resp = sendRequest("show_members " + projName, BASE_SIZE);
        if (resp == null) return;

        String[] response = new String(resp.array()).split("\n");
        if (!response[0].equals("200 OK")) System.out.println(response[0]);
        if (response.length > 1) {
            for (int i = 1; i < response.length; i++)
                System.out.println(response[i]);
        }
    }

    /**
     * Show cards belonging to the project
     * @param projName project name
     */
    private static void showCards(String projName) {
        ByteBuffer resp = sendRequest("show_cards " + projName, BASE_SIZE);
        if (resp == null) return;

        String[] response = new String(resp.array()).split("\n");
        if (!response[0].equals("200 OK")) System.out.println(response[0]);
        if (response.length > 1) {
            for (int i = 1; i < response.length; i++)
                System.out.println(response[i]);
        }
    }

    /**
     * Show a card belonging to the project
     * @param projName project name
     * @param cardName card name
     */
    private static void showCard(String projName, String cardName) {
        ByteBuffer resp = sendRequest("show_card " + projName + " " + cardName, MAX_SIZE);
        if (resp == null) return;

        String[] response = new String(resp.array()).split("\n");
        if (!response[0].equals("200 OK")) System.out.println(response[0]);
        if (response.length > 1) {
            for (int i = 1; i < response.length; i++)
                System.out.println(response[i]);
        }
    }

    /**
     * Retrieve the movements of the card
     * @param projName project name
     * @param cardName card name
     */
    private static void getCardHistory(String projName, String cardName) {
        ByteBuffer resp = sendRequest("get_card_history " + projName + " " + cardName, BASE_SIZE);
        if (resp == null) return;

        String[] response = new String(resp.array()).split("\n");
        if (!response[0].equals("200 OK")) System.out.println(response[0]);
        if (response.length > 1) {
            for (int i = 1; i < response.length; i++)
                System.out.println(response[i]);
        }
    }

    /**
     * Add a card to the project
     * @param projName project name
     * @param cardName card name
     * @param descr card description
     * @throws IOException I/O error on sendRequest
     */
    private static void addCard(String projName, String cardName, String descr) throws IOException {
        ByteBuffer resp = sendRequest("add_card " + projName + " " + cardName + " " + descr, BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);

        if (response.equals("200 OK")) sendSystemMsg(projName, username + " added card " +
                cardName);
    }

    /**
     * Move card from a source list (to_do, inprogress, toberevised) to a destination list
     * @param projName project name
     * @param cardName card name
     * @param sourceList source list
     * @param destList destination list
     * @throws IOException I/O error on sendRequest
     */
    private static void moveCard(String projName, String cardName, String sourceList, String destList)
            throws IOException {
        ByteBuffer resp = sendRequest("move_card " + projName + " " + cardName + " " + sourceList
                + " " + destList, BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);

        if (response.equals("200 OK")) sendSystemMsg(projName, username + " moved " +
                cardName + " from " + sourceList + " to " + destList);
    }

    /**
     * Read messages on project chat
     * @param projName project name
     * @throws IOException I/O error on sendRequest or receive
     */
    private static void readChat(String projName) throws IOException {
        /* Check if user belongs to project */
        ByteBuffer resp = sendRequest("is_member " + projName, BASE_SIZE);
        if (resp == null) return;

        String answer = new String(resp.array()).trim();
        int stopFlag = 0;

        if (answer.equals("200 OK")) {
            System.out.println("|New messages|");
            while (stopFlag == 0) {
                try {
                    byte[] buf = new byte[MAX_SIZE];
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    projects.get(projName).getMultiSocket().receive(dp);
                    String response = new String(dp.getData());
                    System.out.println(response);
                }
                catch (SocketTimeoutException e) {
                    stopFlag = 1;
                    System.out.println("|------------|");
                }
            }
        } else System.out.println(answer);
    }

    /**
     * Write message to project chat
     * @param projName project name
     * @param sendMsg  message to write
     * @throws IOException I/O error on sendRequest or send
     */
    private static void sendChatMsg(String projName, String sendMsg) throws IOException {
        ByteBuffer resp = sendRequest("is_member " + projName, BASE_SIZE);
        if (resp == null) return;

        String answer = new String(resp.array()).trim();
        String msg;

        if (answer.equals("200 OK")) {
            msg = ClientMain.username + ": " + sendMsg;
            byte[] buf = msg.getBytes();
            MulticastInfos aux = projects.get(projName);
            DatagramPacket dp = new DatagramPacket(buf, buf.length, InetAddress.getByName(aux.getAddr()),
                    aux.getPort());
            ds.send(dp);
            System.out.println("Message sent");
        } else System.out.println(answer);
    }

    /**
     * Remove the project (all the tasks should be completed)
     * @param projName project name
     */
    private static void cancelProject(String projName) {
        ByteBuffer resp = sendRequest("cancel_project " + projName, BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);
    }

    /**
     * Logout the user
     * 
     * @param exit 1 logout user and shutdown the client
     *             0 logout user
     * @throws IOException I/O error on sendRequest or unregisterForCallback
     */
    private static void logout(int exit) throws IOException {
        if(exit == 0 || (exit == 1 && ONLINE == 1)) {
            try {
                if (stub != null) {
                    server.unregisterForCallback(stub);
                    stub = null;
                }

                UnicastRemoteObject.unexportObject(callbackObj, true);
                ONLINE = 0;
                projects.clear();
                System.out.println("Logout was successful.");
            }
            /* Server connection is closed */
            catch (IOException e) {
                if (ONLINE == 1) {
                    ONLINE = 0;
                    UnicastRemoteObject.unexportObject(callbackObj, true);
                    stub = null;
                    System.out.println("Logout was successful even if the server is closed.");
                    return;
                }
            }

            try {
                if (exit == 0) {
                    ByteBuffer request = ByteBuffer.wrap("logout".getBytes());
                    client.write(request);
                    client.close();
                } else  {
                    ByteBuffer request = ByteBuffer.wrap("exit".getBytes());
                    client.write(request);
                    client.close();

                    ds.close();
                }
            } catch (IOException e) {
                System.out.println("Cannot communicate with the server.");
            }
        }
        /* If client should be closed and logout has already been performed */
        else if(exit == 1) ds.close();

    }

    /**
     * Send a system message to project chat, denoting a particular event (e.g. new card or new user)
     * @param projName project name
     * @param sendMsg message
     * @throws IOException I/O error
     */
    private static void sendSystemMsg(String projName, String sendMsg) throws IOException {
        String msg = "System: " + sendMsg;
        byte[] buf = msg.getBytes();
        MulticastInfos aux = projects.get(projName);
        DatagramPacket dp = new DatagramPacket(buf, buf.length, InetAddress.getByName(aux.getAddr()),
                aux.getPort());
        ds.send(dp);
    }

    private static void help() {
        System.out.println(" ");

        if(ONLINE == 0) {
            System.out.printf("%-20s %-40s\n\n", "login user pass", "perform user login");

            System.out.printf("%-20s %-40s\n\n", "register user pass", "perform user registration");

            System.out.printf("%-20s %-40s\n\n", "exit", "close the client");
        }
        else {

            System.out.printf("%-55s %-100s\n\n", "logout", "perform user logout");

            System.out.printf("%-55s %-100s\n\n", "list_users", "list registered users");

            System.out.printf("%-55s %-100s\n\n", "list_online_users", "list online users");

            System.out.printf("%-55s %-100s\n\n", "list_projects", "list projects the user belongs to");

            System.out.printf("%-55s %-100s\n\n", "create_project project_name", "create a project \"project_name\"");

            System.out.printf("%-55s %-100s\n\n", "add_member project_name nickname", "add user \"nickname\" to project \"project_name\"");

            System.out.printf("%-55s %-100s\n\n", "show_members project_name", "show members of project \"project_name\"");

            System.out.printf("%-55s %-100s\n\n", "show_cards project_name", "show cards of project \"project_name\"");

            System.out.printf("%-55s %-100s\n\n", "show_card project_name card_name", "show information about card \"card_name\" of project \"project_name\"");

            System.out.printf("%-55s %-100s\n\n", "add_card project_name card_name descr", "add card with description \"descr\" to project");

            System.out.printf("%-55s %-100s\n\n", "move_card project_name card_name source_list dest_list", "move card from \"source_list\" to \"dest_list\"");

            System.out.printf("%-55s %-100s\n\n", "read_chat project_name", "show message of project chat");

            System.out.printf("%-55s %-100s\n\n", "send_chat_msg project_name msg", "write a message to project chat");

            System.out.printf("%-55s %-100s\n\n", "get_card_history project_name card_name", "obtain movements of the card");

            System.out.printf("%-55s %-100s\n\n", "cancel_project project_name", "remove project");

            System.out.printf("%-55s %-100s\n\n", "exit", "close the client");
        }

    }
}
