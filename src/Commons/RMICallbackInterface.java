package Commons;

import Client.ClientNotifyInterface;

import java.rmi.RemoteException;
import java.rmi.Remote;

public interface RMICallbackInterface extends Remote {
    int PORT = 5000;
    String REMOTE_OBJECT_NAME = "CallbackServer";

    void registerForCallback (ClientNotifyInterface clientInterface) throws RemoteException;

    void unregisterForCallback (ClientNotifyInterface clientInterface) throws RemoteException;

}
