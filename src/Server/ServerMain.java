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
    private static String MAIN_PATH;                    // directory che contiene il vecchio stato del server
    private static DBMS dbms;                           // DB che gestisce le registrazioni
    private static final String ERR = "[ERROR]";        // messaggio di errore
    private static int port;                            // porta su cui avviene la connessione TCP con i client
    private static Selector sel;                        // selettore

    private static ArrayList<Project> projects;         // lista dei progetti
    private static HashMap<String, String> users;       // lista degli utenti con il loro stato
    private static HashMap<String, String> credentials; // lista delle credenziali degli utenti

    private static RMICallbackImpl supportServer;       // riferimento locale dell'interfaccia remota del server
    private static String lastAddr = "228.0.0.0";       // ultimo indirizzo utilizzato per il multicast
    final static int lastPort = 2000;                   // ultima porta utilizzata per il multicast


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

        /* Recupera le informazioni riguardanti registrazioni, progetti
         * e card che possono trovarsi sul disco */
        dbms = DBMS.getInstance();
        MAIN_PATH = dbms.getMainPath();
        String state = dbms.checkState();
        if(!state.equals("[CREATE]")) {
            if (state.equals(ERR) || restoreProjects().equals(ERR)){
                System.out.println(ERR + " Impossibile ripristinare lo stato del server.");
                return;
            }
        }

        System.out.println("----------------------------");

        /* Imposta l'interfaccia remota per poter permettere ai client di registrarsi
         * (tramite RMI) */
        if(setRMIRegistration().equals(ERR) || setRMIAndRegistry().equals(ERR)) {
            System.out.println(ERR + " Impossibile impostare servizio di RMI.");
            return;
        }

        /* Inizializza la connessione TCP e si prepara a ricevere nuove connessioni */
        if(TCPConfiguration().equals(ERR)) {
            System.out.println(ERR + " Impossibile creare connessione TCP");
            return;
        }

        while (true) {
            try {
                if(sel.select() == 0) continue;
            } catch (IOException e) {
                System.out.println("[ERROR] Errore selettore");
                return;
            }

            /* Inizia a gestire i "channel" che sono pronti per svolgere un'operazione */
            Set<SelectionKey> readyKeys = sel.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                /* Elimina la chiave dal "selected Set" e non dal "ready Set" */
                iterator.remove();

                try {
                    /* Stabilisce la connessione con il client */
                    if (key.isAcceptable())
                        Acceptable(key);
                    /* Riceve ed esegue i comandi ricevuti dal client */
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
     * Accetta la connessione con il client
     * @param key token che rappresenta il "channel" riferito ad un certo client
     * @throws IOException errore di I/O
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
     * Riceve la richiesta del client e esegue dei controlli in lettura
     * @param key token che rappresenta il "channel" riferito ad un certo client
     * @throws IOException errore di I/O
     */
    private static void Readable(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        AdvKey keyAttach = (AdvKey) key.attachment();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        int read = client.read(buffer); // numero di bytes letti

        if (read == -1) throw new IOException("Canale chiuso");

        keyAttach.request = new String(buffer.array()).trim();
        parser(key);
    }

    /**
     * Invia la risposta al client e resetta il campo request e response
     * di AdvKey
     * @param key token che rappresenta il "channel" riferito ad un certo client
     * @throws IOException errore di I/O
     */
    private static void Writable(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        AdvKey keyAttach = (AdvKey) key.attachment();
        String response = keyAttach.response; // risposta da inviare al client
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());

        while (buffer.hasRemaining()) {
            client.write(buffer);
        }

        keyAttach.request = null;
        keyAttach.response = null;
        key.interestOps(SelectionKey.OP_READ);
    }






    /**
     * Effettua il parsing della richiesta dell'utente e sceglie il metodo con
     * cui risolverla
     * @param key token che rappresenta il "channel" riferito ad un certo client
     * @throws IOException errore di I/O
     */
    private static void parser(SelectionKey key) throws IOException {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String[] commandAndArg = keyAttach.request.split(" ");
        /* Cleaning the input */
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
                /* Utilizzo questo meccanismo poiché la descrizione è
                 * composta da vari spazi vuoti (" ") */
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
                throw new IOException("Canale chiuso");
        }
    }

    /**
     * Imposta il sistema RMI per far registrare gli utenti
     * @return "[OK]" se l'operazione avviene con successo,
     * "[ERR]" altrimenti
     */
    private static String setRMIRegistration() {
        try {
            RMIRegistrationImpl registrationServer = RMIRegistrationImpl.getServerRMI();
            /* Ottiene il riferimento locale */
            RMIRegistrationInterface stub = (RMIRegistrationInterface)
                    UnicastRemoteObject.exportObject(registrationServer, 0);

            /* Creazione registry */
            Registry registry = LocateRegistry.createRegistry(RMIRegistrationInterface.PORT);
            registry.bind(RMIRegistrationInterface.REMOTE_OBJECT_NAME, stub);
        } catch (IOException | AlreadyBoundException e){
            return ERR;
        }

        return "[OK]";
    }

    /**
     * Imposta il sistema RMI per svolgere le callback
     * @return "[OK]" se l'operazione avviene con successo,
     * "[ERR]" altrimenti
     */
    private static String setRMIAndRegistry() {
        try {
            supportServer = new RMICallbackImpl();
            /* Imposta il sistema di notifica RMI e crea il registry */
            RMICallbackInterface stub = (RMICallbackInterface)
                    UnicastRemoteObject.exportObject(supportServer, 0);

            /* Crea il registry associato ad un determinato nome e ad una determinata porta */
            Registry registry = LocateRegistry.createRegistry(RMICallbackInterface.PORT);
            registry.bind(RMICallbackInterface.REMOTE_OBJECT_NAME, stub);

            /* Passa il riferimento al dbms */
            DBMS.setLocal_ref(supportServer);
        } catch (AlreadyBoundException | IOException e) {
            return ERR;
        }

        return "[OK]";
    }

    /**
     * Imposta una connessione tcp su una determinata port
     * @return "[OK]" se l'operazione avviene con successo,
     * "[ERR]" altrimenti
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
     * Ottiene la lista degli utenti registrati dal DBMS, e aggiorna la lista
     * degli utenti locali, con il loro relativo stato
     */
    private static void getUpdatedData() {
        credentials = dbms.getCredentials();

        /* Se si sono registrati nuovi utenti, li aggiunge
         * alla lista degli utenti, con stato "offline" */
        for(String user : credentials.keySet()) {
            if(!users.containsKey(user))
                users.put(user, "offline");
        }
    }

    /**
     * Invia al client l'indirizzo e la porta utilizzati per il multicast di quel determinato
     * progetto
     * @param projName nome del progetto
     * @param key token relativo al client
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
     * Metodo ausiliario che controlla se il progetto fornito esiste all'interno
     * della lista dei progetti
     * @param projName nome del progetto
     * @return riferimento al progetto se esiste, null altrimenti
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
     * Genera un nuovo indirizzo per il multicast di un progetto
     * @param lastAddress ultimo indirizzo utilizzato (già in uso)
     * @return un nuovo indirizzo
     */
    private static String generateAddress(String lastAddress) {
        String auxAddr;
        String[] parts = lastAddress.split("\\.");

        /* "Incremento l'indirizzo attuale di 1" */
        for (int i = 3; i >= 0; i--) {
            if (!parts[i].equals("255")) {
                parts[i] = String.valueOf(Integer.parseInt(parts[i]) + 1);
                break;
            } else {
                /* Se ho un 255 in una delle 3 parti di indirizzo (senza considerare la prima parte),
                 * allora l'azzero e incremento di uno il blocco subito precedente (con il prossimo ciclo) */
                parts[i] = "0";
            }
        }

        /* Controllo se ho raggiunto il limite degli indirizzi di multicast disponibili,
         * caso che difficilmente si può raggiungere con un programma di queste dimensioni.*/
        if (parts[0].equals("239")) {
            return "NULL";
        }
        /* Riassemblo il nuovo indirizzo */
        auxAddr = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        lastAddr = auxAddr;
        return auxAddr;
    }

    /**
     * Controlla se l'utente è membro del progetto
     * @param projName nome del progetto
     * @param key token relativo al client
     * @return "200 OK" in caso di successo, messaggio di errore altrimenti
     */
    private static String isMember(String projName, SelectionKey key) {
        String answer;
        AdvKey keyAttach = (AdvKey) key.attachment();
        Project aux = isProject(projName);

        if (aux == null) answer = "Nome del progetto non trovato";
        else {
            answer = "Non sei un membro del progetto";
            /* Controllo se l'utente è un membro del progetto */
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
     * Uguale al metodo "isMember" che però invia l'esito come risposta al client
     * @param projName nome del progetto
     * @param key token relativo al client
     */
    private static void isMemberClient(String projName, SelectionKey key) {
        String answer = isMember(projName, key);
        AdvKey keyAttach = (AdvKey) key.attachment();
        keyAttach.response = answer;
    }






    /**
     * Elenca i progetti di cui l'utente fa parte
     * @param key token relativo al client
     */
    private static void listProjects(SelectionKey key) {
        StringBuilder answer = new StringBuilder();
        AdvKey keyAttach = (AdvKey) key.attachment();

        /* Per ogni progetto, se l'utente è un membro allora aggiungo il nome del progetto
         * alla risposta */
        answer.append("| Nome progetto |");
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
     * Crea un nuovo progetto e lo aggiunge alla struttura dati dei progetti
     * @param projName nome del progetto
     * @param key token relativo al client
     */
    private static void createProject(String projName, SelectionKey key) {
        String answer;
        Project n;
        Project aux = isProject(projName);
        AdvKey keyAttach = (AdvKey) key.attachment();

        if (aux != null) answer = "Il nome del progetto è già in uso";
        else {
            String newAddr = generateAddress(lastAddr);
            if (newAddr.equals("NULL")) { // se ha esaurito tutti gli indirizzi di multicast
                answer = "Non posso generare ulteriori indirizzi di multicast";
            } else {
                //lastPort = lastPort + 1;
                n = new Project(projName, keyAttach.nickname, newAddr, String.valueOf(lastPort));
                projects.add(n);
                answer = "200 OK\n";
                answer += (newAddr + ":" + lastPort);
                try {
                    saveProject(n);
                } catch (IOException e) {
                    System.out.println("[ERROR] Impossibile salvare progetto sul disco");
                }
            }
        }

        keyAttach.response = answer;
    }

    /**
     * Aggiunge un nuovo membro ad un certo progetto
     * @param projName nome del progetto
     * @param nickname nome del nuovo membro
     * @param key token relativo al client
     */
    private static void addMember(String projName, String nickname, SelectionKey key) {
        String answer = isMember(projName, key);
        Project aux = isProject(projName); // se answer è "200 OK" allora otterrò sicuramente un riferimento
        AdvKey keyAttach = (AdvKey) key.attachment();
        int flag = 0;

        /* Se l'utente fa parte del progetto e il progetto esiste, allora può
         * aggiungere il nuovo membro */
        if (answer.equals("200 OK")) {
            getUpdatedData();
            /* Controlla che nickname rappresenti un utente registrato */
            if (!users.containsKey(nickname)) answer = "Il nome utente non esiste";
            else {
                /* Controlla che nickname non rappresenti già un membro del progetto */
                for (String member : aux.getMembers()) {
                    if (member.equals(nickname)) {
                        flag = 1;
                        break;
                    }
                }

                if (flag == 1)
                    answer = "L'utente è già un membro del progetto";
                else {
                    answer = aux.addMember(nickname);
                    updateProjState(aux, MAIN_PATH + "/" + projName, "members", null);
                }
            }
        }

        keyAttach.response = answer;
    }

    /**
     * Elenca i membri relativi ad un certo progetto e li invia al client
     * @param projName nome del progetto
     * @param key token relativo al client
     */
    private static void showMembers(String projName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder(); // Uso StringBuilder per migliorare le performance
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
     * Elenca le card associate ad un certo progetto e le invia al client
     * @param projName nome del progetto
     * @param key token relativo al client
     */
    private static void showCards(String projName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder();
        answer.append(isMember(projName, key));
        Project aux = isProject(projName);

        if (answer.toString().equals("200 OK")) {
            answer.append("\n").append(String.format("%-30s %-20s", "Nome card", "|Stato"));
            for (Card c : aux.listAllCards()) {
                answer.append("\n").append(String.format("%-30s %-20s", c.getName(), "|" + c.getList()));
            }
        }

        keyAttach.response = answer.toString();
    }

    /**
     * Prepara le informazioni relative alla card e le invia al client
     * @param projName nome del progetto
     * @param cardName nome della card
     * @param key token relativo al client
     */
    private static void showCard(String projName, String cardName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder();
        answer.append(isMember(projName, key));
        Project aux = isProject(projName);

        if (answer.toString().equals("200 OK")) {
            Card auxC = aux.getCard(cardName);

            /* La card non esiste all'interno del progetto */
            if (auxC == null) {
                answer = new StringBuilder("La card non fa parte del progetto");
            } else {
                answer.append("\n");
                answer.append("Nome: ").append(auxC.getName()).append("\n");
                answer.append("Descrizione: ").append(auxC.getDescription()).append("\n");
                answer.append("Lista: ").append(auxC.getList());
            }
        }

        keyAttach.response = answer.toString();
    }

    /**
     * Aggiunge una card ad un determinato progetto (la card finirà nella lista to_do)
     * @param projName nome del progetto
     * @param cardName nome della card
     * @param descr descrizione della card
     * @param key token relativo al client
     */
    private static void addCard(String projName, String cardName, String descr, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String answer = isMember(projName, key);
        Project aux = isProject(projName);
        Card c;

        if (answer.equals("200 OK")) {
            if ((c = aux.addCard(cardName, descr, 0)) == null) {
                answer = "Il nome della card è già in uso. Errore";
            } else {
                try {
                    /* Crea un file relativo alla card */
                    saveCard(aux, c, MAIN_PATH+"/"+projName);
                } catch (IOException e) {
                    System.out.println("[ERROR] Impossibile salvare la card sul disco");
                }
            }
        }

        keyAttach.response = answer;
    }

    /**
     * Muove la card di un certo progetto da una lista ad un'altra
     * @param projName nome del progetto
     * @param cardName nome della card
     * @param sourceList lista di origine
     * @param destList lista di destinazione
     * @param key token relativo al client
     */
    private static void moveCard(String projName, String cardName, String sourceList, String destList,
                                 SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String answer = isMember(projName, key);
        Project aux = isProject(projName);

        if (answer.equals("200 OK")) {
            answer = aux.moveCard(cardName, sourceList, destList);
            /* Aggiorno il file relativo alla card */
            updateCard(aux, aux.getCard(cardName), MAIN_PATH+"/"+projName, sourceList, destList);
        }

        keyAttach.response = answer;
    }

    /**
     * Ottiene la "storia" della card
     * @param projName nome del progetto
     * @param cardName nome della card
     * @param key token relativo al client
     */
    private static void getCardHistory(String projName, String cardName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder();
        answer.append(isMember(projName, key));
        Project aux = isProject(projName);

        if (answer.toString().equals("200 OK")) {
            Card auxC = aux.getCard(cardName);

            if (auxC == null)
                answer = new StringBuilder("La card non appartiene al progetto");
            else {
                for (String l : auxC.getMovements())
                    answer.append("\n").append(l);
            }
        }

        keyAttach.response = answer.toString();
    }

    /**
     * Cancella un progetto
     * @param projName nome del progetto
     * @param key token relativo al client
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
                answer = "Delle card devono essere completato, impossibile cancellare il progetto";
        }

        keyAttach.response = answer;
    }






    /**
     * Effettua la logout dell'utente cambiando il suo stato da "online"
     * a "offline"
     *
     * @param key token relativo al client
     * @throws IOException errore di I/O
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
     * Esegue la login dell'utente
     * @param nickname nome dell'utente
     * @param password password dell'utente
     * @param key token relativo al client
     * @throws IOException errore di I/O
     */
    public static void login(String nickname, String password, SelectionKey key) throws IOException {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String response;

        getUpdatedData();
        if (credentials.containsKey(nickname)) {
            if (credentials.get(nickname).equals(password)) {
                if (users.get(nickname).equals("online"))
                    response = "L'utente è già online";
                else {
                    if(!users.replace(nickname, "offline", "online"))
                        response = "Impossibile mandare l'utente online";
                    else {
                        keyAttach.nickname = nickname;
                        response = "200 OK";
                        supportServer.update(users, null);
                    }
                }
            } else
                response = "Password errata";
        } else
            response = "Il nickname non esiste";


        keyAttach.response = response;
    }






    /**
     * Ripristina le informazioni relative ai progetti
     * @return "[OK]" se il ripristino è avvenuto con successo,
     * "[ERROR]" altrimenti
     */
    private static String restoreProjects() {
        File basedir = new File(MAIN_PATH);
        // if(basedir.exists())
        String[] filesAndDir = basedir.list(); // ottiene la lista di file e directory

        /* Se il vecchio stato contiene degli elementi */
        if (filesAndDir != null) {
            for (String dir : filesAndDir) {
                /* registrations.json già analizzato */
                if (dir.equals("registrations.json"))
                    continue;

                String newPath = MAIN_PATH + "/" + dir;
                File aux = new File(newPath);
                /* Ogni directory mi rappresenta un progetto, con all'interno
                 *  tutte le informazioni relative a quest'ultimo */
                if (aux.isDirectory()) {
                    String[] projectInfo = aux.list();
                    String filePath;

                    if (projectInfo != null) {
                        Project p = new Project(dir);
                        /* Ogni file all'interno della directory aux può essere
                         * o il file "infos.json" o un file relativo ad una card
                         * del progetto*/
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

                        /* Dopo aver ripristinato i dati del progetto e delle card, può
                         * aggiungere il progetto così creato alla lista dei progetti */
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
     * Ripristina le informazioni di un progetto, come le informazioni di
     * multicast, la lista dei membri..
     * @param p riferimento al progetto
     * @param filePath path relativo a "infos.json"
     * @return "[OK]" se il ripristino è avvenuto con successo,
     * "[ERROR]" altrimenti
     */
    @SuppressWarnings("unchecked")
    private static String restoreInfos(Project p, String filePath) {
        /* Utilizza il JSONObject per recuperare le informazioni dal file
         * "infos.json" */
        try {
            Object obj = new JSONParser().parse(new FileReader(filePath));
            JSONObject jo = (JSONObject) obj;

            /* Imposta le varie informazioni del progetto */
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
     * Ripristina la card e le sue informazioni (nome, descrizione...)
     * @param p        riferimento al progetto
     * @param filePath path relativo al file "nomecard.json"
     * @return "[OK]" se il ripristino è avvenuto con successo,
     * "[ERROR]" altrimenti
     */
    @SuppressWarnings("unchecked")
    private static String restoreCard(Project p, String filePath) {
        try {
            Object obj = new JSONParser().parse(new FileReader(filePath));
            JSONObject jo = (JSONObject) obj;

            String cardName = (String) jo.get("cardName");
            String descr = (String) jo.get("description");
            /* Crea un nuovo oggetto card con le informazioni appena
            *  acquisite */
            Card aux = p.addCard(cardName, descr, 1);

            /* Imposta la lista della card e gli spostamenti della card */
            aux.setList((String) jo.get("list"));
            aux.setMovements((ArrayList<String>) jo.get("movements"));

        } catch (ParseException | IOException e) {
            return ERR;
        }

        return "[OK]";
    }

    /**
     * Crea una directory relativa ad un progetto, contenente varie informazioni come
     * membri, card e stato delle liste
     * @param p riferimento al progetto
     * @throws IOException saveProjectInfo, o saveCards, riscontra un errore I/O
     */
    private static void saveProject(Project p) throws IOException {
        String newPath = MAIN_PATH + "/" + p.getProjectName();
        File aux_proj = new File(newPath);
        if (aux_proj.mkdir()) // escluso ramo else
            System.out.println("[CREATE] Directory " + p.getProjectName() + " creata.");

        /* Crea un file json contenente le informazioni del progetto */
        saveProjectInfo(p, newPath);
    }

    /**
     * Crea un file contente le informazioni del progetto come membri, card, stato
     * delle liste e nome del progetto
     * @param proj     riferimento al progetto
     * @param projPath percorso iniziale su cui verranno salvati i file
     * @throws IOException riscontrato errore in I/O
     */
    @SuppressWarnings("unchecked")
    private static void saveProjectInfo(Project proj, String projPath) throws IOException {
        File infos = new File(projPath + "/infos.json");
        if (infos.createNewFile()) // escluso ramo else
            System.out.println("[CREATE] File \"infos.json\" creato.");

        FileWriter fileW = new FileWriter(infos);
        JSONObject jsonO = new JSONObject();

        /* Creazione dei vari campi del file JSON */
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


        /* Scrittura del file JSON */
        fileW.write(jsonO.toJSONString());
        fileW.close();
    }

    /**
     * Crea un file per la card contenente il nome, la descrizione, la lista attuale e
     * i movimenti relativi a quella card
     * @param p riferimento al progetto
     * @param c riferimento alla card
     * @param projPath percorso della directory del progetto
     * @throws IOException errore in I/O
     */
    @SuppressWarnings("unchecked")
    private static void saveCard(Project p, Card c, String projPath) throws IOException {
        File aux = new File(projPath + "/" + c.getName() + ".json");
        if (aux.createNewFile()) // escluso ramo else
            System.out.println("[CREATE] File \"" + c.getName() + ".json\" creato.");

        FileWriter fileW = new FileWriter(aux);
        JSONObject jsonO = new JSONObject();

        /* Creazione dei vari campi del file json */
        jsonO.put("cardName", c.getName());
        jsonO.put("description", c.getDescription().trim());
        jsonO.put("list", c.getList());

        JSONArray movements = new JSONArray();
        movements.addAll(c.getMovements());
        jsonO.put("movements", movements);

        /* Scrittura del file JSON */
        fileW.write(jsonO.toJSONString());
        fileW.close();

        /* Deve aggiornare la lista to_do (sul disco), poiché una nuova
         * card è stata creata */
        updateProjState(p, projPath, "todo", null);
    }

    /**
     * Copia i dati obsoleti, li aggiorna e li ricopia nel file. Tra la possibili scelte ci sono:
     * members, to_do, o una coppia (choice1, choice2) che indica la lista di destinazione e quella di
     * arrivo (da aggiornare)
     * @param proj riferimento al progetto
     * @param projPath percorso relativo alla directory del progetto
     * @param choice1 prima entità da aggiornare
     * @param choice2 seconda entità da aggiornare
     */
    @SuppressWarnings("unchecked")
    private static void updateProjState(Project proj, String projPath, String choice1, String choice2) {
        JSONObject joOut = null;
        try {
            Object obj = new JSONParser().parse(new FileReader(projPath + "/infos.json"));
            JSONObject joIn = (JSONObject) obj;
            joOut = new JSONObject();

            /* Estrae le informazioni dal file "infos.json" e le inserisce in un
             * JSONObject */
            joOut.put("projectName", joIn.get("projectName"));
            joOut.put("multicastAddress", joIn.get("multicastAddress"));
            joOut.put("multicastPort", joIn.get("multicastPort"));
            joOut.put("members", joIn.get("members"));
            joOut.put("todo", joIn.get("todo"));
            joOut.put("inprogress", joIn.get("inprogress"));
            joOut.put("toberevised", joIn.get("toberevised"));
            joOut.put("done", joIn.get("done"));

            /* Se joOut conteneva già un'associazione alla chiave, allora
             * il vecchio valore viene rimpiazzato. In questo modo inserisce
             * i nuovi dati */
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

            /* Se è stata inserita una seconda scelta sicuramente riguarda
             * lo spostamento di una card */
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
            System.out.println("[ERROR] Impossibile aggiornare dati sul disco");
            return;
        }

        /* Dopo aver inserito i nuovi dati all'interno di joOut, deve salvare queste
         * informazioni in "infos.json" */
        try {
            File infos = new File(projPath + "/infos.json");
            if (infos.delete()) {
                if (infos.createNewFile()) {
                    System.out.println("[CREATE] File \"infos.json\" aggiornato.");
                }
            } else {
                System.out.println("[ERROR] Impossibile aggiornare dati sul disco");
                return;
            }

            /* Scrittura del file sul disco */
            FileWriter fileW = new FileWriter(infos);
            fileW.write(joOut.toJSONString());
            fileW.close();
        } catch (IOException e) {
            System.out.println("[ERROR] Errore di I/O durante il salvataggio di dati sul filesystem");
        }
    }

    /**
     * Aggiorna i dati sul disco di una certa card
     * @param p riferimento del progetto
     * @param c riferimento della card (appartenente a p)
     * @param projPath percorso relativo alla directory del progetto
     * @param source lista di origine (to_do, inprogress, toberevised)
     * @param dest lista di destinazione (inprogress, toberevised, done)
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

            /* Salva la nuova lista (in cui si trova la card) e i
             * movimenti aggiornati */
            joOut.put("list", c.getList());

            JSONArray movements = new JSONArray();
            movements.addAll(c.getMovements());
            joOut.put("movements", movements);
        } catch (ParseException | IOException e) {
            System.out.println("Invalid filepath: " + path);
        }

        if (joOut == null) {
            System.out.println("[ERROR] Impossibile aggiornare dati sul disco");
            return;
        }

        /* Dopo aver aggiornato le informazioni, salva il file sul disco */
        try {
            File card = new File(path);
            if (card.delete()) {
                if (card.createNewFile()) {
                    System.out.println("[CREATE] File \"" + c.getName() + ".json\" aggiornato.");
                }
            } else {
                System.out.println("[ERROR] Impossibile aggiornare dati sul disco");
                return;
            }

            FileWriter fileW = new FileWriter(card);
            fileW.write(joOut.toJSONString());
            fileW.close();
        } catch (IOException e) {
            System.out.println("[ERROR] Errore di I/O durante il salvataggio di dati sul filesystem");
        }

        updateProjState(p, projPath, source, dest);
    }

    /**
     * Elimina tutti i file e sotto-directories relative ad un certo path
     * @param masterPath percorso della directory principale
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
