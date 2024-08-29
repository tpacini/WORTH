package Commons;

import Client.ClientNotifyInterface;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class RMICallbackImpl extends RemoteServer implements RMICallbackInterface {
    private final List<ClientNotifyInterface> clients;  
    HashMap<String, String> users;

    public RMICallbackImpl() throws RemoteException {
        super();
        clients = new ArrayList<>();
        users = new HashMap<>();
    }

    /**
     * Register client for callbacks
     * @param ClientInterface client remote interface
     * @throws RemoteException remote method error
     */
    public synchronized void registerForCallback(ClientNotifyInterface ClientInterface) throws RemoteException {
        if (!clients.contains(ClientInterface)) {
            clients.add(ClientInterface);
            ClientInterface.notifyChanges(users, 1);
            System.out.println("[REMOTE] New client registered.");
        }
    }

    /**
     * Unregister client for callback
     * @param Client client remote interface
     * @throws RemoteException remote method error
     */
    public synchronized void unregisterForCallback(ClientNotifyInterface Client) throws RemoteException {
        if (clients.remove(Client))
            System.out.println("[REMOTE] Client unregistered.");
        else
            System.out.println("[REMOTE] Unable to unregister client.");
    }

    /**
     * Perform callback for every registered client
     * @throws RemoteException errore nel remote method
     */
    private void doCallbacks() throws RemoteException {
        ArrayList<ClientNotifyInterface> clientsDown = new ArrayList<>();
        System.out.println("[REMOTE] Starting callbacks.");
        for (ClientNotifyInterface client : clients) {
            try {
                client.notifyChanges(users, 0);
            } catch (ConnectException e) {
                //unregisterForCallback(client);
                clientsDown.add(client);
            }
        }

        /* Check if the client are still up, if an exception occurs during
         * the callback, the client interface reference will be removed.*/
        int count = 0;
        for (ClientNotifyInterface client : clientsDown) {
            clients.remove(client);
            count++;
        }

        if (count > 0) System.out.println("[REMOTE]* Removed " + count + " client registrations");
        System.out.println("[REMOTE] Callbacks completed");
    }

    /**
     * Update "users" data structure 
     * @param users updated data structure
     * @throws RemoteException remote method error
     */
    public synchronized void update(HashMap<String, String> users, String nickname) throws RemoteException {
        if(nickname != null) {
            if(!this.users.containsKey(nickname)) this.users.put(nickname, "offline");
        }
        if(users != null) this.users = users;

        doCallbacks();
    }
}
