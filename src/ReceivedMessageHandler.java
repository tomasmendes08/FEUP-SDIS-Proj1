import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ReceivedMessageHandler implements Runnable{
    private byte[] message;
    private String[] messageHeader;
    private byte[] body;

    public ReceivedMessageHandler(byte[] message){
        this.message = message;
        handleReceivedMessage();
    }

    //handling received message, spliting header and body
    public void handleReceivedMessage(){
        String messageAux = new String(this.message);
        String CRLF_2 = "\r\n\r\n";
        //split by crlf
        String[] messageAuxArray = messageAux.split(CRLF_2, 2);
        String headerAux = messageAuxArray[0].trim();
        this.messageHeader = headerAux.split(" ");
        int headerSpace = headerAux.length() + 5;

        //alloc space for body
        this.body = new byte[this.message.length - headerSpace];
        System.arraycopy(this.message, headerSpace, this.body, 0, this.body.length);

    }

    @Override
    public void run() {
        int senderId = Integer.parseInt(this.messageHeader[2]);

        //senderID won't handle his own messages
        if(senderId != Peer.getPeerID()){
            switch (this.messageHeader[1]) {
                case "PUTCHUNK":
                    try {

                        String chunkID = this.messageHeader[3] + this.messageHeader[4];
                        int desired_rep_degree = Integer.parseInt(this.messageHeader[5]);
                        int randomNum = ThreadLocalRandom.current().nextInt(0, 401);
                        Thread.sleep(randomNum);
                        int perceived_rep_degree = Peer.getStorage().getChunkPercRepDegree().getOrDefault(chunkID, 0);

                        //enhancement protocol
                        if(perceived_rep_degree >= desired_rep_degree && Peer.getProtocol_version().equals("1.1") && this.messageHeader[0].equals("1.1")){
                            return;
                        }
                        handlePUTCHUNK();
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                case "STORED":

                    handleSTORED();

                    break;
                case "DELETE":
                    try {
                        handleDELETE();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "GETCHUNK":
                    try {
                        handleGETCHUNK();
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                    break;

                case "CHUNK":

                    try {
                        handleCHUNK();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    break;


                case "REMOVED":
                    try {
                        handleREMOVED(this.messageHeader);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "READY":
                    try {
                        if(Peer.getProtocol_version().equals("1.1"))
                            handleREADY(this.messageHeader);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + this.messageHeader[1]);
            }
        }

    }

    private void handleREADY(String[] messageHeader) throws IOException {

        //responding to wake up
        for(int i = 0; i < Peer.getStorage().getFilesDeleted().size(); i++){
            Message deleteMessage = new Message(messageHeader[0], "DELETE", Peer.getPeerID(), Peer.getStorage().getFilesDeleted().get(i), null, null, null);
            byte[] deleteByteArray = deleteMessage.buildMessage();
            System.out.println("Sending DELETE...");
            Peer.getMulticastControl().sendMessage(deleteByteArray);
        }

    }

    private void handleCHUNK() throws IOException {

        String fileID = this.messageHeader[3];
        int chunkNumber = Integer.parseInt(this.messageHeader[4]);
        String chunkID = fileID + chunkNumber;

        //if chunk was already restored by this peer, return
        if(Peer.getStorage().getChunksRestored().contains(chunkID)) return;
        Peer.getStorage().getChunksRestored().add(chunkID);

        for(int i = 0; i < Peer.getStorage().getFileList().size(); i++){
            //check if fileID matches a previously backed up file
            if(Peer.getStorage().getFileList().get(i).getFileId().equals(fileID)){

                String mainDir = "chunks/peer" + Peer.getPeerID();
                File newMainDir = new File(mainDir);
                newMainDir.mkdir();

                String dir = mainDir + "/restore";
                File newDir = new File(dir);
                newDir.mkdir();

                String filePath = newDir.getAbsolutePath() + "/" + Peer.getStorage().getFileList().get(i).getFileId() + Peer.getStorage().getFileList().get(i).getFilepath();
                File newFile = new File(filePath);
                newFile.createNewFile();

                RandomAccessFile newRAF = new RandomAccessFile(filePath, "rw");


                newRAF.seek(chunkNumber * 64000L);
                newRAF.write(this.body);
                newRAF.close();

            }
        }

    }

    private void handleGETCHUNK() throws IOException, InterruptedException {
        String fileID = this.messageHeader[3];
        int chunkNumber = Integer.parseInt(this.messageHeader[4]);
        String chunkID = fileID + chunkNumber;


        for(int i = 0; i < Peer.getStorage().getChunksStored().size(); i++){
            //check if the chunk received matches any of the chunks stored in this peer
            if(Peer.getStorage().getChunksStored().get(i).getFileId().equals(fileID) && Peer.getStorage().getChunksStored().get(i).getChunkNumber().equals(chunkNumber)){
                byte[] content = new byte[64500];
                String filePath = "chunks/peer" + Peer.getPeerID() + "/" + fileID + chunkNumber;
                FileInputStream fileInputStream = new FileInputStream(filePath);



                int randomNum = ThreadLocalRandom.current().nextInt(0, 401);
                Thread.sleep(randomNum);

                //If the current chunk is added to the synchronized list before that time expires, it will not send the CHUNK message.
                if(Peer.getStorage().getChunksRestored().contains(chunkID)){
                    fileInputStream.close();
                    return;
                }

                //building CHUNK message
                Peer.getStorage().getChunksRestored().add(chunkID);
                int bytesRead = fileInputStream.read(content);
                byte[] contentShrink = Arrays.copyOf(content, bytesRead);
                Message chunkMessage = new Message(this.messageHeader[0], "CHUNK", Peer.getPeerID(), fileID, chunkNumber, null, contentShrink);
                byte[] chunkMessageByteArray = chunkMessage.buildMessage();
                fileInputStream.close();
                System.out.println("Sending CHUNK...\n");
                Peer.getMulticastRecover().sendMessage(chunkMessageByteArray);


            }
        }

    }

    private void handleDELETE() throws IOException {
        String fileID = this.messageHeader[3];

        for(int i = 0; i < Peer.getStorage().getChunksStored().size(); i++) {
            //check if any of the chunks that were stored match the fileID that was received in the message header
            String aux = Peer.getStorage().getChunksStored().get(i).getFileId();
            if (aux.equals(fileID)) {
                String filePath = "chunks/peer" + Peer.getPeerID() + "/" + aux + Peer.getStorage().getChunksStored().get(i).getChunkNumber();
                Path path = Paths.get(filePath);

                //delete chunk file
                boolean res = Files.deleteIfExists(path);
                if (res) {
                    System.out.println(filePath + " is deleted!");
                } else {
                    System.out.println("Sorry, unable to delete " + filePath);
                }
                String chunkID = aux + Peer.getStorage().getChunksStored().get(i).getChunkNumber();

                //remove from concurrent hash map
                if(Peer.getStorage().getChunkPercRepDegree().containsKey(chunkID)){
                    Peer.getStorage().getChunkPercRepDegree().remove(chunkID);
                }

                //remove from chunks stored
                Peer.getStorage().getChunksStored().remove(i);
                i--;
            }
        }

        return;
    }


    private void handleREMOVED(String[] header) throws Exception {

        //Update PercRepDegree
        String chunkID = header[3] + header[4];
        int numStores = Peer.getStorage().getChunkPercRepDegree().get(chunkID);
        Peer.getStorage().getChunkPercRepDegree().put(chunkID, numStores - 1);

        List<Chunk> chunks = Peer.getStorage().getChunksStored();
        boolean flag = false;

        //verification of chunks backed up
        for(int i = 0; i< chunks.size(); i++){
            if(chunks.get(i).getFileId().equals(header[3]) && chunks.get(i).getChunkNumber() == Integer.parseInt(header[4])){
                flag = true;
                int randomNum = ThreadLocalRandom.current().nextInt(0, 401);
                Thread.sleep(randomNum);
                if(Peer.getStorage().getChunkPercRepDegree().get(chunkID) < chunks.get(i).getRep_degree() ){
                    //read chunk content
                    byte[] content = new byte[64500];
                    String filepath = "chunks/peer" + Peer.getPeerID() + "/" + chunks.get(i).getFileId() + chunks.get(i).getChunkNumber();
                    FileInputStream inputStream = new FileInputStream(filepath);
                    int bytesRead = inputStream.read(content);
                    byte[] contentShrink = Arrays.copyOf(content, bytesRead);

                    //sending putchunk message
                    Message newPutchunk = new Message(Peer.getProtocol_version(), "PUTCHUNK", Peer.getPeerID(), chunks.get(i).getFileId(), chunks.get(i).getChunkNumber(), chunks.get(i).getRep_degree(), contentShrink);
                    byte[] messageByteArray = newPutchunk.buildMessage();

                    SendPutchunk sendPutchunk = new SendPutchunk(messageByteArray, chunks.get(i).getFileId(), chunks.get(i).getChunkNumber(), chunks.get(i).getRep_degree());
                    Peer.getScheduledThreadPoolExecutor().execute(sendPutchunk);
                }
            }
        }

        if(!flag){
            System.out.println("Peer" + Peer.getPeerID() + " doesn't have chunks for " + header[3] + " or already has this file.\n");
        }


    }

    private void handleSTORED() {

        String fileId = this.messageHeader[3];
        int chunkNumber = Integer.parseInt(this.messageHeader[4]);

        String key = fileId + chunkNumber;
        int numStores = 0;

        //update concurrent hash map
        if(Peer.getStorage().getChunkPercRepDegree().containsKey(key)) {
            numStores = Peer.getStorage().getChunkPercRepDegree().get(key);
        }

        Peer.getStorage().getChunkPercRepDegree().put(key, numStores + 1);

    }

    private void sendStored(int senderId) throws IOException {
        //build stored message
        Message messageStored = new Message(this.messageHeader[0], "STORED", senderId, this.messageHeader[3], Integer.parseInt(this.messageHeader[4]),null, null);
        byte[] messageStoredByteArray = messageStored.buildMessage();
        System.out.println("Sending STORED...\n");
        String key = this.messageHeader[3] + this.messageHeader[4];
        int numStores = 0;

        //update concurrent hash map
        if(Peer.getStorage().getChunkPercRepDegree().containsKey(key)) {
            numStores = Peer.getStorage().getChunkPercRepDegree().get(key);
        }

        Peer.getStorage().getChunkPercRepDegree().put(key, numStores + 1);
        Peer.getMulticastControl().sendMessage(messageStoredByteArray);
    }

    public void handlePUTCHUNK() throws IOException, InterruptedException {

        String fileId = this.messageHeader[3];
        int chunkNumber = Integer.parseInt(this.messageHeader[4]);
        int rep_degree = Integer.parseInt(this.messageHeader[5]);
        boolean storeChunk = true;


        //check if chunk was already stored in this peer
        for(int i = 0; i < Peer.getStorage().getChunksStored().size(); i++){
            if(Peer.getStorage().getChunksStored().get(i).getFileId().equals(fileId) && Peer.getStorage().getChunksStored().get(i).getChunkNumber().equals(chunkNumber)){
                storeChunk = false;
                break;
            }
        }

        //check if chunk belongs to a file that this peer has already backed up
        if(storeChunk){
            for(int i = 0; i < Peer.getStorage().getFileList().size(); i++){
                if (Peer.getStorage().getFileList().get(i).getFileId().equals(fileId)) {
                    storeChunk = false;
                    break;
                }
            }
        }


        if(storeChunk){
            //check if space available is enough
            if(Peer.getStorage().getTakenSpace() + this.body.length <= Peer.getStorage().getAvailableSpace()){


                //new main directory
                String mainDir = "chunks";
                File newMainDir = new File(mainDir);
                newMainDir.mkdir();

                //new directory for peer
                String dir = "chunks/peer" + Peer.getPeerID();
                File newDir = new File(dir);
                newDir.mkdir();

                //new file
                String fileName = newDir.getAbsolutePath() + "/" + fileId + chunkNumber;
                File newFile = new File(fileName);
                newFile.createNewFile();

                //writing to the new file
                OutputStream outputStream = new FileOutputStream(newFile);

                outputStream.write(this.body);
                outputStream.close();

                //add chunk to synchronized list
                Chunk chunk = new Chunk(fileId, chunkNumber, rep_degree,null, this.body.length);
                Peer.getStorage().getChunksStored().add(chunk);
                sendStored(Peer.getPeerID());
            }
        }

    }
}
