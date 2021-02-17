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
     * Restituisce lo stato del ripristino delle registrazioni, che può
     * essere positivo (["OK"] or ["CREATE"]) o negativo (["ERROR"])
     * @return stringa rappresentante lo stato
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
     * Esegue un controllo sull'esistenza di un certo nickname
     * @param nickname nome dell'utente
     * @return true se il nome utente è già associato ad un account,
     * false altrimenti
     */
    public synchronized boolean existUser(String nickname) {
        return credentials.containsKey(nickname);
    }

    /**
     * Registra l'utente se possibile e aggiorna il file sul disco
     * @param nickname nome dell'utente
     * @param pass password dell'utente
     * @return true se la registrazione è avvenuta con successo,
     * false altrimenti
     */
    public synchronized boolean registerUser(String nickname, String pass) {
        if(credentials.put(nickname, pass) == null) {
            try {
                local_ref.update(null, nickname);
            } catch (IOException e) { System.out.println("Impossibile aggiungere utente."); }
            updateRegistrations();
            return true;
        }

        return false;
    }

    /**
     * Ripristina le registrazioni salvate sul disco
     * @return "[OK]" se il ripristino è avvenuto con successo,
     * "[CREATE]" se non c'era nessuno stato da ripristinare, "[ERROR] altrimenti
     */
    private static String restoreRegistrations() {
        File dir = new File(MAIN_PATH);
        File[] files = dir.listFiles();

        /* Controllo se la cartella esiste altrimenti non c'è nulla da recuperare */
        if (!dir.exists() && dir.mkdir())
            return "[CREATE]";
        else if (!dir.isDirectory())
            return "[ERROR]";
        /* Se la directory esiste ma non ci sono file all'interno */
        else if (files != null && files.length == 0)
            return "[OK]";
        else { /* Se esiste inizio il recupero */
            /* Recupera le credenziali degli utenti */
            String fileN = MAIN_PATH + "/registrations.json";
            try {
                Object obj = new JSONParser().parse(new FileReader(fileN));
                JSONObject jo = (JSONObject) obj;

                /* Salva i dati degli utenti sulle relative strutture dati */
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

    /**
     * Aggiorna il file sul disco riguardante le registrazioni
     */
    @SuppressWarnings("unchecked")
    private static void updateRegistrations() {
        String path = MAIN_PATH+"/registrations.json";
        File registrations = new File(path);
        /* Controlla se il file esiste altrimenti lo crea */
        if (registrations.exists()) {
            if (!registrations.delete()) {
                System.out.println("[ERROR] Impossibile aggiornare le registrazioni");
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
                    System.out.println("[UPDATE] Registrazioni aggiornate.");
                } catch (FileNotFoundException e) {
                    System.out.println("[ERROR] Invalid path: " + path);
                }
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Impossibile aggiornare le registrazioni");
        }
    }
}
