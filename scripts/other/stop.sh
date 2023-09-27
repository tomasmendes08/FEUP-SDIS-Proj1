#!/bin/bash
cd ..
cd .. || exit
cd src || exit
rm -r peer_logs
cd build
rm -r chunks
killall rmiregistry
