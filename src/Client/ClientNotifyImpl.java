package Client;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.HashMap;
import java.util.Map;

/* Classe che implementa l'interfaccia remota del client */
public class ClientNotifyImpl extends RemoteObject implements ClientNotifyInterface {
    HashMap <String, String> local_users; // lista degli utenti con il loro stato

    public ClientNotifyImpl() throws RemoteException {
        super(); // To Remote Object
    }

    /**
     * Stampa a schermo le informazioni sugli utenti aggiornate, altrimenti aggiorna
     * solamente la struttura dati
     * @param users struttura dati aggiornata
     * @param visualize 1 se si vogliono visualizzare a schermo le informazioni
     *                  0 altrimenti
     * @throws RemoteException errore nel remote method
     */
    public void notifyChanges(HashMap<String, String> users, int visualize) throws RemoteException {
        /* Stampa a schermo l'elenco degli utenti con il relativo stato */
        if(visualize == 1) {
            System.out.printf("\n%-20s %-30s\n", "Username", "|Stato");
            for (Map.Entry<String, String> entry : users.entrySet())
                System.out.printf("%-20s %-30s\n", entry.getKey(), "|" + entry.getValue());
        }

        /* Deve salvare il riferimento alla struttura dati, così il client
         * mantiene le informazioni aggiornate */
        local_users = users;
    }

    /**
     * Visualizza l'elenco degli utenti
     * @param online 1 se si vogliono visualizzare solo gli utenti online
     *               0 altrimenti
     */
    public void getLocalUserList(int online) throws RemoteException {
        if(online == 1) {
            System.out.println("|Username|");
            for (Map.Entry<String, String> entry : local_users.entrySet()) {
                if(entry.getValue().equals("online"))
                    System.out.println(entry.getKey());
            }
        }
        else {
            System.out.printf("%-20s %-30s\n", "Username", "|State");
            for (Map.Entry<String, String> entry : local_users.entrySet())
                System.out.printf("%-20s %-30s\n", entry.getKey(), "|" + entry.getValue());
        }
    }
}
