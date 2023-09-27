#!/bin/bash
cd .. || exit
cd .. || exit
cd src || exit
cd build || exit
java TestApp peer1 RECLAIM 0
java TestApp peer3 RECLAIM 200
java TestApp peer4 RECLAIM 300