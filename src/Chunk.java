import java.io.Serializable;

public class Chunk implements Serializable {

    private int chunkNumber, rep_degree;
    private String fileId;
    private byte[] body;
    private int length;

    public Chunk(String fileId, Integer chunkNumber, Integer rep_degree, byte[] body, int length){
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.rep_degree = rep_degree;
        this.body = body;
        this.length = length;
    }

    public int getLength() {
        return length;
    }

    public String getFileId() {
        return fileId;
    }

    public byte[] getBody() {
        return body;
    }

    public Integer getChunkNumber() {
        return chunkNumber;
    }

    public Integer getRep_degree() {
        return rep_degree;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public void setRep_degree(int rep_degree){
        this.rep_degree = rep_degree;
    }

}
