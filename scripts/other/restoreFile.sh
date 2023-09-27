#!/bin/bash
cd .. || exit
cd .. || exit
cd src || exit
cd build || exit
java TestApp peer1 RESTORE tomcruise.jpg
java TestApp peer2 RESTORE city.jpg