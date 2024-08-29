package Server;

import Commons.RMICallbackImpl;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.HashMap;

public class DBMS {
    private static String STATE;
    private static DBMS dbms;
    private static final String MAIN_PATH = "../SavedState";
    private static HashMap<String, String> credentials;
    private static RMICallbackImpl local_ref;

    private DBMS() {
        credentials = new HashMap<>();
        STATE = restoreRegistrations();
    }

    /**
     * Return the state of the recovered registrations
     * @return string representing the state
     */
    public String checkState() {
        return STATE;
    }

    public HashMap<String, String> getCredentials() {
        return credentials;
    }

    public String getMainPath() { return MAIN_PATH; }

    public static DBMS getInstance() {
        if(dbms == null) dbms = new DBMS();
        return dbms;
    }

    public static void setLocal_ref(RMICallbackImpl ref) { local_ref = ref; }

    /**
     * Check if a username "nickname" already exists
     * @param nickname username
     * @return true if the username already exists, false otherwise
     */
    public synchronized boolean existUser(String nickname) {
        return credentials.containsKey(nickname);
    }

    /**
     * Register the user
     * @param nickname username
     * @param pass password
     * @return true if the registration has been carried out successfully,
     *              false otherwise
     */
    public synchronized boolean registerUser(String nickname, String pass) {
        if(credentials.put(nickname, pass) == null) {
            try {
                local_ref.update(null, nickname);
            } catch (IOException e) { System.out.println("The user cannot be added."); }
            updateRegistrations();
            return true;
        }

        return false;
    }

    /**
     * Recover the registrations stored on disk
     * @return "[OK]" if recovery is successfully,
     * "[CREATE]" if there was no state to recover, "[ERROR]" otherwise
     */
    private static String restoreRegistrations() {
        File dir = new File(MAIN_PATH);
        File[] files = dir.listFiles();

        if (!dir.exists() && dir.mkdir())
            return "[CREATE]";
        else if (!dir.isDirectory())
            return "[ERROR]";
        else if (files != null && files.length == 0)
            return "[OK]";
        else { 
            String fileN = MAIN_PATH + "/registrations.json";
            try {
                Object obj = new JSONParser().parse(new FileReader(fileN));
                JSONObject jo = (JSONObject) obj;

                /* Recover data from serialized JSON object */
                for (Object user : jo.keySet()) {
                    String nickname = (String) user;
                    String password = (String) jo.get(user);
                    credentials.put(nickname, password);
                }

            } catch (IOException | ParseException e) {
                return "[ERROR]";
            }

            return "[OK]";
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateRegistrations() {
        String path = MAIN_PATH+"/registrations.json";
        File registrations = new File(path);
        if (registrations.exists()) {
            if (!registrations.delete()) {
                System.out.println("[ERROR] Registrations cannot be updated");
            }
        }
        try {
            if (registrations.createNewFile()) {
                try {
                    FileWriter fileReg = new FileWriter(registrations);
                    JSONObject joOut = new JSONObject();

                    for (String user : credentials.keySet())
                        joOut.put(user, credentials.get(user));

                    fileReg.write(joOut.toJSONString());
                    fileReg.close();
                    System.out.println("[UPDATE] Registrations updated.");
                } catch (FileNotFoundException e) {
                    System.out.println("[ERROR] Invalid path: " + path);
                }
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Registrations cannot be updated.");
        }
    }
}
