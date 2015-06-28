#!/bin/bash
# setup the python directory

basedir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
pydir=${basedir}

if [ -f ${pydir}/eye/Comm.java ]; then
rm ${pydir}/eye/Comm.java
fi

#PROTOHOME=/usr/local/protobuf-2.5.0/bin
#PROTOHOME=/usr/local/Cellar/protobuf241/2.4.1/bin
PROTOHOME=/Users/harleenkaur/build-protobuf/protobuf/bin

$PROTOHOME/protoc -I=${basedir} --java_out=${pydir} ${basedir}/comm.proto

# this places the Comm.java file in a subdirectory of python
