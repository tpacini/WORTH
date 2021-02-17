# WORTH

Progetto del corso Reti di Calcolatori e Laboratorio a.a. 2020/21.

Il progetto consiste nell’implementazione di ​ **WORkTogetHer** (**WORTH**)​ : uno strumento per la gestione di progetti collaborativi che si ispira ad alcuni principi della metodologia Kanban.

Descrizione dell'assignment: **ProgettoWORTH.pdf**

Relazione riguardante la soluzione proposta: **Relazione.pdf**

## Client

*ClientMain.java:* client main class that receive the user's requests and display the server response.

*MulticastInfos.java*: auxiliary class which contains useful data about the multicast socket used to communicate with the project's chat.

*ClientNotifyInterface.java*: interface which contains the method used by the server to update a resource via RMI Callback.

*ClientNotifyImpl.java*

## Commons

*RMICallbackInterface.java*: interface which contains the methods used by the clients to register/unregister themself to the RMI Callbacks.

*RMICallbackImpl.java*

*RMIRegistrationInterface.java*: interface which contains the method used by the clients to register a user via RMI.

*RMIRegistrationImpl.java*

## Server

*ServerMain.java*: server main class that receive and process the client's requests via TCP.

*Card.java*: a class which describes the card's properties and implements methods to handle them.

*Project.java*: a class which describes the project's properties and implements methods to handle them.

*DBMS.java*: a class which implements the methods used by the RMI registration system to register the users and other useful methods.

*AdvKey.java*

## Compile & Execute

Go inside the *src* folder, then to *compile*:

<pre><code>chmod +x compile.sh</code></pre>
<pre><code>./compile.sh</code></pre>
   
After compiling, you can *execute* the client :
<pre><code>java Client.ClientMain localhost 4382</code></pre>
and the server:
<pre><code>java -cp :../json-simple-1.1.jar Server.ServerMain 4382</code></pre>
In the client side, **localhost** define the server's IP which you can connect to, and **4382** the port.