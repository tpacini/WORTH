package Client;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import Server.ServerInterface;

public class ClientMain {
    private static String username;                          // username dell'utente
    private static SocketChannel client;                     // channel utilizzato per comunicare con il server
    private static int ONLINE = 0;                           // identifica se l'utente è loggato o meno
    private static ClientNotifyInterface stub;               // riferimento all'oggetto remoto (callbackObj)
    private static ServerInterface server;                   // interfaccia remota del server
    private static ClientNotifyInterface callbackObj;        // interfaccia del client che riceve le callback
    private static HashMap<String, MulticastInfos> projects; // associazione progetto-informazione per il multicast
    private static DatagramSocket ds;                        // utilizzata per inviare messaggi nella chat multicast
    private static final int BASE_SIZE = 256;                // dim. base del ByteBuffer che riceve msg dal server
    private static final int MAX_SIZE = 2048;                // dim. massima del ByteBuffer che riceve msg dal server

    final static String NOT_LOGGED = "Non sei loggato, impossibile svolgere l'operazione";
    private static boolean flag = true;

    public static void main(String[] args) {
        int port;
        projects = new HashMap<>();

        if (args.length == 0) {
            System.out.println("Usage: java ClientMain host [port]");
            return;
        }
        try {
            port = Integer.parseInt(args[1]);
        } catch (RuntimeException ex) {
            System.out.println("Invalid port. Error");
            return;
        }

        try {
            ds = new DatagramSocket(2000 + (new Random()).nextInt(8000));
        } catch (SocketException e) {
            e.printStackTrace();
        }

        try {
            /* Apre il "canale" con il server */
            client = SocketChannel.open(new InetSocketAddress(args[0], port));
        } catch (IOException e) {
            System.out.println("SocketChannel error. Controllare che il server sia online");
            return;
        }

        /* Ottiene l'input dell'utente e esegue la sua richiesta */
        try {
            Scanner s = new Scanner(System.in);
            String input;
            do {
                System.out.print("\n> ");
                input = s.nextLine();

                /* Controlla se un utente lo ha aggiunto al suo progetto */
                if (ONLINE == 1) checkNotifications();

                parser(input);
            } while (flag);
            logout(1);
            System.out.println("Uscendo...");
            s.close();
        } catch (IOException | NotBoundException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Esegue il parsing dell'input dell'utente e esegue dei controlli sulla validità della
     * richiesta
     *
     * @param input input dell'utente
     * @throws IOException       errore di I/O
     * @throws NotBoundException errore nella lookup del registry
     */
    private static void parser(String input) throws IOException, NotBoundException {
        String[] splitted = input.split(" ");
        if(splitted.length == 0) return;

        switch (splitted[0]) {
            case "login":
                if (ONLINE == 0) {
                    if (splitted.length != 3) {
                        System.out.println("Input non valido. (login user pass)");
                    } else login(splitted[1], splitted[2]);
                } else
                    System.out.println("Sei già loggato, impossibile fare la login");
                break;
            case "register":
                if (ONLINE == 0) {
                    if (splitted.length != 3) {
                        System.out.println("Invalid input. (register user pass)");
                    } else register(splitted[1], splitted[2]);
                } else
                    System.out.println("Sei loggato, esegui il logout per poter " +
                            "registrare un altro account.");
                break;
            case "logout":
                if (ONLINE == 1) {
                    logout(0);
                } else
                    System.out.println("Impossibile fare il logout, non sei online");
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
                        System.out.println("Invalid input. (create_project nome_progetto)");
                    else createProject(splitted[1]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "add_member":
                if (ONLINE == 1) {
                    if (splitted.length != 3)
                        System.out.println("Invalid input. (add_member nome_prog nickname)");
                    else addMember(splitted[1], splitted[2]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "show_members":
                if (ONLINE == 1) {
                    if (splitted.length != 2) System.out.println("Invalid input. (show_members nome_prog)");
                    else showMembers(splitted[1]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "show_cards":
                if (ONLINE == 1) {
                    if (splitted.length != 2) System.out.println("Invalid input. (show_cards nome_prog)");
                    else showCards(splitted[1]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "show_card":
                if (ONLINE == 1) {
                    if (splitted.length != 3)
                        System.out.println("Invalid input. (show_card nome_prog nome_card)");
                    else showCard(splitted[1], splitted[2]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "add_card":
                if (ONLINE == 1) {
                    if (splitted.length < 4)
                        System.out.println("Invalid input. (add_card nome_prog nome_card descr)");
                    else {
                        splitted = input.split(" ", 4);
                        addCard(splitted[1], splitted[2], splitted[3]);
                    }
                } else System.out.println(NOT_LOGGED);
                break;
            case "move_card":
                if (ONLINE == 1) {
                    if (splitted.length != 5)
                        System.out.println("Invalid input. (move_card nome_prog nome_card source_list dest_list)");
                    else moveCard(splitted[1], splitted[2], splitted[3], splitted[4]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "read_chat":
                if (ONLINE == 1) {
                    if (splitted.length != 2)
                        System.out.println("Invalid input. (read_chat nome_prog)");
                    else readChat(splitted[1]);
                } else System.out.println(NOT_LOGGED);
                break;
            case "send_chat_msg":
                if (ONLINE == 1) {
                    if (splitted.length < 3)
                        System.out.println("Invalid input. (send_chat nome_prog msg)");
                    else {
                        splitted = input.split(" ", 3);
                        sendChatMsg(splitted[1], splitted[2]);
                    }
                } else System.out.println(NOT_LOGGED);
                break;
            case "get_card_history":
                if (ONLINE == 1) {
                    if (splitted.length != 3)
                        System.out.println("Invalid input. (get_card_history nome_prog nome_card)");
                    else {
                        getCardHistory(splitted[1], splitted[2]);
                    }
                } else System.out.println(NOT_LOGGED);
                break;
            case "cancel_project":
                if (ONLINE == 1) {
                    if (splitted.length != 2)
                        System.out.println("Invalid input. (cancel_project nome_prog)");
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
                System.out.println("Comando non esistente, digita \"help\" per maggiori informazioni.");
                break;
        }
    }

    /**
     * Metodo ausiliario che gestisce l'invio di una richiesta al server, e la
     * ricezione della relativa risposta
     *
     * @param msg  messaggio di richiesta da inviare al server
     * @param size dimensione del ByteBuffer di ricezione
     * @return ByteBuffer contenente la risposta del server
     */
    private static ByteBuffer sendRequest(String msg, int size) {
        ByteBuffer request = ByteBuffer.wrap(msg.getBytes());
        ByteBuffer resp = ByteBuffer.allocate(size);

        /* Controlla se tutti i bytes siano stati scritti */
        while (request.hasRemaining()) {
            try {
                client.write(request);
            }
            /* La write ha riscontrato un errore*/ catch (IOException e) {
                System.out.println("Errore scrittura nel buffer della socket del server.");
                break;
            }
        }

        /* Controlla i bytes letti */
        try {
            int read = client.read(resp);

            /* Errore in lettura, esce dal programma perché nel caso di canale non bloccante,
             * -1 viene restituito solo in caso di errore (mentre 0 viene restituito nel caso
             * in cui si sia letto il buffer per intero */
            if (read == -1) {
                System.out.println("Errore lettura nel buffer della socket del server.");
                flag = false;
                return null;
            }
        } catch (IOException e) {
            System.out.println("Server chiuso.");
            flag = false;
        }

        return resp;
    }

    /**
     * Gestisce gli inviti di partecipazione ad un certo progetto, o le uscite nel
     * caso in cui il progetto sia stato cancellato. Se sei appena loggato ti ricollega
     * alla chat e aggiorna projects
     *
     * @throws IOException sendRequest o checkFirstTime hanno riscontrato errori di I/O
     */
    private static void checkNotifications() throws IOException {
        ByteBuffer resp = sendRequest("list_projects", BASE_SIZE);
        if (resp == null) return;

        String[] response = new String(resp.array()).trim().split("\n");
        /* Elimina response[0] perché si tratta della risposta del server */
        ArrayList<String> aux = new ArrayList<>(Arrays.asList(response).subList(1, response.length));

        /* Se ci sono elementi in più in "projects" rispetto ad "aux", allora dei
         * progetti sono stati chiusi */
        for (String key : projects.keySet()) {
            if (!aux.contains(key)) {
                projects.remove(key);
                System.out.println("*Il progetto " + key + " è stato chiuso*");
            }
        }

        for (String check : aux) {
            /* C'è un elemento in più in "aux" e significa che l'utente è stato aggiunto ad un progetto*/
            if (!projects.containsKey(check)) {
                /* Richiede al server i parametri per il multicast */
                resp = sendRequest("get_parameter " + check, BASE_SIZE);
                if (resp == null) return;

                String[] parameter = new String(resp.array()).split("\n");
                /* Aggiunge l'utente al "gruppo" e salva le informazioni per
                 * il multicast */
                if (parameter[0].equals("200 OK"))
                    checkFirstTime(check, parameter[1]);
                else
                    System.out.println("Impossibile ottenere i parametri. Non aggiunto al progetto " + check);
            }
        }
    }

    /**
     * Aggiunge le informazioni sul nuovo progetto all'interno dell'HashMap projects e
     * crea la socket di multicast per interagire con gli altri membri del progetto
     *
     * @param projName il nome del progetto a cui l'utente è stato aggiunto
     * @param addrPort address:port relativi alla chat multicast del progetto
     * @throws IOException errori di I/O durante le operazioni sulla MulticastSocket
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
            System.out.println("*Sei stato aggiunto al progetto " + projName + "*");
        }
    }

    /**
     * Metodo ausiliario che gestisce la registrazione al meccanismo di callback e
     * la creazione del ROC (interfaccia remota del server)
     *
     * @param regName nome associato al registry
     * @param regPort porta associata al registry
     * @throws RemoteException   errore nel remote method
     * @throws NotBoundException al nome non è associato nessun registro
     */
    private static void callbackImpl(String regName, int regPort) throws RemoteException, NotBoundException {
        /* Il client recupera la ROS (interfaccia remota del server) */
        Registry registry = LocateRegistry.getRegistry(regPort);
        server = (ServerInterface) registry.lookup(regName);

        /* Il client si registra alla callback */
        System.out.println("Registering for callback");
        callbackObj = new ClientNotifyImpl();
        stub = (ClientNotifyInterface) UnicastRemoteObject.exportObject(callbackObj, 0);
        server.registerForCallback(stub);
        System.out.println(" ");
    }

    /**
     * Effettua la login dell'utente
     *
     * @param nickname username dell'utente
     * @param pass     password dell'utente
     * @throws IOException       sendRequest ha riscontrato errori di I/O
     * @throws NotBoundException la lookup del registry è stata eseguita su un nome "unbound"
     */
    private static void login(String nickname, String pass) throws IOException, NotBoundException {
        ByteBuffer resp = sendRequest("login " + nickname + " " + pass, BASE_SIZE);
        if (resp == null) return;

        String[] answer = new String(resp.array()).trim().split("\n");
        System.out.println(answer[0]);

        if (answer[0].equals("200 OK")) {
            String[] info = answer[1].split(" ");
            String regName = info[0];
            int regPort = Integer.parseInt(info[1]);
            ClientMain.username = nickname;
            /* Si registra al sistema di notifica (RMI Callback) */
            callbackImpl(regName, regPort);
            ONLINE = 1;

            /* Recupera le informazioni di multicast, e crea una socket, dei
             * progetti a cui apparteneva */
            checkNotifications();
        }
    }

    /**
     * Effettua la registrazione dell'utente
     *
     * @param nickname username dell'utente
     * @param pass     password dell'utente
     */
    private static void register(String nickname, String pass) {
        ByteBuffer resp = sendRequest("register " + nickname + " " + pass, BASE_SIZE);
        if (resp == null) return;

        String[] answer = new String(resp.array()).trim().split("\n");
        System.out.println(answer[0]);
    }

    /**
     * Mostra la lista dei progetti a cui l'utente partecipa
     */
    private static void listProjects() {
        ByteBuffer resp = sendRequest("list_projects", BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);
    }

    /**
     * Crea un nuovo progetto
     *
     * @param projName nome del progetto creato
     * @throws IOException sendRequest ha riscontrato errori di I/O
     */
    private static void createProject(String projName) throws IOException {
        ByteBuffer resp = sendRequest("create_project " + projName, BASE_SIZE);
        if (resp == null) return;

        String[] respArray = new String(resp.array()).split("\n");
        System.out.println(respArray[0]);
        /* Imposta la socket di multicast e salva l'indirizzo e la porta */
        if (respArray[0].equals("200 OK"))
            checkFirstTime(projName, respArray[1]);
    }

    /**
     * Aggiunge un nuovo membro al progetto
     *
     * @param projName nome del progetto di cui l'utente fa parte
     * @param nickname nome dell'utente da aggiungere al progetto
     * @throws IOException sendRequest ha riscontrato errori di I/O
     */
    private static void addMember(String projName, String nickname) throws IOException {
        ByteBuffer resp = sendRequest("add_member " + projName + " " + nickname, BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);

        /* Invia un messaggio nella chat del progetto per notificare l'azione eseguita */
        if (response.equals("200 OK")) sendSystemMsg(projName, username + " ha aggiunto l'utente " +
                nickname + " al progetto");
    }

    /**
     * Mostra i membri del progetto
     *
     * @param projName nome del progetto
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
     * Mostra le card associate al progetto
     *
     * @param projName nome del progetto
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
     * Mostra una specifica card associata al progetto
     *
     * @param projName nome del progetto
     * @param cardName nome della card
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
     * Ottiene la "storia" (le liste che ha visitato la card) della card
     *
     * @param projName nome del progetto
     * @param cardName nome della card
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
     * Aggiunge una card al progetto
     *
     * @param projName nome del progetto
     * @param cardName nome della card
     * @param descr    descrizione relativa alla card
     * @throws IOException sendRequest ha riscontrato errori di I/O
     */
    private static void addCard(String projName, String cardName, String descr) throws IOException {
        ByteBuffer resp = sendRequest("add_card " + projName + " " + cardName + " " + descr, BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);

        /* Invia un messaggio nella chat del progetto per notificare l'azione eseguita */
        if (response.equals("200 OK")) sendSystemMsg(projName, username + " ha aggiunto la card " +
                cardName);
    }

    /**
     * Sposta una card da una certa lista sorgente (to_do, inprogress, toberevised) ad una di destinazione
     *
     * @param projName   nome del progetto
     * @param cardName   nome della card
     * @param sourceList lista di partenza
     * @param destList   lista di arrivo
     * @throws IOException sendRequest ha riscontrato errori di I/O
     */
    private static void moveCard(String projName, String cardName, String sourceList, String destList)
            throws IOException {
        ByteBuffer resp = sendRequest("move_card " + projName + " " + cardName + " " + sourceList
                + " " + destList, BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);

        /* Invia un messaggio nella chat del progetto per notificare l'azione eseguita */
        if (response.equals("200 OK")) sendSystemMsg(projName, username + " ha spostato " +
                cardName + " da " + sourceList + " a " + destList);
    }

    /**
     * Legge i messaggi della chat del progetto
     *
     * @param projName nome del progetto
     * @throws IOException sendRequest o receive hanno riscontrato errori di I/O
     */
    private static void readChat(String projName) throws IOException {
        /* Controlla se l'utente appartenga al progetto (in questo caso
         * il controllo deve farlo lato client perché non invia nessun'altra
         * richiesta al server */
        ByteBuffer resp = sendRequest("is_member " + projName, BASE_SIZE);
        if (resp == null) return;

        String answer = new String(resp.array()).trim();
        int stopFlag = 0;

        /* L'utente appartiene al progetto */
        if (answer.equals("200 OK")) {
            /* Esegue la lettura dei messaggi della chat */
            System.out.println("|Nuovi messaggi|");
            while (stopFlag == 0) {
                try {
                    byte[] buf = new byte[MAX_SIZE];
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    /* Utilizza la socket salvata in projects (e associata
                     * ad un nome) */
                    projects.get(projName).getMultiSocket().receive(dp);
                    String response = new String(dp.getData());
                    System.out.println(response);
                }
                /* Se il timeout è scaduto allora non ci sono più messaggi da
                 * leggere */ catch (SocketTimeoutException e) {
                    stopFlag = 1;
                    System.out.println("|Fine nuovi messaggi|");
                }
            }
        } else System.out.println(answer);
    }

    /**
     * Invia un messaggio sulla chat del progetto
     *
     * @param projName nome del progetto
     * @param sendMsg  messaggio da inviare
     * @throws IOException sendRequest o send hanno riscontrato errori di I/O
     */
    private static void sendChatMsg(String projName, String sendMsg) throws IOException {
        /* Controllo se l'utente appartenga al progetto */
        ByteBuffer resp = sendRequest("is_member " + projName, BASE_SIZE);
        if (resp == null) return;

        String answer = new String(resp.array()).trim();
        String msg;

        /* L'utente appartiene al progetto */
        if (answer.equals("200 OK")) {
            /* Invio un messaggio sulla chat */
            msg = ClientMain.username + ": " + sendMsg;
            byte[] buf = msg.getBytes();
            MulticastInfos aux = projects.get(projName);
            DatagramPacket dp = new DatagramPacket(buf, buf.length, InetAddress.getByName(aux.getAddr()),
                    aux.getPort());
            ds.send(dp);
            System.out.println("Messaggio inviato");
        } else System.out.println(answer);
    }

    /**
     * Cancella il progetto (solo se tutti i "task" sono conclusi)
     *
     * @param projName nome del progetto
     */
    private static void cancelProject(String projName) {
        ByteBuffer resp = sendRequest("cancel_project " + projName, BASE_SIZE);
        if (resp == null) return;

        String response = new String(resp.array()).trim();
        System.out.println(response);
        /* Non devo eliminare nessun riferimento locale poiché lo farà
         * checkNotifications */
    }

    /**
     * Esegue il logout
     * @param exit 1 se l'utente vuole disconnettersi e uscire dal programma
     *             0 se l'utente vuole solo disconnettersi
     * @throws IOException sendRequest o unregisterForCallback ha riscontrato errori di I/O
     */
    private static void logout(int exit) throws IOException {
        /* Se vuole fare la logout senza exit oppure se vuole fare
         * la logout e la exit  */
        if(exit == 0 || (exit == 1 && ONLINE == 1)) {
            try {
                /* Elimina la registrazione alle callback */
                if (stub != null) {
                    server.unregisterForCallback(stub);
                    stub = null;
                }

                UnicastRemoteObject.unexportObject(callbackObj, true);
                ONLINE = 0;
                projects.clear();
                System.out.println("Logout effettuato con successo.");
            }
            /* Nel caso in cui il server si sia disconnesso per un errore o per qualche anomalia,
             * allora la unregisterForCallback lancerà un'eccezione non riuscendo a contattare il server,
             * e in questo caso notifica l'utente e la fa uscire */
            catch (IOException e) {
                if (ONLINE == 1) {
                    ONLINE = 0;
                    UnicastRemoteObject.unexportObject(callbackObj, true);
                    stub = null;
                    System.out.println("Logout effettuato con successo, anche se il server è chiuso.");
                }
            }

            /* Se vuole fare solo la logout, invia il messaggio "logout" al server,
             * altrimenti invia "exit" */
            try {
                if (exit == 0) {
                    /* Notifica il server del logout */
                    ByteBuffer request = ByteBuffer.wrap("logout".getBytes());
                    client.write(request);
                } else  {
                    /* Avvisa il server della exit */
                    ByteBuffer request = ByteBuffer.wrap("exit".getBytes());
                    client.write(request);

                    ds.close();
                }
            } catch (IOException e) {
                System.out.println("Impossibile contattare il server. Server chiuso");
            }
        }
        /* Se vuole uscire ma ha già effettuato la logout */
        else if(exit == 1) ds.close();

    }

    /**
     * Invia un messaggio di sistema sulla chat, che notifica qualche evento svolto
     * dall'utente (aggiunta di una card, aggiunta di un utente ecc..)
     *
     * @param projName nome del progetto
     * @param sendMsg  messaggio di sistema
     * @throws IOException errore di I/O
     */
    private static void sendSystemMsg(String projName, String sendMsg) throws IOException {
        /* Invia un messaggio sulla chat */
        String msg = "System: " + sendMsg;
        byte[] buf = msg.getBytes();
        MulticastInfos aux = projects.get(projName);
        DatagramPacket dp = new DatagramPacket(buf, buf.length, InetAddress.getByName(aux.getAddr()), aux.getPort());
        ds.send(dp);
    }

    /* Stampa a schermo la lista dei comandi disponibili */
    private static void help() {
        System.out.println(" ");

        System.out.printf("%-55s %-100s\n\n", "login user pass", "esegui la login");

        System.out.printf("%-55s %-100s\n\n", "register user pass", "esegui la registrazione");

        System.out.printf("%-55s %-100s\n\n", "logout", "esegui la logout");

        System.out.printf("%-55s %-100s\n\n", "list_users", "visualizza la lista degli utenti registrati");

        System.out.printf("%-55s %-100s\n\n", "list_online_users", "visualizza la lista degli utenti online");

        System.out.printf("%-55s %-100s\n\n", "list_projects", "visualizza i progetti di cui sei membro");

        System.out.printf("%-55s %-100s\n\n", "create_project nome_prog", "crea un progetto con nome \"nome_prog\"");

        System.out.printf("%-55s %-100s\n\n", "add_member nome_prog nickname", "aggiungi il membro \"nickname\" al progetto \"nome_prog\"");

        System.out.printf("%-55s %-100s\n\n", "show_members nome_prog", "visualizza i membri del progetto \"nome_prog\"");

        System.out.printf("%-55s %-100s\n\n", "show_cards nome_prog", "visualizza le carte associate al progetto \"nome_prog\"");

        System.out.printf("%-55s %-100s\n\n", "show_card nome_prog nome_card", "visualizza le informazioni della card \"nome_card\" associata al progetto \"nome_prog\"");

        System.out.printf("%-55s %-100s\n\n", "add_card nome_prog nome_card descr", "aggiungi la card con descrizione \"descr\" al progetto");

        System.out.printf("%-55s %-100s\n\n", "move_card nome_prog nome_card source_list dest_list", "sposta la card dalla lista \"source_list\" alla lista \"dest_list\"");

        System.out.printf("%-55s %-100s\n\n", "read_chat nome_prog", "visualizza i messaggi della chat del progetto");

        System.out.printf("%-55s %-100s\n\n", "send_chat_msg nome_prog msg", "invia un messaggio nella chat del progetto");

        System.out.printf("%-55s %-100s\n\n", "get_card_history nome_prog nome_card", "ottieni la \"storia\" della card");

        System.out.printf("%-55s %-100s\n\n", "cancel_project nome_prog", "cancella il progetto");

        System.out.printf("%-55s %-100s\n\n", "exit", "uscire dal programma");

    }
}
