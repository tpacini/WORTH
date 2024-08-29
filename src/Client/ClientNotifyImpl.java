package Client;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.HashMap;
import java.util.Map;

/* Implementation of the client remote interface */
public class ClientNotifyImpl extends RemoteObject implements ClientNotifyInterface {
    HashMap <String, String> local_users; // list of users and state

    public ClientNotifyImpl() throws RemoteException {
        super(); // To Remote Object
    }

    /**
     * Show and/or updates the users list and their state
     * 
     * @param users updated users list
     * @param visualize 1, show users list and state
     *                  0, otherwise
     * @throws RemoteException remote method error
     */
    public void notifyChanges(HashMap<String, String> users, int visualize) throws RemoteException {
        if(visualize == 1) {
            System.out.printf("\n%-20s %-30s\n", "Username", "|Stato");
            for (Map.Entry<String, String> entry : users.entrySet())
                System.out.printf("%-20s %-30s\n", entry.getKey(), "|" + entry.getValue());
        }

        /* Update local copy of users list*/
        local_users = users;
    }

    /**
     * Show users lists
     * @param online 1, shows only online users
     *               0, otherwise
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
