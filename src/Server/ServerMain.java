package Server;

import Commons.RMICallbackImpl;
import Commons.RMICallbackInterface;
import Commons.RMIRegistrationImpl;
import Commons.RMIRegistrationInterface;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

// NIO Multiplexed Server
public class ServerMain {
    private static String MAIN_PATH; // directory used for persistency
    private static DBMS dbms; // DB for registrations
    private static final String ERR = "[ERROR]";
    private static int port; // exposed port
    private static Selector sel;
    private static ArrayList<Project> projects;
    private static HashMap<String, String> users;
    private static HashMap<String, String> credentials;
    private static RMICallbackImpl supportServer; // reference to the server remote interface
    private static String lastAddr = "228.0.0.0"; // last multicast address
    final static int lastPort = 2000;             // last multicast port


    public static void main(String[] args) {
        projects = new ArrayList<>();
        users = new HashMap<>();
        credentials = new HashMap<>();
        if (args.length == 0) {
            System.out.println("Usage: java ServerMain port");
            return;
        }
        try {
            port = Integer.parseInt(args[0]);
        } catch (RuntimeException e) {
            System.out.println("Invalid port. Error");
            return;
        }

        /* Restore data from disk */
        dbms = DBMS.getInstance();
        MAIN_PATH = dbms.getMainPath();
        String state = dbms.checkState();
        if(!state.equals("[CREATE]")) {
            if (state.equals(ERR) || restoreProjects().equals(ERR)){
                System.out.println(ERR + " Cannot restore server state.");
                return;
            }
        }

        System.out.println("----------------------------");

        if(setRMIRegistration().equals(ERR) || setRMIAndRegistry().equals(ERR)) {
            System.out.println(ERR + " Cannot set up RMI service.");
            return;
        }

        if(TCPConfiguration().equals(ERR)) {
            System.out.println(ERR + " Cannot configure TCP connection");
            return;
        }

        while (true) {
            try {
                if(sel.select() == 0) continue;
            } catch (IOException e) {
                System.out.println("[ERROR] Selector error");
                return;
            }

            /* Start handling channels */
            Set<SelectionKey> readyKeys = sel.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                // Remove key from "selected set", not from "ready set"
                iterator.remove();

                try {
                    /* Establish connection with client */
                    if (key.isAcceptable())
                        Acceptable(key);
                    /* Receive and perform client's requests */
                    else if (key.isReadable())
                        Readable(key);
                    else if (key.isWritable())
                        Writable(key);
                } catch (IOException e) {
                    key.cancel();

                    try {
                        AdvKey keyAttach = (AdvKey) key.attachment();
                        String nickname = keyAttach.nickname;
                        if (nickname != null) {
                            getUpdatedData();
                            users.replace(nickname, "online", "offline");
                            supportServer.update(users, null);
                        }
                        System.out.println("[CLOSED]: " + nickname +
                                " | " + ((SocketChannel) key.channel()).getRemoteAddress());
                        key.channel().close();
                    } catch (IOException cex) {
                        cex.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Accept client connection
     * @param key token representing the channel
     * @throws IOException I/O error
     */
    private static void Acceptable(SelectionKey key) throws IOException {
        ServerSocketChannel serv = (ServerSocketChannel) key.channel();
        SocketChannel client = serv.accept();

        System.out.println("[ACCEPTED CLIENT] " + client.getRemoteAddress());

        client.configureBlocking(false);
        SelectionKey key1 = client.register(sel, SelectionKey.OP_READ);
        key1.attach(new AdvKey());
    }

    /**
     * Receive and parse client request
     * @param key token representing the channel
     * @throws IOException I/O error
     */
    private static void Readable(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        AdvKey keyAttach = (AdvKey) key.attachment();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        int read = client.read(buffer); // bytes read

        if (read == -1) throw new IOException("Channel closed");

        keyAttach.request = new String(buffer.array()).trim();
        parser(key);
    }

    /**
     * Reply to the client
     * @param key token representing the channel
     * @throws IOException I/O error
     */
    private static void Writable(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        AdvKey keyAttach = (AdvKey) key.attachment();
        String response = keyAttach.response;
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());

        while (buffer.hasRemaining()) {
            client.write(buffer);
        }

        keyAttach.request = null;
        keyAttach.response = null;
        key.interestOps(SelectionKey.OP_READ);
    }

    /**
     * Parse user request
     * @param key token representing the channel
     * @throws IOException I/O error
     */
    private static void parser(SelectionKey key) throws IOException {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String[] commandAndArg = keyAttach.request.split(" ");
        
        for (int i = 0; i < commandAndArg.length; i++)
            commandAndArg[i] = commandAndArg[i].trim();

        switch (commandAndArg[0]) {
            case "login":
                login(commandAndArg[1], commandAndArg[2], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "list_projects":
                listProjects(key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "create_project":
                createProject(commandAndArg[1], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "add_member":
                addMember(commandAndArg[1], commandAndArg[2], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "show_members":
                showMembers(commandAndArg[1], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "show_cards":
                showCards(commandAndArg[1], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "show_card":
                showCard(commandAndArg[1], commandAndArg[2], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "add_card":
                String[] descr = keyAttach.request.split(" ", 4);
                addCard(descr[1].trim(), descr[2].trim(), descr[3], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "move_card":
                moveCard(commandAndArg[1], commandAndArg[2], commandAndArg[3],
                        commandAndArg[4], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "get_card_history":
                getCardHistory(commandAndArg[1], commandAndArg[2], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "cancel_project":
                cancelProject(commandAndArg[1], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "is_member":
                isMemberClient(commandAndArg[1], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "get_parameter":
                getParameter(commandAndArg[1], key);
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "logout":
                logout(key);
                break;
            case "exit":
                throw new IOException("Channel closed");
        }
    }

    /**
     * Set up RMI system to enable user registration
     * @return "[OK]" if the operation is successful,
     *         "[ERR]" otherwise
     */
    private static String setRMIRegistration() {
        try {
            RMIRegistrationImpl registrationServer = RMIRegistrationImpl.getServerRMI();
            RMIRegistrationInterface stub = (RMIRegistrationInterface)
                    UnicastRemoteObject.exportObject(registrationServer, 0); // local reference

            Registry registry = LocateRegistry.createRegistry(RMIRegistrationInterface.PORT);
            registry.bind(RMIRegistrationInterface.REMOTE_OBJECT_NAME, stub);
        } catch (IOException | AlreadyBoundException e){
            return ERR;
        }

        return "[OK]";
    }

    /**
     * Set up callback system for RMI 
     * @return "[OK]" if the operation is successful,
     *         "[ERR]" otherwise
     */
    private static String setRMIAndRegistry() {
        try {
            supportServer = new RMICallbackImpl();
            RMICallbackInterface stub = (RMICallbackInterface)
                    UnicastRemoteObject.exportObject(supportServer, 0);

            Registry registry = LocateRegistry.createRegistry(RMICallbackInterface.PORT);
            registry.bind(RMICallbackInterface.REMOTE_OBJECT_NAME, stub);

            /* Give reference to DBMS */
            DBMS.setLocal_ref(supportServer);
        } catch (AlreadyBoundException | IOException e) {
            return ERR;
        }

        return "[OK]";
    }

    /**
     * Configure TCP connection
     * @return "[OK]" if the operation is successful,
     *         "[ERR]" otherwise
     */
    private static String TCPConfiguration() {
        try {
            ServerSocketChannel servSockChan = ServerSocketChannel.open();
            servSockChan.socket().bind(new InetSocketAddress("localhost", port));
            servSockChan.configureBlocking(false);
            sel = Selector.open();
            servSockChan.register(sel, SelectionKey.OP_ACCEPT);
            System.out.println("[WAITING] Connections on port " + port);
        } catch(IOException e) {
            return ERR;
        }

        return "[OK]";
    }

    /**
     * Update the list and state of the registered users
     */
    private static void getUpdatedData() {
        credentials = dbms.getCredentials();
        for(String user : credentials.keySet()) {
            if(!users.containsKey(user))
                users.put(user, "offline");
        }
    }

    /**
     * Send to the client the address and the port for the multicast socket
     * @param projName project name
     * @param key token representing the channel
     */
    private static void getParameter(String projName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String answer = isMember(projName, key);
        Project aux = isProject(projName);

        if (answer.equals("200 OK"))
            answer += "\n" + aux.getMulticastParameter();

        keyAttach.response = answer;
    }

    /**
     * Check if the project already exists
     * @param projName project name
     * @return reference to project if exists, null otherwise
     */
    private static Project isProject(String projName) {
        Project aux = null;

        for (Project p : projects) {
            if (p.getProjectName().equals(projName)) {
                aux = p;
                break;
            }
        }
        return aux;
    }

    /**
     * Generate a new address for multicast connections
     * @param lastAddress last address (in use)
     * @return new address
     */
    private static String generateAddress(String lastAddress) {
        String auxAddr;
        String[] parts = lastAddress.split("\\.");

        /* Incrementing the last address by one */
        for (int i = 3; i >= 0; i--) {
            if (!parts[i].equals("255")) {
                parts[i] = String.valueOf(Integer.parseInt(parts[i]) + 1);
                break;
            } else {
                parts[i] = "0";
            }
        }

        /* Check if all the available addresses have been used */
        if (parts[0].equals("239")) {
            return "NULL";
        }

        auxAddr = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        lastAddr = auxAddr;
        return auxAddr;
    }

    /**
     * Check if user belongs to project
     * @param projName project name
     * @param key token representing the channel
     * @return "200 OK" if successful, error message otherwise
     */
    private static String isMember(String projName, SelectionKey key) {
        String answer;
        AdvKey keyAttach = (AdvKey) key.attachment();
        Project aux = isProject(projName);

        if (aux == null) answer = "Project name not found";
        else {
            answer = "You are not a member of the project";
            for (String m : aux.getMembers()) {
                if (m.equals(keyAttach.nickname)) {
                    answer = "200 OK";
                    break;
                }
            }
        }

        return answer;
    }

    /**
     * Check if user belongs to project and send the outcome to the client
     * @param projName project name
     * @param key token representing the channel
     */
    private static void isMemberClient(String projName, SelectionKey key) {
        String answer = isMember(projName, key);
        AdvKey keyAttach = (AdvKey) key.attachment();
        keyAttach.response = answer;
    }

    /**
     * List the project which the user belongs to
     * @param key token representing the channel
     */
    private static void listProjects(SelectionKey key) {
        StringBuilder answer = new StringBuilder();
        AdvKey keyAttach = (AdvKey) key.attachment();

        answer.append("| Project Name |");
        for (Project p : projects) {
            for (String member : p.getMembers()) {
                if (member.equals(keyAttach.nickname)) {
                    answer.append("\n").append(p.getProjectName());
                    break;
                }
            }
        }

        keyAttach.response = answer.toString();
    }

    /**
     * Create a new project
     * @param projName project name
     * @param key token representing the channel
     */
    private static void createProject(String projName, SelectionKey key) {
        String answer;
        Project n;
        Project aux = isProject(projName);
        AdvKey keyAttach = (AdvKey) key.attachment();

        if (aux != null) answer = "Project name is already in use";
        else {
            String newAddr = generateAddress(lastAddr);
            if (newAddr.equals("NULL")) {
                answer = "No multicast address available";
            } else {
                //lastPort = lastPort + 1;
                n = new Project(projName, keyAttach.nickname, newAddr, String.valueOf(lastPort));
                projects.add(n);
                answer = "200 OK\n";
                answer += (newAddr + ":" + lastPort);
                try {
                    saveProject(n);
                } catch (IOException e) {
                    System.out.println("[ERROR] Cannot save project to disk");
                }
            }
        }

        keyAttach.response = answer;
    }

    /**
     * Add a new member to the project
     * @param projName project name
     * @param nickname username
     * @param key token representing the channel
     */
    private static void addMember(String projName, String nickname, SelectionKey key) {
        String answer = isMember(projName, key);
        Project aux = isProject(projName);
        AdvKey keyAttach = (AdvKey) key.attachment();
        int flag = 0;

        if (answer.equals("200 OK")) {
            getUpdatedData();
            if (!users.containsKey(nickname)) answer = "Username does not exists";
            else {
                for (String member : aux.getMembers()) {
                    if (member.equals(nickname)) {
                        flag = 1;
                        break;
                    }
                }

                if (flag == 1)
                    answer = "User already belongs to the project";
                else {
                    answer = aux.addMember(nickname);
                    updateProjState(aux, MAIN_PATH + "/" + projName, "members", null);
                }
            }
        }

        keyAttach.response = answer;
    }

    /**
     * Send the members of a project to the client
     * @param projName project name
     * @param key token representing the channel
     */
    private static void showMembers(String projName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder();
        answer.append(isMember(projName, key));
        Project aux = isProject(projName);

        if (answer.toString().equals("200 OK")) {
            ArrayList<String> auxMemb = aux.getMembers();

            answer.append("\n").append("| Nickname |");
            for (String member : auxMemb) {
                answer.append("\n").append(member);
            }
        }

        keyAttach.response = answer.toString();
    }

    /**
     * Send the card belonging to a project to the client
     * @param projName project name
     * @param key token representing the channel
     */
    private static void showCards(String projName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder();
        answer.append(isMember(projName, key));
        Project aux = isProject(projName);

        if (answer.toString().equals("200 OK")) {
            answer.append("\n").append(String.format("%-30s %-20s", "Card name", "|State"));
            for (Card c : aux.listAllCards()) {
                answer.append("\n").append(String.format("%-30s %-20s", c.getName(), "|" + c.getList()));
            }
        }

        keyAttach.response = answer.toString();
    }

    /**
     * Send a card information to the client
     * @param projName project name
     * @param cardName card name
     * @param key token representing the channel
     */
    private static void showCard(String projName, String cardName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder();
        answer.append(isMember(projName, key));
        Project aux = isProject(projName);

        if (answer.toString().equals("200 OK")) {
            Card auxC = aux.getCard(cardName);

            if (auxC == null) {
                answer = new StringBuilder("Card does not belong to the project");
            } else {
                answer.append("\n");
                answer.append("Name: ").append(auxC.getName()).append("\n");
                answer.append("Description: ").append(auxC.getDescription()).append("\n");
                answer.append("List: ").append(auxC.getList());
            }
        }

        keyAttach.response = answer.toString();
    }

    /**
     * Add the card to a project (the card will end up in the todo list)
     * @param projName project name
     * @param cardName card name
     * @param descr card description
     * @param key token representing the channel
     */
    private static void addCard(String projName, String cardName, String descr, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String answer = isMember(projName, key);
        Project aux = isProject(projName);
        Card c;

        if (answer.equals("200 OK")) {
            if ((c = aux.addCard(cardName, descr, 0)) == null) {
                answer = "Card name has already been used";
            } else {
                try {
                    /* Crea un file relativo alla card */
                    saveCard(aux, c, MAIN_PATH+"/"+projName);
                } catch (IOException e) {
                    System.out.println("[ERROR] Cannot save card to disk");
                }
            }
        }

        keyAttach.response = answer;
    }

    /**
     * Move the card from a source list to a destination list
     * @param projName project name
     * @param cardName card name
     * @param sourceList source list
     * @param destList destination list
     * @param key token representing the channel
     */
    private static void moveCard(String projName, String cardName, String sourceList, String destList,
                                 SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String answer = isMember(projName, key);
        Project aux = isProject(projName);

        if (answer.equals("200 OK")) {
            answer = aux.moveCard(cardName, sourceList, destList);
            /* Update the card's file */
            updateCard(aux, aux.getCard(cardName), MAIN_PATH+"/"+projName, sourceList, destList);
        }

        keyAttach.response = answer;
    }

    /**
     * Obtain the movement history of the card
     * @param projName project name
     * @param cardName card name
     * @param key token representing the channel
     */
    private static void getCardHistory(String projName, String cardName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder();
        answer.append(isMember(projName, key));
        Project aux = isProject(projName);

        if (answer.toString().equals("200 OK")) {
            Card auxC = aux.getCard(cardName);

            if (auxC == null)
                answer = new StringBuilder("Card does not belong to the project");
            else {
                for (String l : auxC.getMovements())
                    answer.append("\n").append(l);
            }
        }

        keyAttach.response = answer.toString();
    }

    /**
     * Remove a project
     * @param projName project name
     * @param key token representing the channel
     */
    private static void cancelProject(String projName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String answer = isMember(projName, key);
        Project aux = isProject(projName);

        if (answer.equals("200 OK")) {
            if (aux.isDone() == 1) {
                projects.remove(aux);
                clearState(MAIN_PATH + "/" + projName);
            } else
                answer = "Some cards should be completed, project cannot be removed";
        }

        keyAttach.response = answer;
    }

    /**
     * Logout the user
     * @param key token representing the channel
     * @throws IOException I/O error
     */
    private static void logout(SelectionKey key) throws IOException {
        AdvKey keyAttach = ((AdvKey) key.attachment());

        getUpdatedData();
        users.replace(keyAttach.nickname, "online", "offline");
        supportServer.update(users, null);

        keyAttach.response = null;
        keyAttach.request = null;
        keyAttach.nickname = null;
    }

    /**
     * Login the user
     * @param nickname username
     * @param password password
     * @param key token representing the channel
     * @throws IOException I/O error
     */
    public static void login(String nickname, String password, SelectionKey key) throws IOException {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String response;

        getUpdatedData();
        if (credentials.containsKey(nickname)) {
            if (credentials.get(nickname).equals(password)) {
                if (users.get(nickname).equals("online"))
                    response = "User is already online";
                else {
                    if(!users.replace(nickname, "offline", "online"))
                        response = "User can not be moved online";
                    else {
                        keyAttach.nickname = nickname;
                        response = "200 OK";
                        supportServer.update(users, null);
                    }
                }
            } else
                response = "Wrong password";
        } else
            response = "Username does not exists";


        keyAttach.response = response;
    }

    /**
     * Restore projects
     * @return "[OK]" if the operation was successful,
     *         "[ERROR]" otherwise
     */
    private static String restoreProjects() {
        File basedir = new File(MAIN_PATH);
        // if(basedir.exists())
        String[] filesAndDir = basedir.list();

        if (filesAndDir != null) {
            for (String dir : filesAndDir) {
                if (dir.equals("registrations.json"))
                    continue;

                String newPath = MAIN_PATH + "/" + dir;
                File aux = new File(newPath);
                if (aux.isDirectory()) {
                    String[] projectInfo = aux.list();
                    String filePath;

                    if (projectInfo != null) {
                        Project p = new Project(dir);
                        for (String name : projectInfo) {
                            filePath = newPath + "/" + name;
                            if (name.equals("infos.json")) {
                                if (restoreInfos(p, filePath).equals(ERR))
                                    return ERR;
                            }
                            else {
                                if (restoreCard(p, filePath).equals(ERR))
                                    return ERR;
                            }
                        }

                        projects.add(p);
                    }
                }
            }

            return "[OK]";
        }
        else {
            return ERR;
        }
    }

    /**
     * Restore project information such as multicast data and members list
     * @param p reference to the project
     * @param filePath relative path to "infos.json"
     * @return "[OK]" if the operation was successful,
     *         "[ERROR]" otherwise
     */
    @SuppressWarnings("unchecked")
    private static String restoreInfos(Project p, String filePath) {
        try {
            Object obj = new JSONParser().parse(new FileReader(filePath));
            JSONObject jo = (JSONObject) obj;

            p.setMulticastAddr((String) jo.get("multicastAddress"));
            p.setMulticastPort((String) jo.get("multicastPort"));
            p.setMembers((ArrayList<String>) jo.get("members"));

            p.setTodo((ArrayList<String>) jo.get("todo"));
            p.setInprogress((ArrayList<String>) jo.get("inprogress"));
            p.setToberevised((ArrayList<String>) jo.get("toberevised"));
            p.setDone((ArrayList<String>) jo.get("done"));

        } catch (ParseException | IOException e) {
            return ERR;
        }

        return "[OK]";
    }

    /**
     * Restore card and its information from disk
     * @param p reference to the project
     * @param filePath relative path to "nomecard.json"
     * @return "[OK]" if the operation was successful,
     *         "[ERROR]" otherwise
     */
    @SuppressWarnings("unchecked")
    private static String restoreCard(Project p, String filePath) {
        try {
            Object obj = new JSONParser().parse(new FileReader(filePath));
            JSONObject jo = (JSONObject) obj;

            String cardName = (String) jo.get("cardName");
            String descr = (String) jo.get("description");
            
            Card aux = p.addCard(cardName, descr, 1);
            aux.setList((String) jo.get("list"));
            aux.setMovements((ArrayList<String>) jo.get("movements"));

        } catch (ParseException | IOException e) {
            return ERR;
        }

        return "[OK]";
    }

    /**
     * Save project to disk
     * @param p reference to the project
     * @throws IOException I/O error
     */
    private static void saveProject(Project p) throws IOException {
        String newPath = MAIN_PATH + "/" + p.getProjectName();
        File aux_proj = new File(newPath);
        if (aux_proj.mkdir())
            System.out.println("[CREATE] Directory " + p.getProjectName() + " created.");

        saveProjectInfo(p, newPath);
    }

    /**
     * Save project information to disk
     * @param proj     reference to the project
     * @param projPath root path where data will be saved
     * @throws IOException I/O error
     */
    @SuppressWarnings("unchecked")
    private static void saveProjectInfo(Project proj, String projPath) throws IOException {
        File infos = new File(projPath + "/infos.json");
        if (infos.createNewFile())
            System.out.println("[CREATE] File \"infos.json\" created.");

        FileWriter fileW = new FileWriter(infos);
        JSONObject jsonO = new JSONObject();

        jsonO.put("projectName", proj.getProjectName());

        JSONArray members = new JSONArray();
        members.addAll(proj.getMembers());
        jsonO.put("members", members);

        String[] multiParameters = proj.getMulticastParameter().split(":");
        jsonO.put("multicastAddress", multiParameters[0]);
        jsonO.put("multicastPort", multiParameters[1]);

        JSONArray todo = new JSONArray();
        todo.addAll(proj.getTodo());
        jsonO.put("todo", todo);

        JSONArray inprogress = new JSONArray();
        inprogress.addAll(proj.getInprogress());
        jsonO.put("inprogress", inprogress);

        JSONArray toberevised = new JSONArray();
        toberevised.addAll(proj.getToberevised());
        jsonO.put("toberevised", toberevised);

        JSONArray done = new JSONArray();
        done.addAll(proj.getDone());
        jsonO.put("done", done);

        fileW.write(jsonO.toJSONString());
        fileW.close();
    }

    /**
     * Save card to disk
     * @param p reference to the project
     * @param c reference to the card
     * @param projPath path to project directory
     * @throws IOException I/O error
     */
    @SuppressWarnings("unchecked")
    private static void saveCard(Project p, Card c, String projPath) throws IOException {
        File aux = new File(projPath + "/" + c.getName() + ".json");
        if (aux.createNewFile())
            System.out.println("[CREATE] File \"" + c.getName() + ".json\" created.");

        FileWriter fileW = new FileWriter(aux);
        JSONObject jsonO = new JSONObject();

        jsonO.put("cardName", c.getName());
        jsonO.put("description", c.getDescription().trim());
        jsonO.put("list", c.getList());

        JSONArray movements = new JSONArray();
        movements.addAll(c.getMovements());
        jsonO.put("movements", movements);

        fileW.write(jsonO.toJSONString());
        fileW.close();

        updateProjState(p, projPath, "todo", null);
    }

    /**
     * Update data on disk
     * @param proj reference to the project
     * @param projPath path to project directory
     * @param choice1 first list to update
     * @param choice2 second list to update
     */
    @SuppressWarnings("unchecked")
    private static void updateProjState(Project proj, String projPath, String choice1, String choice2) {
        JSONObject joOut = null;
        try {
            Object obj = new JSONParser().parse(new FileReader(projPath + "/infos.json"));
            JSONObject joIn = (JSONObject) obj;
            joOut = new JSONObject();

            /* Save information in "infos.json" to a JSONObject */
            joOut.put("projectName", joIn.get("projectName"));
            joOut.put("multicastAddress", joIn.get("multicastAddress"));
            joOut.put("multicastPort", joIn.get("multicastPort"));
            joOut.put("members", joIn.get("members"));
            joOut.put("todo", joIn.get("todo"));
            joOut.put("inprogress", joIn.get("inprogress"));
            joOut.put("toberevised", joIn.get("toberevised"));
            joOut.put("done", joIn.get("done"));

            switch (choice1) {
                case "members":
                    joOut.put("members", proj.getMembers());
                    break;
                case "todo":
                    joOut.put("todo", proj.getTodo());
                    break;
                case "inprogress":
                    joOut.put("inprogress", proj.getInprogress());
                    break;
                case "toberevised":
                    joOut.put("toberevised", proj.getToberevised());
                    break;
                case "done":
                    joOut.put("done", proj.getDone());
                    break;
            }

            if (choice2 != null) {
                switch (choice2) {
                    case "todo":
                        joOut.put("todo", proj.getTodo());
                        break;
                    case "inprogress":
                        joOut.put("inprogress", proj.getInprogress());
                        break;
                    case "toberevised":
                        joOut.put("toberevised", proj.getToberevised());
                        break;
                    case "done":
                        joOut.put("done", proj.getDone());
                        break;
                }
            }

        } catch (ParseException | IOException e) {
            System.out.println("Invalid filepath: " + projPath);
        }

        if (joOut == null) {
            System.out.println("[ERROR] Data on disk cannot be updated.");
            return;
        }

        try {
            File infos = new File(projPath + "/infos.json");
            if (infos.delete()) {
                if (infos.createNewFile()) {
                    System.out.println("[CREATE] File \"infos.json\" updated.");
                }
            } else {
                System.out.println("[ERROR] Data on disk cannot be updated.");
                return;
            }

            FileWriter fileW = new FileWriter(infos);
            fileW.write(joOut.toJSONString());
            fileW.close();
        } catch (IOException e) {
            System.out.println("[ERROR] I/O error while saving data on disk.");
        }
    }

    /**
     * Update data on disk about card
     * @param p reference to project
     * @param c reference to card
     * @param projPath path to project directory
     * @param source source list
     * @param dest destination list
     */
    @SuppressWarnings("unchecked")
    private static void updateCard(Project p, Card c, String projPath, String source, String dest) {
        JSONObject joOut = null;
        String path = null;

        try {
            path = projPath + "/" + c.getName() + ".json";
            Object obj = new JSONParser().parse(new FileReader(path));
            JSONObject joIn = (JSONObject) obj;
            joOut = new JSONObject();

            joOut.put("cardName", joIn.get("cardName"));
            joOut.put("description", joIn.get("description"));
            joOut.put("list", c.getList());

            JSONArray movements = new JSONArray();
            movements.addAll(c.getMovements());
            joOut.put("movements", movements);
        } catch (ParseException | IOException e) {
            System.out.println("Invalid filepath: " + path);
        }

        if (joOut == null) {
            System.out.println("[ERROR] Data on disk cannot be updated.");
            return;
        }

        try {
            File card = new File(path);
            if (card.delete()) {
                if (card.createNewFile()) {
                    System.out.println("[CREATE] File \"" + c.getName() + ".json\" updated.");
                }
            } else {
                System.out.println("[ERROR] Data on disk cannot be updated.");
                return;
            }

            FileWriter fileW = new FileWriter(card);
            fileW.write(joOut.toJSONString());
            fileW.close();
        } catch (IOException e) {
            System.out.println("[ERROR] I/O error while saving data on disk.");
        }

        updateProjState(p, projPath, source, dest);
    }

    /**
     * Remove files and subdirectories relative to root directory
     * @param masterPath path of root directory
     */
    private static void clearState(String masterPath) {
        File dir = new File(masterPath);

        if (dir.exists()) {
            String[] subFiles = dir.list();

            if (subFiles != null) {
                for (String subFile : subFiles) {
                    String newPath = masterPath + "/" + subFile;
                    File aux = new File(newPath);
                    if (aux.isDirectory())
                        clearState(newPath);
                    else if (aux.delete())
                        System.out.println("[DELETE] " + subFile + " deleted.");
                }
            }

            if (dir.delete())
                System.out.println("[DELETE] " + dir.getName() + " deleted.");
        }
    }
}
