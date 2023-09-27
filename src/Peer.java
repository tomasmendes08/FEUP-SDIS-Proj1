import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLOutput;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.Iterator;


public class Peer implements RMInterface {

    private static String protocol_version;
    private static String peerName;
    private static Integer peerID;
    private static MulticastControl multicastControl;
    private static MulticastBackup multicastBackup;
    private static MulticastRecover multicastRecover;
    private static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static Storage storage;

    public static Storage getStorage() {
        return storage;
    }

    public static String getProtocol_version() {
        return protocol_version;
    }

    public static String getPeerName() {
        return peerName;
    }

    public static MulticastBackup getMulticastBackup() {
        return multicastBackup;
    }

    public static MulticastControl getMulticastControl() {
        return multicastControl;
    }

    public static MulticastRecover getMulticastRecover() {
        return multicastRecover;
    }

    public static Integer getPeerID() {
        return peerID;
    }

    public static ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor() {
        return scheduledThreadPoolExecutor;
    }

    public static void setStorage(Storage storage) {
        Peer.storage = storage;
    }

    public Peer(String[] args) throws IOException {
        protocol_version = args[0];
        peerName = args[1];
        peerID = Integer.parseInt(args[2]);
        storage = new Storage((long) (64 * Math.pow(10, 9)));

        //multithread scheduling
        scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(64);

        multicastControl = new MulticastControl(InetAddress.getByName(args[3]), Integer.parseInt(args[4]));
        multicastBackup = new MulticastBackup(InetAddress.getByName(args[5]), Integer.parseInt(args[6]));
        multicastRecover = new MulticastRecover(InetAddress.getByName(args[7]), Integer.parseInt(args[8]));

        Peer.getStorage().deserializeStorageInformation();

        //Fonte: https://docs.oracle.com/javase/1.5.0/docs/api/java/lang/Runtime.html#addShutdownHook%28java.lang.Thread%29
        //serialize on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Peer.getStorage().serializeStorageInformation();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        System.out.println(protocol_version + " " + peerName + " " + peerID + " \n" );
    }


    public static void main(String[] args) {

        if(args.length != 9){
            System.out.println("Usage:\tjava Peer <protocol_version> <peerName> <peerID> <multicastControl_ip> <multicastControl_port> <multicastBackup_ip> <multicastBackup_port> <multicastRecover_ip> <multicastRecover_port> \n");
            return;
        }

        try {
            Peer peer_ap = new Peer(args);
            //execute scheduledThreadPool
            scheduledThreadPoolExecutor.execute(multicastBackup);
            scheduledThreadPoolExecutor.execute(multicastControl);
            scheduledThreadPoolExecutor.execute(multicastRecover);

            RMInterface stub = (RMInterface) UnicastRemoteObject.exportObject(peer_ap, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(peerName, stub);

            //delete enhancement
            if(protocol_version.equals("1.1")){
                Message peerReadyMessage = new Message(protocol_version, "READY", peerID, null, null, null, null);
                byte[] peerReadyByteArray = peerReadyMessage.buildMessage();
                System.out.println("Peer" + peerID + " is READY.");
                multicastControl.sendMessage(peerReadyByteArray);
            }

        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    //Fonte: https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
    public static String byteArrayToHex(byte[] byteArray){
        char[] hexChars = new char[byteArray.length * 2];
        for(int i = 0; i < byteArray.length; i++){
            int aux = byteArray[i] & 0xFF;
            hexChars[i*2] = hexArray[aux >>> 4];
            hexChars[i * 2 + 1] = hexArray[aux & 0x0F];
        }

        return new String(hexChars);
    }

    public String generateFileID(String file) throws IOException, NoSuchAlgorithmException {
        Path path = Paths.get(file);
        //metadata
        FileOwnerAttributeView fileOwnerAttributeView = Files.getFileAttributeView(path,
                FileOwnerAttributeView.class);
        UserPrincipal fileOwner = fileOwnerAttributeView.getOwner(); //file owner
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        FileTime lastTime = attributes.lastModifiedTime(); //last modified time
        String auxFileId = file + lastTime + fileOwner.getName();

            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashEnc = messageDigest.digest(auxFileId.getBytes("UTF-8"));

            String fileId = byteArrayToHex(hashEnc);

        return fileId;
    }

    @Override
    public void backup_protocol(String file, int rep_degree) throws RemoteException {
        System.out.println("starting Backup Protocol\n");

        try {

            //fileId
            String fileId = generateFileID(file);

            //if file was previously deleted, remove from filesDeleted list
            if(Peer.getStorage().getFilesDeleted().contains(fileId)) Peer.getStorage().getFilesDeleted().remove(fileId);

            System.out.println("File ID: " + fileId + "\n");

            List<Chunk> chunkList = new ArrayList<>();

            Path path = Paths.get(file);
            boolean lastChunk = false;
            byte[] content = new byte[64000];
            int chunkCounter = 0, bytesRead;
            Chunk chunk;
            FileInputStream inputStream = new FileInputStream(file);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

            //read file in chunks
            while ((bytesRead = bufferedInputStream.read(content)) != -1){
                if(bytesRead % 64000 == 0){
                    chunk = new Chunk(fileId, chunkCounter, rep_degree, content, bytesRead);
                    chunkList.add(chunk);
                }
                else if(bytesRead % 64000 != 0){  //if last chunk not divisible by 64000
                    lastChunk = true;
                    byte[] lastContent = new byte[bytesRead];
                    System.arraycopy(content, 0, lastContent, 0, bytesRead);
                    chunk = new Chunk(fileId, chunkCounter, rep_degree, lastContent, bytesRead);
                    chunkList.add(chunk);
                }

                content = new byte[64000];
                chunkCounter++;
            }

            bufferedInputStream.close();
            
            // if all chunks are divisible by 64000, add 1 with empty body
            if(!lastChunk){
                chunk = new Chunk(fileId, chunkCounter, rep_degree, null, 0);
                chunkList.add(chunk);
            }

            //sending putchunk messages
            for(int i = 0; i < chunkList.size(); i++){
                Message message = new Message(protocol_version, "PUTCHUNK", peerID, fileId, i, rep_degree, chunkList.get(i).getBody());
                byte[] messageByteArray = message.buildMessage();
                chunkList.get(i).setBody(null);
                SendPutchunk sendPutchunk = new SendPutchunk(messageByteArray, fileId, i, rep_degree);
                scheduledThreadPoolExecutor.execute(sendPutchunk);
                Thread.sleep(100);
            }

            FileInformation fileInformation = new FileInformation(path.getFileName().toString(), fileId, chunkList);
            Peer.getStorage().getFileList().add(fileInformation);  // add file to backed up files list
        } catch (IOException | NoSuchAlgorithmException | InterruptedException e) {
            e.printStackTrace();
        }

        return;
    }

    @Override
    public void state() {
        System.out.println("\n----------------  STATE ----------------\n");
        if(!Peer.getStorage().getFileList().isEmpty()){
            System.out.println("\n--------------------------------\n");
            System.out.println("\n***  FILES  ***");
            for(int i = 0; i < Peer.getStorage().getFileList().size(); i++){
                if(Peer.getStorage().getFileList().get(i).getFilepath() != null)
                    System.out.println("\nFile Path: " + Peer.getStorage().getFileList().get(i).getFilepath());
                System.out.println("\nFile ID: " + Peer.getStorage().getFileList().get(i).getFileId());
                System.out.println("\nDesired Replication Degree: " + Peer.getStorage().getFileList().get(i).getChunkList().get(0).getRep_degree());
                System.out.println("\n\t****  FILE CHUNKS  ****");
                for(int j = 0; j < Peer.getStorage().getFileList().get(i).getChunkList().size(); j++){
                    String chunkID = Peer.getStorage().getFileList().get(i).getChunkList().get(j).getFileId() + Peer.getStorage().getFileList().get(i).getChunkList().get(j).getChunkNumber();
                    System.out.println("\n\tChunk ID: " + chunkID);
                    if(Peer.getStorage().getChunkPercRepDegree().containsKey(chunkID))
                        System.out.println("\n\tPerceived Replication Degree: " + Peer.getStorage().getChunkPercRepDegree().get(chunkID));
                    else System.out.println("\n\tPerceived Replication Degree: 0");
                }
                System.out.println("\n\n--------------------------------\n");
            }
        }
        else {
            System.out.println("\nNo files backed up yet :(\n");
            System.out.println("\n--------------------------------\n");
        }

        if(!Peer.getStorage().getChunksStored().isEmpty()) {
            System.out.println("\n--------------------------------\n");
            System.out.println("\n***  CHUNKS STORED  ***");
            for(int i = 0; i < Peer.getStorage().getChunksStored().size(); i++){
                String chunkID = Peer.getStorage().getChunksStored().get(i).getFileId() + Peer.getStorage().getChunksStored().get(i).getChunkNumber();
                System.out.println("\nChunk ID: " + chunkID);
                System.out.println("\nChunk Size: " + Peer.getStorage().getChunksStored().get(i).getLength() / 1000 + " KBytes");
                System.out.println("\nDesired Replication Degree: " + Peer.getStorage().getChunksStored().get(i).getRep_degree());
                if(Peer.getStorage().getChunkPercRepDegree().containsKey(chunkID)){
                    System.out.println("\nPerceived Replication Degree: " + Peer.getStorage().getChunkPercRepDegree().get(chunkID));
                }
                System.out.println("\n\n--------------------------------\n");
            }
        }
        else {
            System.out.println("\nNo chunks stored yet :(\n");
            System.out.println("\n--------------------------------\n");
        }

        System.out.println("\nPeer Storage Capacity: " + Peer.getStorage().getAvailableSpace() + "\n");
        System.out.println("Storage Capacity Used: " + Peer.getStorage().getTakenSpace() + "\n");
        System.out.println("Storage Chunks: " + Peer.getStorage().getChunksStored().size() + "\n");

        System.out.println("\n--------------------------------\n");

    }

    @Override
    public void restore_protocol(String file) throws IOException {
        boolean flag = false;
        for(int i = 0; i < Peer.getStorage().getFileList().size(); i++){
            //check if file was previously backed up
            if(Peer.getStorage().getFileList().get(i).getFilepath().equals(file)){
                flag = true;
                //send getchunk messages
                for(int j = 0; j < Peer.getStorage().getFileList().get(i).getChunkList().size(); j++){
                    Message restoreMessage = new Message(protocol_version, "GETCHUNK", peerID, Peer.getStorage().getFileList().get(i).getFileId(), Peer.getStorage().getFileList().get(i).getChunkList().get(j).getChunkNumber(), null, null);
                    byte[] restoreMessageByteArray = restoreMessage.buildMessage();
                    SendGetchunk sendGetchunk = new SendGetchunk(restoreMessageByteArray);
                    scheduledThreadPoolExecutor.execute(sendGetchunk);
                }
            }
        }

        if(!flag) System.out.println("Didn't find " + file + " in peer" + Peer.getPeerID() + " :(\n");

    }

    @Override
    public void delete_protocol(String file) throws IOException, InterruptedException, NoSuchAlgorithmException {

        boolean flag = false;

        if(!Peer.getStorage().getFileList().isEmpty()){

            for(int i = 0; i < Peer.getStorage().getFileList().size(); i++) {
                //check if file was previously backed up
                if (Peer.getStorage().getFileList().get(i).getFilepath().equals(file)) {
                    //send delete message
                    flag = true;
                    Message deleteMessage = new Message(protocol_version, "DELETE", peerID, Peer.getStorage().getFileList().get(i).getFileId(), null, null, null);
                    byte[] deleteMessageByteArray = deleteMessage.buildMessage();
                    System.out.println("Sending DELETE...");
                    multicastControl.sendMessage(deleteMessageByteArray);
                    Peer.getStorage().getFilesDeleted().add(Peer.getStorage().getFileList().get(i).getFileId());
                    for(int j = 0; j < Peer.getStorage().getFileList().get(i).getChunkList().size(); j++){
                        String chunkID = Peer.getStorage().getFileList().get(i).getChunkList().get(j).getFileId() + Peer.getStorage().getFileList().get(i).getChunkList().get(j).getChunkNumber();
                        //remove chunk from concurrent hash map
                        if(Peer.getStorage().getChunkPercRepDegree().containsKey(chunkID)){
                            Peer.getStorage().getChunkPercRepDegree().remove(chunkID);
                        }
                    }
                    Peer.getStorage().getFileList().remove(i);
                    i--;
                }
            }

            if(!flag) System.out.println("\nPeer" + peerID + " doesn't contain " + file + " :(");
        }
        else System.out.println("\nNo files to delete in peer" + peerID + " :(");


        return;
    }

    @Override
    public void reclaim_protocol(long space) throws IOException {
        //if space given is negative, then space available will be 64GB
        if(space >= 0){
            storage.setAvailableSpace(space); //set new storage space
            while(space < storage.getTakenSpace()){
                Chunk aux = null;
                int auxi = 0;
                //choose chunk with higher length
                for(int i = 0; i < storage.getChunksStored().size(); i++){
                    if(aux == null){
                        aux = storage.getChunksStored().get(i);
                        auxi = i;
                    }
                    else {
                        if(aux.getLength() < storage.getChunksStored().get(i).getLength()) {
                            aux = storage.getChunksStored().get(i);
                            auxi = i;
                        }
                    }
                }
                String chunkID = aux.getFileId() + aux.getChunkNumber();
                String filePath = "chunks/peer" + Peer.getPeerID() + "/" + chunkID;
                helperPrinter(filePath);
                helper(chunkID);
                //send removed message
                Message reclaimMessage = new Message(protocol_version, "REMOVED", peerID, aux.getFileId(), aux.getChunkNumber(), null, null);
                byte[] reclaimMessageByteArray = reclaimMessage.buildMessage();
                System.out.println("Sending REMOVED...");
                multicastControl.sendMessage(reclaimMessageByteArray);

                Peer.getStorage().getChunksStored().remove(auxi);

            }
            System.out.println("Reclaim done\n");
        }
        else storage.setAvailableSpace((long) (64 * Math.pow(10, 9))); //set new storage space
    }

    //delete file
    static void helperPrinter(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        System.out.println(filePath);
        boolean res = Files.deleteIfExists(path);
        if (res) {
            System.out.println(filePath + " is deleted!");
        } else {
            System.out.println("Sorry, unable to delete " + filePath);
        }
    }

    //delete chunk from concurrent hash map
    static void helper(String chunkID) {
        if(Peer.getStorage().getChunkPercRepDegree().containsKey(chunkID)){
            int numStores = Peer.getStorage().getChunkPercRepDegree().get(chunkID);
            Peer.getStorage().getChunkPercRepDegree().put(chunkID, numStores - 1);
            if(numStores == 0){
                Peer.getStorage().getChunkPercRepDegree().remove(chunkID);
            }
        }
    }
}
