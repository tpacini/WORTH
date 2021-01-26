package Client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;

/* Interfaccia remota del client che implementa il metodo (notifyChanges)
*  utilizzato dal server per notificare un evento (al client) */
public interface ClientNotifyInterface extends Remote {

    void notifyChanges(HashMap<String, String> users, int visualize) throws RemoteException;

    void getLocalUserList(int online) throws RemoteException;
}
