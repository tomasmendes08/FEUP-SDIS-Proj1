import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SendPutchunk implements Runnable{
    private byte[] messageByteArray;
    private int num_tries = 0;
    private String fileId;
    private int chunkNumber, desiredRepDegree, time = 1000;

    public SendPutchunk(byte[] messageByteArray, String fileId, int chunkNumber, int desiredRepDegree){
        this.messageByteArray = messageByteArray;
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.desiredRepDegree = desiredRepDegree;
    }

    @Override
    public void run() {
        try {
            System.out.println("Sending PUTCHUNK...\n");
            Peer.getMulticastBackup().sendMessage(this.messageByteArray); //sending PUTCHUNK

        } catch (IOException e) {
            e.printStackTrace();
        }

        String key = this.fileId + this.chunkNumber;

        try {
            Thread.sleep(this.time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int numStores = Peer.getStorage().getChunkPercRepDegree().getOrDefault(key, 0);

        System.out.println("Tries: " + this.num_tries);

        //the initiator will send at most 5 PUTCHUNK messages per chunk
        if(numStores < this.desiredRepDegree && this.num_tries < 5){
            //double time
            this.time *= 2;
            this.num_tries++;
            Peer.getScheduledThreadPoolExecutor().execute(this);
        }

    }
}
