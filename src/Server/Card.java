package Server;

import java.util.ArrayList;

public class Card {
    String name;                 // nome della card
    String description;          // descrizione della card
    String list;                 // nome della lista in cui si trova la card
    ArrayList<String> movements; // lista dei movimenti effettuati

    /* Costruttore */
    public Card(String name, String descr, String list) {
        this.name = name;
        this.description = descr;
        this.list = list;
        movements = new ArrayList<>();
        movements.add(list);
    }

    /* Costruttore utilizzato per ripristinare lo stato del server */
    public Card(String name, String descr) {
        this.name = name;
        this.description = descr;

        movements = new ArrayList<>();
        /* I movimenti e la lista verranno aggiunti in un secondo momento */
    }

    /**
     * Sposta la card da una lista ad un'altra
     * @param newList nome della nuova lista
     */
    public void changeList(String newList) {
        this.list = newList;
        movements.add(newList);
    }

    /******** GETTERS ********/

    public String getName() { return this.name; }

    public String getDescription() { return this.description; }

    public String getList() { return this.list; }

    public ArrayList<String> getMovements() { return this.movements; }

    /******** SETTERS ********/

    public void setList(String list) { this.list = list; }

    public void setMovements(ArrayList<String> movements) { this.movements = movements; }
}
