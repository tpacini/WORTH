package Commons;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RMIRegistrationInterface extends Remote {
    int PORT = 8000;
    String REMOTE_OBJECT_NAME = "Register";

    String register(String nick, String pass) throws RemoteException;
}
