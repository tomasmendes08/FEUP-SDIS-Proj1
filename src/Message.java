
public class Message {

    private String version, fileId, messageType;
    private byte[] body;
    private Integer senderId, chunkNumber, rep_degree;
    private String CRLF = "\r\n";

    public Message(String version, String messageType, int senderId, String fileId, Integer chunkNumber, Integer rep_degree, byte[] body){
        this.version = version;
        this.messageType = messageType;
        this.senderId = senderId;
        this.fileId = fileId;

        this.chunkNumber = chunkNumber;

        this.rep_degree = rep_degree;
        this.body = body;
    }

    public byte[] buildMessage()  {
        String message;
        if(this.rep_degree != null)
            message = version + " " + messageType + " " + senderId + " " + fileId + " " + chunkNumber + " " + rep_degree + " " + CRLF + CRLF;
        else if(this.chunkNumber != null)
            message = version + " " + messageType + " " + senderId + " " + fileId + " " + chunkNumber + " " + CRLF + CRLF;
        else if(this.fileId != null)
            message = version + " " + messageType + " " + senderId + " " + fileId + " " + CRLF + CRLF;
        else
            message = version + " " + messageType + " " + senderId + " " + CRLF + CRLF;


        byte[] messageByteArrayHeader = message.getBytes();
        //concatenate header and body
        if(body != null){
            byte[] messageByteArray = new byte[messageByteArrayHeader.length + body.length];
            System.arraycopy(messageByteArrayHeader, 0, messageByteArray, 0, messageByteArrayHeader.length);
            System.arraycopy(body, 0, messageByteArray, messageByteArrayHeader.length, body.length);
            return messageByteArray;
        }
        else return messageByteArrayHeader;
    }


}
