import java.io.IOException;

public class SendGetchunk  implements Runnable{
    private byte[] messageByteArray;

    public SendGetchunk(byte[] messageByteArray){
        this.messageByteArray = messageByteArray;
    }

    @Override
    public void run() {
        System.out.println("Sending GETCHUNK...\n");
        try {
            Peer.getMulticastControl().sendMessage(this.messageByteArray);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
