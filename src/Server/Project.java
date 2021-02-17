package Server;

import java.util.ArrayList;

public class Project {
    /* The four list that handle the cards: to_do, inprogress, toberevised e done */
    private ArrayList<String> todo;
    private ArrayList<String> inprogress;
    private ArrayList<String> toberevised;
    private ArrayList<String> done;

    private ArrayList<String> members;     // members' list
    private final ArrayList<Card> cards;   // cards' list
    private final String projectName;      // project name
    private String multicastAddr;          // chat's multicast address
    private String multicastPort;          // chat's multicast port

    private final String OK = "200 OK";

    /* Constructor */
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

    /* Constructor used in server's recovery mode */
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

    /**
     * Add a user to the members
     * @param nickname user's name
     * @return "200 OK"
     */
    public String addMember(String nickname) {
        members.add(nickname);
        return OK;
    }

    /**
     * Obtain the list of all the cards that belong to the project
     * @return list's reference
     */
    public ArrayList<Card> listAllCards () {
        return cards;
    }

    /**
     * If possible, obtain the card's reference
     * @param cardName card's name
     * @return if the card's name exists, the card's reference, null otherwise
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
     * If possible, add the card to the project
     * @param cardName card's name
     * @param descr card's description
     * @param recover 1 if the method has been called in recovery mode,
     *                0 otherwise
     * @return if the card has been added, card's reference, null otherwise
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
                /* If recover==1 so don't execute to_do.add(cardName) because in recovery
                *  mode the to_do list is updated in a unique operation */
                aux = new Card(cardName, descr);
                cards.add(aux);
            }
        }

        return aux;
    }

    /**
     * If possible move the card from the source's list to the destination's list
     * @param cardName card's name
     * @param source source's list
     * @param dest destination's list
     * @return "200 OK" if the operation has been successful, an error message otherwise
     */
    public String moveCard(String cardName, String source, String dest) {
        ArrayList<String> auxSource;
        ArrayList<String> auxDest;
        Card c;
        String response;

        /* Check the card existence */
        if((c = getCard(cardName)) == null)
            response = "The card doesn't exists.";
        else {
            /* Take the source's list reference */
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
                    return "Invalide source list. Possible choices: todo, inprogress," +
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
                    return "Invalid destination list. Possible choices: inprogress," +
                            " toberevised, done. Error";
            }

            /* Check the existence of a path between the source and the destionation list */
            if (source.equals("todo") && (dest.equals("toberevised") || dest.equals("done")))
                response = "From 'todo' can go only to 'inprogress'. Error";
            else if (source.equals(dest))
                response = "The card is already inside the " + source + " list";
            /* If the operation is possible */
            else {
                /* Check if the card belongs to the list */
                if(!auxSource.remove(cardName))
                    response = "The card doesn't belong to the " + source + " list. Error";
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
     * Check if all the cards are completed (belong to done list)
     * @return 1 if all the cards are completed, 0 otherwise
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
