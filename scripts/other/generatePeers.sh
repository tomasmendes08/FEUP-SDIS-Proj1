#!/bin/bash
cd .. || exit
cd .. || exit
cd src || exit
mkdir -p peer_logs
cd build || exit
java Peer 1.0 peer1 21 225.0.0.1 8001 225.0.0.2 8002 225.0.0.3 8003 > ../peer_logs/0_peer1.txt &
java Peer 1.0 peer2 22 225.0.0.1 8001 225.0.0.2 8002 225.0.0.3 8003 > ../peer_logs/0_peer2.txt &
java Peer 1.0 peer3 23 225.0.0.1 8001 225.0.0.2 8002 225.0.0.3 8003 > ../peer_logs/0_peer3.txt &
java Peer 1.0 peer4 24 225.0.0.1 8001 225.0.0.2 8002 225.0.0.3 8003 > ../peer_logs/0_peer4.txt &
java Peer 1.0 peer5 25 225.0.0.1 8001 225.0.0.2 8002 225.0.0.3 8003 > ../peer_logs/0_peer5.txt &
