# Distributed Backup Service
## Introduction
This project was developed with the intent of developing a distributed backup service for a local area network (LAN). The idea is to use the free disk space of the computers in a LAN for backing up files in other computers in the same LAN. The service is provided by servers in an environment that is assumed cooperative (rather than hostile). Nevertheless, each server retains control over its own disks and, if needed, may reclaim the space it made available for backing up other computers' files.

Because no server is special, we call these servers "peers". (This kind of implementation is often called serverless service.) Each peer is identified by an integer, which is unique among the set of peers in the system and never changes.

The servers are called **peers**, each peer is identified by an integer, which is unique among the set of peers in the system and never changes. Users can request the service to perform certain tasks throught the use of a User Interface, in this case the UI can be used though the java class TestApp.

![](https://i.imgur.com/c5GQyk8.png)


## Files
The use of the distributed backup system means that the **files** processed in the system need to be able to be divided into a fragment of information called **chunks**.
Each chunk contains, besides the information of the parent file a replication degree that descrives the number of times that same chunk is stored somewhere in the system.

![](https://i.imgur.com/HRaarXz.png)

Each File contains:
* **fileId** - a string that identifies the file
* **filepath** - path to the file
* **chunkList** - a list of every chunk belonging to that file

Chunks on their end contain:
* **chunkNumber** - integer that identifies the chunk
* **rep_degree** - the replication degree of the chunk
* **fileId** - connects the chunk to the parent file
* **body** - information inside the chunk
* **length** - size of the chunk

## Service Interface and Peer Protocols
The UI described earlier represents the Service Interface that allows users to send requests to their peer. The Peer protocol used by the backup service comprises several smaller subprotocols, which are used for specific tasks, and that can be run concurrently.

![](https://i.imgur.com/4hPtwV9.png)

### Thread Scheduling
As each peer protocol can require a large number of threads the choice was made to use ThreadPoolExecuters instead of ThreadSleeps to prevent the coexistence of a large number of threads running at the same time.
A ThreadPoolExecutor that can schedule commands to run after a given delay, or to execute periodically. This class is preferable to Timer when multiple worker threads are needed, or when the additional flexibility or capabilities of ThreadPoolExecutor (which this class extends) are required.
Delayed tasks execute no sooner than they are enabled, but without any real-time guarantees about when, after they are enabled, they will commence. Tasks scheduled for exactly the same execution time are enabled in first-in-first-out (FIFO) order of submission.

![](https://i.imgur.com/mbytvtH.png)

### Messaging
We define a generic format for all messages. After that, in the the subsections with the description of the different subprotocols, we specify the format of each message of the respective subprotocol by specifying the fields that must be present. When a field is used in a message, it must be encoded as described herein.

The generic message is composed by two parts: a header and the body. The header contains essentially control information, whereas the body is used for the data and is used in some messages only.

#### Header
The message header has the following non-empty single line:
````
<Version> <MessageType> <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF>
`````
Where:
* **Version** - This is the version of the protocol. It is a three ASCII char sequence with the format n.m, where n and m are the ASCII codes of digits.
* **MessageType** - This is the type of the message. Each subprotocol specifies its own message types. This field determines the format of the message and what actions its receivers should perform. This is encoded as a variable length sequence of ASCII characters.
* **SenderId** - This is the id of the peer that has sent the message. This field is useful in many subprotocols. This is encoded as a variable length sequence of ASCII digits.
* **FileId** - This is the file identifier in the backup service.
* **ChunkNumber** - This field together with the FileId specifies a chunk in the file.
* **ReplicationDeg** - This field contains the desired replication degree of the chunk. This is a digit, thus allowing a replication degree of up to 9. It takes one byte, which is the ASCII code of that digit.

#### Body
When present, the body contains the data of a file chunk. The length of the body is variable. If it is smaller than the maximum chunk size, 64KByte, it is the last chunk in a file. The protocol does not interpret the contents of the Body. For the protocol its value is just a byte sequence.

### Multicast Control Channel
A Multicast Control Channel is a point-to-multipoint downlink channel used for transmitting multimedia broadcast multicast service, control information from the network to the UE, for one or several multicast traffic channels. (P J Marnick BSc (Hons) CEng MIEE, R G Russell BSc (Hons) CEng MIEE, in Telecommunications Engineer's Reference Book, 1993)

In our service each peer when created joins a multicast group.

![](https://i.imgur.com/cTqCE8Y.png)

### Chunk backup subprotocol
To backup a chunk, the initiator-peer sends to the MDB multicast data channel a message whose body is the contents of that chunk. This message includes also the sender and the chunk ids and the desired replication degree:
```
<Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
```
A peer that stores the chunk upon receiving the PUTCHUNK message, should reply by sending on the multicast control channel (MC) a confirmation message with the following format:
````
<Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
````
The peer that initiates the backup doesn't store the chunks of the file he requested the backup off.
Each chunk is saved the number of times described in the replication degree parameter.

#### Enhancement
One problem of having the PUTCHUNK messages spread in a Multicast Channel is that sometimes several peers could end up backing up the same chunk at the same time ending up with a greater Replication Degree tham the one described in the ReplicationDeg field of the message.
To prevent this problem a key was created using a ConcurrentHashMap that associates a chunk with it's current Replication Degree. This key was named chunkPercRepDegree.
![](https://i.imgur.com/j7C83CK.png)

This chunkPercRepDegree is them compared with the Desired Replication Degree described in the message and while the Perceived RD is smaller tham the Desired RD the service will store the chunks in different peers.
![](https://i.imgur.com/IOO6PYf.png)

### Chunk restore protocol
To recover a chunk, the initiator-peer shall send a message with the following format:
````
<Version> GETCHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
````
Upon receiving this message, a peer that has a copy of the specified chunk shall send it in the body of a CHUNK message via the MDR channel:

````
<Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
````


### File deletion subprotocol

In order to allow the deletion all chunks of a file from the backup service, the protocol provides the following message:
````
<Version> DELETE <SenderId> <FileId> <CRLF><CRLF>
````
Upon receiving this message, each peer removes from its backing store all chunks belonging to the specified file.

#### Enhancement
During the running time of the system a peer that had backed up some chunks of the file might not be not running at the time the initiator peer sends a DELETE message for that file. This type of event is not reliably preventable and as such a way of correcting the errors that can come from this event was created.

To start with the Java Serializable Interface was implemented in the protocol version 1.1 allowing us to Serialize and Deserialize.
![](https://i.imgur.com/zBpghhq.png)
Serialization allows us to convert the state of an object into a byte stream, which then can be saved into a file on the local disk or sent over the network to any other machine. And deserialization allows us to reverse the process, which means reconverting the serialized byte stream to an object again.
![](https://i.imgur.com/6cg28xh.png)
With this even when a peer shuts down, once restarting all the information will return to it working as a small waking up instead of a full reboot.

Upon waking up the peer with the protocol version 1.1 warns every other peer that he is back on. Peers will then send back information of all deletions that happend in the meanwhile, saved in a list in storage (filesDeleted).
![](https://i.imgur.com/mI2kUKt.png)

If our peer has the chunk referd in the message saved up it will procede to delete it.
![](https://i.imgur.com/YbH17fa.png)

In case a new backup is made of a file previously deleted, this deletion will be removed from the filesDeleted list and as such the peer wont delete them after waking up.

![](https://i.imgur.com/OLfiwLC.png)

### Space reclaiming subprotocol
A user might want to redefign the space a peer has access to. In the beginning this space is not limited.
Once reclaiming the space 3 things might happen:
* the user might choose a negative space coded so that it means that the space continues/becomes unlimited once more
* the space left is more tham enouth to accommodate every chunk currently in the peer, this updates the available space but nothing happens to the chunks
* there isn't enouth space for all chunks in the peer

In case of the third option the peer begins erasing chunks from the biggest to the smallest(minimizing the amount of chunks deleted) and sends the following message:
```
<Version> REMOVED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
```
Once receiving this message other peers check to see if that chunks' replication degree is currently smaller tham expected (other peers might have backed the chunk in the meanwhile). In case a new backup is needed and provided enouth space is available that peer will them save a copy of that chunk updating the replication degree to the desired value.


## Instructions
There are 2 ways of running the service, one is running all the commands by hand on the command line and the other is by running the scripts in the proj1/src/scripts folder. This scripts are a sort of example of what can be donne and for furder use it is recommended that a custon scrip is created using the same formula as in the other scripts.

### Command line option

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