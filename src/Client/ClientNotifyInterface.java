package Client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;

/* Client remote interface, implementing the method (notifyChanges) used by
 * the server to notify an event to the client */
public interface ClientNotifyInterface extends Remote {
    
    void notifyChanges(HashMap<String, String> users, int visualize) throws RemoteException;

    void getLocalUserList(int online) throws RemoteException;
}
