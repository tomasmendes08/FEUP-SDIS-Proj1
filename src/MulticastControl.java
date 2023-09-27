import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

public class MulticastControl implements Runnable{
    private InetAddress ip;
    private int port;
    private MulticastSocket multicastSocket;

    public MulticastControl(InetAddress ip, int port) throws IOException {
        this.ip = ip;
        this.port = port;
        this.multicastSocket = new MulticastSocket(port);
        this.multicastSocket.joinGroup(ip);
    }

    public int sendMessage(byte[] messageBytes) throws IOException {
        DatagramPacket datagramPacket = new DatagramPacket(messageBytes, messageBytes.length, ip, port);
        multicastSocket.send(datagramPacket);

        return 0;
    }

    @Override
    public void run() {
        byte[] message = new byte[64500];

        try {
            boolean flag = true;

            //always checking if a packet was received
            while(flag){
                DatagramPacket datagramPacket = new DatagramPacket(message, message.length);
                multicastSocket.receive(datagramPacket);

                byte[] messageShrink  = Arrays.copyOf(message, datagramPacket.getLength());

                ReceivedMessageHandler receivedMessageHandler = new ReceivedMessageHandler(messageShrink);
                Peer.getScheduledThreadPoolExecutor().execute(receivedMessageHandler);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
