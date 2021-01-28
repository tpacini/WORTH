package Commons;

import Client.ClientNotifyInterface;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class RMICallbackImpl extends RemoteServer implements RMICallbackInterface {
    private final List<ClientNotifyInterface> clients;  // lista dei client registrati
    HashMap<String, String> users;                      // lista degli utenti registrati

    /* Costruttore */
    public RMICallbackImpl() throws RemoteException {
        super();
        clients = new ArrayList<>();
        users = new HashMap<>();
    }

    /**
     * Permette ai vari client di registarsi per ricevere notifiche
     * @param ClientInterface interfaccia remota del client
     * @throws RemoteException errore nel remote method
     */
    public synchronized void registerForCallback(ClientNotifyInterface ClientInterface) throws RemoteException {
        if (!clients.contains(ClientInterface)) {
            clients.add(ClientInterface);
            ClientInterface.notifyChanges(users, 1);
            System.out.println("[REMOTE] Nuovo client registrato.");
        }
    }

    /**
     * Annulla la registrazione per la callback
     * @param Client interfaccia remota del client
     * @throws RemoteException errore nel remote method
     */
    public synchronized void unregisterForCallback(ClientNotifyInterface Client) throws RemoteException {
        if (clients.remove(Client))
            System.out.println("[REMOTE] Eliminata registrazione del client ");
        else
            System.out.println("[REMOTE] Impossibile eliminare registrazione del client");
    }

    /**
     * Esegue la callback per ogni client registrato
     * @throws RemoteException errore nel remote method
     */
    private void doCallbacks() throws RemoteException {
        ArrayList<ClientNotifyInterface> clientsDown = new ArrayList<>();
        System.out.println("[REMOTE]*Starting callbacks.*");
        for (ClientNotifyInterface client : clients) {
            try {
                client.notifyChanges(users, 0);
            } catch (ConnectException e) {
                //unregisterForCallback(client);
                clientsDown.add(client);
            }
        }

        /* Controlla se dei client siano andati down e quindi abbiano creato
         * un'exception durante le callback, in caso positivo elimino il riferimento
         * alla loro interfaccia dalla struttura dati */
        int count = 0;
        for (ClientNotifyInterface client : clientsDown) {
            clients.remove(client);
            count++;
        }

        if (count > 0) System.out.println("[REMOTE]* Eliminate " + count + " registrazioni al client");
        System.out.println("[REMOTE]*Callbacks completate.*");
    }

    /**
     * Aggiorna la struttura dati degli utenti con i nuovi valori, dopodiché
     * esegue la callback su tutti i client (che in questo modo riceveranno gli
     * aggiornamenti)
     * @param users struttura dati aggiornata
     * @throws RemoteException errore nel remote method
     */
    public void update(HashMap<String, String> users) throws RemoteException {
        this.users = users;
        doCallbacks();
    }
}
