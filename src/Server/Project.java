package Server;

import java.util.ArrayList;

public class Project {
    /* Le 4 liste di gestione: to_do, inprogress, toberevised e done */
    private ArrayList<String> todo;
    private ArrayList<String> inprogress;
    private ArrayList<String> toberevised;
    private ArrayList<String> done;

    private ArrayList<String> members;     // lista dei membri del progetto
    private final ArrayList<Card> cards;   // lista delle card associate al progetto
    private final String projectName;      // nome del progetto
    private String multicastAddr;          // indirizzo di multicast della chat
    private String multicastPort;          // porta di multicast della chat

    private final String OK = "200 OK";

    /* Costruttore */
    public Project(String projectName, String nickname_user, String multicastAddr, String multicastPort) {
        members = new ArrayList<>();
        members.add(nickname_user);
        this.projectName = projectName;

        cards = new ArrayList<>();

        todo = new ArrayList<>();
        inprogress = new ArrayList<>();
        toberevised = new ArrayList<>();
        done = new ArrayList<>();

        this.multicastAddr = multicastAddr;
        this.multicastPort = multicastPort;
    }

    /* Costruttore utilizzato durante il ripristino dello stato del server */
    public Project(String projectName) {
        members = new ArrayList<>();
        this.projectName = projectName;

        cards = new ArrayList<>();

        todo = new ArrayList<>();
        inprogress = new ArrayList<>();
        toberevised = new ArrayList<>();
        done = new ArrayList<>();

        this.multicastAddr = null;
        this.multicastPort = null;
    }


    public String addMember(String nickname) {
        members.add(nickname);
        return OK;
    }

    /**
     * Restituisce la lista contenente tutte le card associate al progetto
     * @return ArrayList
     */
    public ArrayList<Card> listAllCards () {
        return cards;
    }

    /**
     * Se possibile restituisce il riferimento alla card con uno specifico nome
     * @param cardName nome della card
     * @return se esiste riferimento a Card, null altrimenti
     */
    public Card getCard(String cardName) {
        Card aux = null;

        for(Card c : cards) {
            if(c.getName().equals(cardName)) {
                aux = c;
                break;
            }
        }

        return aux;
    }

    /**
     * Se possibile aggiunge la card al progetto
     * @param cardName nome della card
     * @param descr descrizione della card
     * @param recover 1 se la carta viene aggiunta per ripristinare lo stato del server,
     *                0 se la carta viene aggiunta su richiesta dell'utente
     * @return un riferimento a Card se la carta è stata aggiunta, null altrimenti
     */
    public Card addCard(String cardName, String descr, int recover) {
        Card aux;
        if(getCard(cardName) != null)
            aux = null;
        else {
            if(recover == 0) {
                aux = new Card(cardName, descr, "todo");
                cards.add(aux);
                todo.add(cardName);
            }
            else {
                aux = new Card(cardName, descr);
                cards.add(aux);
            }
        }

        return aux;
    }

    /**
     * Se possibile sposta la card dalla lista di partenza a quella di destinazione
     * @param cardName nome della card
     * @param source lista di partenza
     * @param dest lista di arrivo
     * @return "200 OK" se lo spostamento è avvenuto con successo, un messaggio di errore
     *          altrimenti
     */
    public String moveCard(String cardName, String source, String dest) {
        ArrayList<String> auxSource;
        ArrayList<String> auxDest;
        Card c;
        String response;

        /* Controlla se la carta esiste */
        if((c = getCard(cardName)) == null)
            response = "La card non esiste.";
        else {
            /* Prende il riferimento alla lista di partenza */
            switch (source) {
                case "todo":
                    auxSource = this.todo;
                    break;
                case "inprogress":
                    auxSource = this.inprogress;
                    break;
                case "toberevised":
                    auxSource = this.toberevised;
                    break;
                default:
                    return "Lista sorgente invalida. Scelte possibili: todo, inprogress," +
                            " toberevised. Error";
            }

            /* Prende il riferimento alla lista di destinazione */
            switch (dest) {
                case "inprogress":
                    auxDest = this.inprogress;
                    break;
                case "toberevised":
                    auxDest = this.toberevised;
                    break;
                case "done":
                    auxDest = this.done;
                    break;
                default:
                    return "Lista destinazione invalida. Scelte possibili: inprogress," +
                            " toberevised, done. Errore";
            }

            /* Controlla se esiste "un percorso" dalla lista sorgente alla lista destinazione */
            if (source.equals("todo") && (dest.equals("toberevised") || dest.equals("done")))
                response = "Da 'todo' può andare solo in 'inprogress'. Errore";
            else if (source.equals(dest))
                response = "La card si trova già in " + source;
            /* Se lo spostamento è possibile */
            else {
                /* Controlla se la card appartiene alla lista di origine */
                if(!auxSource.remove(cardName))
                    response = "La card non appartiene alla lista " + source + ". Error";
                else {
                    auxDest.add(cardName);
                    c.changeList(dest);
                    response = OK;
                }
            }
        }

        return response;
    }

    /**
     * Controlla se tutte le card sono state "eseguite" (si trovano in done)
     * @return 1 se tutti i compiti sono stati eseguiti, 0 altrimenti
     */
    public int isDone() {
        if(inprogress.isEmpty() && toberevised.isEmpty() && todo.isEmpty())
            return 1;
        return 0;
    }

    /******** GETTERS ********/

    public ArrayList<String> getMembers() { return this.members; }

    public String getProjectName() { return this.projectName; }

    public String getMulticastParameter() { return this.multicastAddr + ":" + this.multicastPort; }

    public ArrayList<String> getTodo() { return this.todo; }

    public ArrayList<String> getInprogress() { return this.inprogress; }

    public ArrayList<String> getToberevised() { return this.toberevised; }

    public ArrayList<String> getDone() { return this.done; }

    /******** SETTERS ********/

    public void setMulticastAddr(String addr) { this.multicastAddr = addr; }

    public void setMulticastPort(String port) { this.multicastPort = port; }

    public void setMembers(ArrayList<String> members) { this.members = members; }

    public void setTodo(ArrayList<String> todo) { this.todo = todo; }

    public void setInprogress(ArrayList<String> inprogress) { this.inprogress = inprogress; }

    public void setToberevised(ArrayList<String> toberevised) {this.toberevised = toberevised; }

    public void setDone(ArrayList<String> done) { this.done = done; }

}
