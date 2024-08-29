package Commons;

import Server.DBMS;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

public class RMIRegistrationImpl extends RemoteObject implements RMIRegistrationInterface {
    private static RMIRegistrationImpl serverRMI;
    private final DBMS dbms;

    private RMIRegistrationImpl() throws RemoteException {
        super();
        dbms = DBMS.getInstance();
    }

    public static RMIRegistrationImpl getServerRMI() throws RemoteException {
        if(serverRMI==null) serverRMI = new RMIRegistrationImpl();
        return serverRMI;
    }

    /**
     * Register user to the system by checking username and password
     * @param nick username
     * @param pwd password
     * @return "200 OK" for success, an error message otherwise
     * @throws RemoteException remote method error
     */
    @Override
    public String register(String nick, String pwd) throws RemoteException {
        if(nick == null || nick.equals("") || nick.contains(" ")) return "Invalid nickname";
        if(pwd ==  null || pwd.equals("")) return "Invalid password";
        if(pwd.length()<5) return "Password too short. Minimum five characters";
        if(pwd.length()>20) return "Password too long. Maximum twenty characters";
        if(dbms.existUser(nick)) return "Username already exists";
        if(dbms.registerUser(nick, pwd)) return "200 OK";

        return "Error";
    }
}
