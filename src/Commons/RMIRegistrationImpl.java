package Commons;

import Server.DBMS;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

public class RMIRegistrationImpl extends RemoteObject implements RMIRegistrationInterface {
    private static RMIRegistrationImpl serverRMI;
    private DBMS dbms;

    private RMIRegistrationImpl() throws RemoteException {
        super();
        dbms = DBMS.getInstance();
    }

    public static RMIRegistrationImpl getServerRMI() throws RemoteException {
        if(serverRMI==null) serverRMI = new RMIRegistrationImpl();
        return serverRMI;
    }

    @Override
    public String register(String nick, String pwd) throws RemoteException {
        if(nick == null ||
                nick.equals("") ||
                nick.contains(" ")) return "Nickname non valido";
        if(pwd ==  null || pwd.equals("")) return "Password non valida";
        if(pwd.length()<5) return "Password troppo corta. Minimo 5 caratteri";
        if(pwd.length()>20) return "Password troppo lunga. Massimo 20 caratteri";
        if(dbms.existUser(nick)) return "Nickname già esistente";
        if(dbms.registerUser(nick, pwd)) return "200 OK";

        return "Errore";
    }
}
