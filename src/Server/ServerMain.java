package Server;

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
    final static int TIMER = 5000;                      // timer del selettore
    final static String MAIN_PATH = "../SavedState";

    final static String rName = "Server";               // nome associato al registry
    final static int rPort = 5000;                      // numero di porta corrispondente al registry
    private static Selector sel;                        // selettore

    private static ArrayList<Project> projects;         // lista dei progetti
    private static HashMap<String, String> users;       // lista degli utenti con il loro stato
    private static HashMap<String, String> credentials; // lista delle credenziali degli utenti

    private static ServerImpl supportServer;            // riferimento locale dell'interfaccia remota del server
    private static String lastAddr = "228.0.0.0";       // ultimo indirizzo utilizzato per il multicast
    final static int lastPort = 2000;                   // ultima porta utilizzata per il multicast

    public static void main(String[] args) throws IOException {
        int port;
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

        /* Recupera le informazioni salvate su file */
        restoreServerState();
        System.out.println("----------------------------");

        setRMIAndRegistry(0, 39000);

        /* Inizializza la SocketChannel e si prepara a ricevere nuove connessioni */
        ServerSocketChannel servSockChan = ServerSocketChannel.open();
        servSockChan.socket().bind(new InetSocketAddress("localhost", port));
        servSockChan.configureBlocking(false);
        sel = Selector.open();
        servSockChan.register(sel, SelectionKey.OP_ACCEPT);
        System.out.println("[WAITING] Connections on port " + port);

        while (true) {
            try {
                sel.select(TIMER);
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
                            users.replace(nickname, "online", "offline");
                            supportServer.update(users);
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

        /* Prima di uscire, il programma deve salvare il proprio stato */
        //servSockChan.close();
        //sel.close();
        //UnicastRemoteObject.unexportObject(supportServer, false);
        //System.out.println("----------------------------");
        //saveServerState();
    }



    /*  SELECTOR METHODS **************/

    /**
     * Accetta la connessione con il client
     *
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
     *
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
     *
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



    /*  AUXILIARY METHODS **************/

    /**
     * Effettua il parsing della richiesta dell'utente e sceglie il metodo con
     * cui risolverla
     *
     * @param key token che rappresenta il "channel" riferito ad un certo client
     * @throws IOException errore di I/O
     */
    private static void parser(SelectionKey key) throws IOException {
        String resp;
        AdvKey keyAttach = (AdvKey) key.attachment();
        String[] commandAndArg = keyAttach.request.split(" ");
        /* Cleaning the input */
        for (int i = 0; i < commandAndArg.length; i++)
            commandAndArg[i] = commandAndArg[i].trim();

        switch (commandAndArg[0]) {
            case "login":
                if ((resp = userIdentification(key, commandAndArg)).equals("200 OK")) {
                    /* Invia le informazioni per far registrare il client al sistema
                     * di notifica */
                    resp += "\n" + rName + " " + rPort;
                }
                keyAttach.response = resp;
                key.interestOps(SelectionKey.OP_WRITE);
                break;
            case "register":
                resp = userIdentification(key, commandAndArg);
                keyAttach.response = resp;
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

    private static void setRMIAndRegistry(int count, int port) {
        try {
            /* Imposta il sistema di notifica RMI e crea il registry */
            supportServer = new ServerImpl();
            ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(supportServer, port);

            /* Crea il registry associato ad un determinato nome e ad una determinata porta */
            LocateRegistry.createRegistry(rPort);
            Registry registry = LocateRegistry.getRegistry(rPort);
            registry.bind(rName, stub);
            //supportServer.update(users); verrà fatta durante login/register....
        } catch (IOException e) {
            System.out.println("[BIND ERROR] Porta " + port + " già in uso? Incrementa.");
            setRMIAndRegistry(count+1, port+1);
            if(count > 3) System.out.println("[ERROR] Fatal error");
        } catch (AlreadyBoundException e) {
            System.out.println("[BIND ERROR] Registry already bound");
        }

    }

    /**
     * Invia al client l'indirizzo e la porta utilizzati per il multicast di quel determinato
     * progetto
     *
     * @param projName nome del progetto
     * @param key      token relativo al client
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
     *
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
     * Utilizza i metodi dell'interfaccia remota per effettuare la login/registrazione dell'utente
     *
     * @param key         token che rappresenta la registrazione, di un certo "channel", al selettore
     * @param credentials stringa contenente username e password dell'utente
     * @return "200 OK" in caso di successo, "Error" altrimenti
     * @throws IOException login o register hanno riscontrato una RemoteException
     */
    private static String userIdentification(SelectionKey key, String[] credentials) throws IOException {
        String answer;

        if (credentials[0].equals("login")) {
            answer = login(credentials[1], credentials[2]);
            if (answer.equals("200 OK"))
                /* Allego il nickname all'utente */
                ((AdvKey) key.attachment()).nickname = credentials[1];
        }
        else if (credentials[0].equals("register"))
            answer = register(credentials[1], credentials[2]);
        else return "Error";

        return answer;
    }

    /**
     * Genera un nuovo indirizzo per il multicast di un progetto
     *
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
     *
     * @param projName nome del progetto
     * @param key      token relativo al client
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
     * Metodo "isMember" che invia l'esito come risposta al client
     *
     * @param projName nome del progetto
     * @param key      token relativo al client
     */
    private static void isMemberClient(String projName, SelectionKey key) {
        String answer = isMember(projName, key);
        AdvKey keyAttach = (AdvKey) key.attachment();
        keyAttach.response = answer;
    }



    /*  MAIN METHODS **************/

    /**
     * Elenca i progetti di cui l'utente fa parte e li invia al client
     *
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
     *
     * @param projName nome del progetto
     * @param key      token relativo al client
     */
    private static void createProject(String projName, SelectionKey key) {
        String answer;
        Project n;
        Project aux = isProject(projName);
        AdvKey keyAttach = (AdvKey) key.attachment();

        if (aux != null) answer = "Il nome del progetto è già in uso";
        else {
            String newAddr = generateAddress(lastAddr);
            if (newAddr.equals("NULL")) {
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
     *
     * @param projName nome del progetto
     * @param nickname nome del nuovo membro
     * @param key      token relativo al client
     */
    private static void addMember(String projName, String nickname, SelectionKey key) {
        String answer = isMember(projName, key);
        Project aux = isProject(projName); // se answer è "200 OK" allora otterrò sicuramente un riferimento
        AdvKey keyAttach = (AdvKey) key.attachment();
        int flag = 0;

        /* Se l'utente fa parte del progetto e il progetto esiste, allora può
         * aggiungere il nuovo membro */
        if (answer.equals("200 OK")) {
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
     *
     * @param projName nome del progetto
     * @param key      token relativo al client
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
     *
     * @param projName nome del progetto
     * @param key      token relativo al client
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
     *
     * @param projName nome del progetto
     * @param cardName nome della card
     * @param key      token relativo al client
     */
    private static void showCard(String projName, String cardName, SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        StringBuilder answer = new StringBuilder();
        answer.append(isMember(projName, key));
        Project aux = isProject(projName);

        if (answer.toString().equals("200 OK")) {
            Card auxC = aux.getCard(cardName);

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
     *
     * @param projName nome del progetto
     * @param cardName nome della card
     * @param descr    descrizione della card
     * @param key      token relativo al client
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
     *
     * @param projName   nome del progetto
     * @param cardName   nome della card
     * @param sourceList lista di origine
     * @param destList   lista di destinazione
     * @param key        token relativo al client
     */
    private static void moveCard(String projName, String cardName, String sourceList, String destList,
                                 SelectionKey key) {
        AdvKey keyAttach = (AdvKey) key.attachment();
        String answer = isMember(projName, key);
        Project aux = isProject(projName);

        if (answer.equals("200 OK")) {
            answer = aux.moveCard(cardName, sourceList, destList);
            updateCard(aux, aux.getCard(cardName), MAIN_PATH+"/"+projName, sourceList, destList);
        }

        keyAttach.response = answer;
    }

    /**
     * Ottiene la "storia" della card e la invia al client
     *
     * @param projName nome del progetto
     * @param cardName nome della card
     * @param key      token relativo al client
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
     *
     * @param projName nome del progetto
     * @param key      token relativo al client
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

        users.replace(keyAttach.nickname, "online", "offline");
        supportServer.update(users);

        keyAttach.response = null;
        keyAttach.request = null;
        keyAttach.nickname = null;
    }

    /**
     * Esegue la registrazione dell'utente, aggiungendolo alla lista degli utenti
     * e cambiando il suo stato da "offline" a "online"
     *
     * @param nickname nickname dell'utente
     * @param password password dell'utente
     * @return "200 OK" se la registrazione avviene con successo, un messaggio
     * di errore altrimenti
     * @throws IOException update riscontra una RemoteException
     */
    public static String register(String nickname, String password) throws IOException {
        String response;
        if (credentials.containsKey(nickname)) {
            response = "Il nickname è già in uso";
        } else {
            credentials.put(nickname, password);
            users.put(nickname, "offline");
            response = "200 OK";
            supportServer.update(users);
            updateRegistrations();
        }

        return response;
    }

    /**
     * Esegue la login dell'utente, cambiando il suo stato da "offline" a "online"
     *
     * @param nickname nickname dell'utente
     * @param password password dell'utente
     * @return "200 OK" se il login avviene con successo, un messaggio di errore
     * altrimenti
     * @throws IOException update riscontra una RemoteException
     */
    public static String login(String nickname, String password) throws IOException {
        String response;
        if (credentials.containsKey(nickname)) {
            if (credentials.get(nickname).equals(password)) {
                if (users.get(nickname).equals("online"))
                    response = "L'utente è già online";
                else {
                    users.replace(nickname, "offline", "online");
                    response = "200 OK";
                    supportServer.update(users);
                }
            } else
                response = "Password errata";
        } else {
            response = "Il nickname non esiste";
        }

        return response;
    }



    /*  SERVER STATE METHODS **************/

    /**
     * Crea una directory relativa ad un progetto, contenente varie informazioni come
     * membri, card e stato delle liste
     *
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
     *
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

        jsonO.put("cardName", c.getName());
        jsonO.put("description", c.getDescription().trim());
        jsonO.put("list", c.getList());

        JSONArray movements = new JSONArray();
        movements.addAll(c.getMovements());
        jsonO.put("movements", movements);

        /* Scrittura del file JSON */
        fileW.write(jsonO.toJSONString());
        fileW.close();

        /* Devo aggiornare la lista to_do (sul disco) */
        updateProjState(p, projPath, "todo", null);
    }



    @SuppressWarnings("unchecked")
    private static void updateProjState(Project proj, String projPath, String choice1, String choice2) {
        JSONObject joOut = null;
        try {
            Object obj = new JSONParser().parse(new FileReader(projPath + "/infos.json"));
            JSONObject joIn = (JSONObject) obj;
            joOut = new JSONObject();

            joOut.put("projectName", joIn.get("projectName"));
            joOut.put("multicastAddress", joIn.get("multicastAddress"));
            joOut.put("multicastPort", joIn.get("multicastPort"));
            joOut.put("members", joIn.get("members"));
            joOut.put("todo", joIn.get("todo"));
            joOut.put("inprogress", joIn.get("inprogress"));
            joOut.put("toberevised", joIn.get("toberevised"));
            joOut.put("done", joIn.get("done"));

            /* Se joOut conteneva già un'associazione alla chiave, allora
             * il vecchio valore viene rimpiazzato */
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

            FileWriter fileW = new FileWriter(infos);
            fileW.write(joOut.toJSONString());
            fileW.close();
        } catch (IOException e) {
            System.out.println("[ERROR] Errore di I/O durante il salvataggio di dati sul filesystem");
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateRegistrations() {
        String path = MAIN_PATH+"/registrations.json";
        File registrations = new File(path);
        /* Controlla se il file esiste altrimenti lo crea */
        if (registrations.exists()) {
            if (!registrations.delete()) {
                System.out.println("[ERROR] Impossibile aggiornare le registrazioni");
            }
        }
        try {
            if (registrations.createNewFile()) {
                try {
                    FileWriter fileReg = new FileWriter(registrations);
                    JSONObject joOut = new JSONObject();

                    for (String user : credentials.keySet())
                        joOut.put(user, credentials.get(user));

                    fileReg.write(joOut.toJSONString());
                    fileReg.close();
                    System.out.println("[UPDATE] Registrazioni aggiornate.");
                } catch (FileNotFoundException e) {
                    System.out.println("[ERROR] Invalid path: " + path);
                }
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Impossibile aggiornare le registrazioni");
        }
    }

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
            System.out.println("[ERROR] Impossibile aggiornare dati sul disco");
            return;
        }

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
     * Ripristina lo stato antecedente alla chiusura del server
     */
    private static void restoreServerState() {
        File dir = new File(MAIN_PATH);
        /* Controllo se la cartella esiste altrimenti non c'è nulla da recuperare */
        if (!dir.exists())
            System.out.println("[NULL] Nessun file da recuperare.");
        if (dir.mkdir()) System.out.println("[CREATE] Directory SavedState creata.");
        else if (!dir.isDirectory())
            System.out.println("[ERROR] " + MAIN_PATH + " non è una directory");
            /* Se esiste inizio il recupero */
        else {
            /* Recupera le credenziali degli utenti */
            String fileN = MAIN_PATH + "/registrations.json";
            try {
                Object obj = new JSONParser().parse(new FileReader(fileN));
                JSONObject jo = (JSONObject) obj;

                /* Salva i dati degli utenti sulle relative strutture dati */
                for (Object user : jo.keySet()) {
                    String nickname = (String) user;
                    String password = (String) jo.get(user);
                    credentials.put(nickname, password);
                    users.put(nickname, "offline");
                }

            } catch (FileNotFoundException e) {
                System.out.println("[ERROR] Invalid path/file: " + fileN);
            } catch (IOException | ParseException e) {
                System.out.println("[ERROR] Error while parsing");
            }

            /* Itera sulle directories e recupera le informazioni riguardanti i progetti */
            restoreProjects();
            System.out.println("Old state restored.");
        }
    }

    /**
     * Ripristina le informazioni riguardanti i progetti e le informazioni
     * relative
     */
    private static void restoreProjects() {
        File basedir = new File(MAIN_PATH);
        // if(basedir.exists())
        String[] filesAndDir = basedir.list(); // ottiene la lista di file e directory

        if (filesAndDir == null)
            System.out.println(basedir.getName() + " non contiene elementi. Errore");
        else {
            for (String dir : filesAndDir) {
                /* registrations.json già analizzato */
                if (dir.equals("registrations.json"))
                    continue;

                String newPath = MAIN_PATH + "/" + dir;
                File aux = new File(newPath);
                /* Ogni directory mi rappresenta un progetto, con all'interno
                 *  tutte le informazioni relative a quest'ultimo*/
                if (aux.isDirectory()) {
                    String[] projectInfo = aux.list();
                    String filePath;
                    /* Per come è progettata la creazione dei file e delle directory,
                     *  l'evento projectInfo == null non dovrebbe presentarsi */
                    if (projectInfo != null) {
                        Project p = new Project(dir);
                        for (String name : projectInfo) {
                            filePath = newPath + "/" + name;
                            if (name.equals("infos.json"))
                                restoreInfos(p, filePath);
                            else
                                restoreCard(p, filePath);
                        }
                        projects.add(p);
                    }
                }
            }
        }
    }

    /**
     * Ripristina le informazioni di un progetto, come le informazioni di
     * multicast, la lista dei membri..
     *
     * @param p        riferimento al progetto
     * @param filePath path relativo a "infos.json"
     */
    @SuppressWarnings("unchecked")
    private static void restoreInfos(Project p, String filePath) {
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
            System.out.println("Invalid filepath: " + filePath);
        }
    }

    /**
     * Ripristina la card e le sue informazioni
     *
     * @param p        riferimento al progetto
     * @param filePath path relativo al file "nomecard.json"
     */
    @SuppressWarnings("unchecked")
    private static void restoreCard(Project p, String filePath) {
        try {
            Object obj = new JSONParser().parse(new FileReader(filePath));
            JSONObject jo = (JSONObject) obj;

            String cardName = (String) jo.get("cardName");
            String descr = (String) jo.get("description");
            Card aux = p.addCard(cardName, descr, 1);

            aux.setList((String) jo.get("list"));
            aux.setMovements((ArrayList<String>) jo.get("movements"));

        } catch (ParseException | IOException e) {
            System.out.println("Invalid filepath: " + filePath);
        }
    }

    /**
     * Elimina tutti i file e sotto-directories relative ad un certo path
     *
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
