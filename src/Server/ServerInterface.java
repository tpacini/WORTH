package Server;

import Client.ClientNotifyInterface;

import java.rmi.RemoteException;
import java.rmi.Remote;

/* Interfaccia remota del server che presenta i metodi necessari
*  al client per registrarsi al servizio di notifica */
public interface ServerInterface extends Remote {

    void registerForCallback (ClientNotifyInterface clientInterface) throws RemoteException;

    void unregisterForCallback (ClientNotifyInterface clientInterface) throws RemoteException;

}
