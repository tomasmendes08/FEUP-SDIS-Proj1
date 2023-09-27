import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;

public interface RMInterface extends Remote {

    void backup_protocol(String file, int rep_degree) throws RemoteException;
    void state() throws IOException;
    void restore_protocol(String file) throws IOException;
    void delete_protocol(String file) throws IOException, InterruptedException, NoSuchAlgorithmException;
    void reclaim_protocol(long space) throws IOException;
}
