# WORTH

**WORkTogetHer (WORTH)​** is a project management tool inspired by the principles of the Kanban methodology. Users can join a project, create new tasks, or work on existing tasks (represented as cards). Each task progresses through a series of lists: it starts in the "todo" list, moves to the "inprogress" list when a user takes responsibility for it, then goes to the "toberevised" list when the task is completed and needs to be reviewed, and finally is moved to the "done" list once the review is complete. Project members can communicate with each other using the project chat.

Assignment description is located at **ProgettoWORTH.pdf**.

The complete project report is located at **ProjectReport.pdf**

## Architecture

The architecture is based on a client-server model. The server is single-threaded and uses a selector to multiplex channels, providing a more lightweight and scalable solution compared to a multithreaded approach. Client registration is handled via an RMI (Remote Method Invocation) mechanism. Once a new user is registered, all registered users are notified through a callback that updates the user list. Client requests to create a new project, join an existing project, or perform any project-related operations are sent to the server over a TCP connection. To ensure data persistence, any modifications to elements are saved to disk. When the server restarts, it restores the previous state, allowing clients to continue working on their projects seamlessly.

## Compile & Execute

To compile the source code, go inside the *src* folder:

<pre><code>chmod +x compile.sh</code></pre>
<pre><code>./compile.sh</code></pre>
   
After compiling, you can *execute* the client:
<pre><code>java Client.ClientMain localhost 4382</code></pre>
and the server:
<pre><code>java -cp :../json-simple-1.1.jar Server.ServerMain 4382</code></pre>
where **localhost** define the server's IP which you can connect to, and **4382** the port.