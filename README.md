# Distributed Backup Service

To use the developed service a user should folow the following steps:
1. Download the files to your computer
2. Travel to the project source folder:
```
cd ../sdis/proj1/src
```
3. Compile the .java files inside source:
```
javac -d build *.java
````
4. Start RMI inside the build folder

For windows:
`````
cd build
start rmiregistry
`````
For others:
``````
cd build
rmiregistry
``````

5. Create the peers
````
java Peer <protocol_version> <peer_name> <peer_id> <MulticastControl_ip> <MulticastControl_port> <MulticastBackup_ip> <MulticastBackup_port> <MulticastRecover_ip> <MulticastRecover_port> 
````
Ex.:
````
java Peer 1.0 peer1 21 225.0.0.1 8001 225.0.0.2 8002 225.0.0.3 8003
`````
6. Run a Service Interface Subprotocol
`````
java TestApp <peer_ap> <sub_protocol> <opnd_1> <opnd_2>
``````
Where:
* **peer_ap** - Is the peer's access point, the peer_name.
* **sub_protocol** - Is the operation the peer of the backup service must execute. It can be either the triggering of the subprotocol to test, or the retrieval of the peer's internal state. In the first case it must be one of: BACKUP, RESTORE, DELETE, RECLAIM. To retrieve the internal state, the value of this argument must be STATE
* **opnd_1** - Is either the path name of the file to backup, the name of the file to restore/delete, for the respective 3 subprotocols, or, in the case of RECLAIM the maximum amount of disk space (in KByte) that the service can use to store the chunks. The STATE operation takes no operands.
* **opnd_2** - This operand is an integer that specifies the desired replication degree and applies only to the backup protocol (or its enhancement)

Ex.:
````
java TestApp peer1 BACKUP ../teste/tomcruise.jpg 1
java TestApp peer1 RESTORE tomcruise.jpg
java TestApp peer1 DELETE tomcruise.jpg
java TestApp peer1 RECLAIM 2
````
7. Kill the rmiregistry
````
killall rmiregistry
````

### Script Option
1. Download the files to your computer
2. Go to the ../proj1/src/scripts folder
3. Run the compile.sh script inside the source folder
4. Run the rmiregistry command inside the build folder
5. Run the peer.sh script inside the build folder
6. Run the test.sh script inside the build folder
7. To finish run the cleanup.sh script inside the build folder

If a user wishes to stop rmiregistry after finishing using the service run the command
```
killall rmiregistry
````