#!/bin/bash
cd .. || exit
cd .. || exit
cd src || exit
cd build || exit
java TestApp peer1 STATE
java TestApp peer2 STATE
java TestApp peer3 STATE
java TestApp peer4 STATE
java TestApp peer5 STATE