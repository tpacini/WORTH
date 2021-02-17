package Server;

import java.util.ArrayList;

public class Card {
    String name;                 // card's name
    String description;          // card's description
    String list;                 // card's list
    ArrayList<String> movements; // list of the card's movements through the lists

    /* Main constructor */
    public Card(String name, String descr, String list) {
        this.name = name;
        this.description = descr;
        this.list = list;
        movements = new ArrayList<>();
        movements.add(list);
    }

    /* Constructor used in server's recovery mode */
    public Card(String name, String descr) {
        this.name = name;
        this.description = descr;

        movements = new ArrayList<>();
    }

    /**
     * Move the card to a new list
     * @param newList name of the new list
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
