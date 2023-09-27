import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class FileInformation implements Serializable {

    private List<Chunk> chunkList;
    private String fileId;
    private String filepath;

    public FileInformation(String filepath, String fileId, List<Chunk> chunkList){
        this.fileId = fileId;
        this.chunkList = chunkList;
        this.filepath = filepath;
    }

    public String getFilepath() {
        return filepath;
    }

    public List<Chunk> getChunkList() {
        return chunkList;
    }

    public String getFileId() {
        return fileId;
    }
}
