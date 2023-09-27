import java.rmi.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestApp {

    private static String peer_ap;
    private static String sub_protocol;
    private static String filePath;
    private static int rep_degree;
    private static long space;


    public static void main(String[] args) throws UnknownHostException {

        if(args.length != 2 &&  args.length != 3 && args.length != 4){
            System.out.println("Usage: java TestApp <peer_ap> <sub_protocol> <opnd_1> <opnd_2>");
            return;
        }

        peer_ap = args[0];
        sub_protocol = args[1];

        if(args.length > 2){
                filePath = args[2];
             if (args.length == 4){
                rep_degree = Integer.parseInt(args[3]);
            }
        }

        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            RMInterface peer_init = (RMInterface) registry.lookup(peer_ap);

            System.out.println("Peer AP: "+ peer_ap + "\n");
            System.out.println("SubProtocol: " + sub_protocol + "\n");

            if(args[1].equals("BACKUP")){
                peer_init.backup_protocol(filePath, rep_degree);
            }
            else if(args[1].equals("STATE")){
                peer_init.state();
            }
            else if(args[1].equals("RESTORE")){
                peer_init.restore_protocol(filePath);
            }
            else if(args[1].equals("DELETE")){
                peer_init.delete_protocol(filePath);
            }
            else if(args[1].equals("RECLAIM")){
                space = Long.parseLong(args[2]);
                peer_init.reclaim_protocol(space);
            }
            else {
                System.out.println(args[1] + " wrong!\n");
                System.out.println("Usage: java TestApp <peer_ap> <sub_protocol> <opnd_1> <opnd_2>");
            }

        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }

    }

}
