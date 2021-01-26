package Client;

import java.net.MulticastSocket;

public class MulticastInfos {
    private final MulticastSocket multiSocket;
    private final String addr;      // indirizzo di multicast
    private final int port;         // porta di multicast

    public MulticastInfos(MulticastSocket multiSocket, String addr, int port) {
        this.multiSocket = multiSocket;
        this.addr = addr;
        this.port = port;
    }

    public MulticastSocket getMultiSocket() { return this.multiSocket; }

    public String getAddr() { return this.addr; }

    public int getPort() { return this.port; }
}
