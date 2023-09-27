import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;

public class Storage implements Serializable {

    private long availableSpace;
    private ConcurrentHashMap<String, Integer> chunkPercRepDegree = new ConcurrentHashMap<>(); //chunkID (key) and his perceived replication degree (value)
    private List<Chunk> chunksStored = Collections.synchronizedList(new ArrayList<>()); //chunks stored in a certain peer
    private List<FileInformation> fileList = new ArrayList<>(); //list of files that were backed up
    private List<String> chunksRestored = Collections.synchronizedList(new ArrayList<>()); //chunks that were restored
    private List<String> filesDeleted = new ArrayList<>(); //list of files that were deleted

    public Storage(long availableSpace){
        this.availableSpace = availableSpace;
    }

    public List<String> getChunksRestored() {
        return chunksRestored;
    }

    public List<FileInformation> getFileList() {
        return fileList;
    }

    public List<Chunk> getChunksStored() {
        return chunksStored;
    }

    public List<String> getFilesDeleted() {
        return filesDeleted;
    }

    public ConcurrentHashMap<String, Integer> getChunkPercRepDegree() {
        return chunkPercRepDegree;
    }

    //check chunks stored space occupied
    public long getTakenSpace(){
        long space = 0;
        for(Chunk chunk : this.chunksStored){
            space += chunk.getLength();
        }
        return space;
    }

    public long getAvailableSpace(){
        return this.availableSpace;
    }

    public void setAvailableSpace(long availableSpace) {
        this.availableSpace = availableSpace;
    }

    // SERIALIZATION
    // Fonte: https://www.tutorialspoint.com/java/java_serialization.htm
    public void serializeStorageInformation() throws IOException {
        //new main directory
        String mainDir = "chunks";
        File newMainDir = new File(mainDir);
        newMainDir.mkdir();


        //new directory for peer
        String dir1 = newMainDir.getAbsolutePath() + "/peer" + Peer.getPeerID();
        File newDir1 = new File(dir1);
        newDir1.mkdir();


        //storage directory
        String dir2 = newDir1.getAbsolutePath() + "/storage";
        File newDir2 = new File(dir2);
        newDir2.mkdir();


        //new file

        String file = newDir2.getAbsolutePath() + "/storage_information";
        File newFile = new File(file);
        newFile.createNewFile();



        try {
            FileOutputStream fileOutputStream = new FileOutputStream(newFile);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(this);
            objectOutputStream.close();
            fileOutputStream.close();
            System.out.println("Serialized data is saved in " + newFile.getCanonicalPath());
        } catch (IOException i) {
            i.printStackTrace();
        }

    }

    // Fonte: https://www.tutorialspoint.com/java/java_serialization.htm
    public void deserializeStorageInformation(){
        String filepath = "chunks/peer" + Peer.getPeerID() + "/storage/storage_information";
        File storageInfo = new File(filepath);
        if(!storageInfo.exists() || !storageInfo.isFile()) {
            System.out.println("File doesn't exist or it is not a file");
            return;
        }


        try {
            Storage storage = new Storage((long) (64 * Math.pow(10, 9)));
            FileInputStream fileInputStream = new FileInputStream(storageInfo);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            storage = (Storage) objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
            Peer.setStorage(storage);
        } catch (IOException i) {
            i.printStackTrace();
            return;
        } catch (ClassNotFoundException c) {
            System.out.println("Storage class not found");
            c.printStackTrace();
            return;
        }
    }

}
