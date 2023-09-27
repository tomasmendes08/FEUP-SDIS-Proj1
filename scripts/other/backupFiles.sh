#!/bin/bash
cd .. || exit
cd .. || exit
cd src || exit
cd build
java TestApp peer1 BACKUP ../teste/tomcruise.jpg 1
java TestApp peer2 BACKUP ../teste/city.jpg 4
