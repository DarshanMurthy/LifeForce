#!/bin/bash
# setup the python directory

basedir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
pydir=${basedir}

if [ -f ${pydir}/comm_pb2.py ]; then
rm ${pydir}/comm_pb2.py
fi

#PROTOHOME=/usr/local/protobuf-2.5.0/bin
#PROTOHOME=/usr/local/Cellar/protobuf241/2.4.1/bin
PROTOHOME=/Users/harleenkaur/build-protobuf/protobuf/bin

$PROTOHOME/protoc -I=${basedir} --python_out=${pydir} ${basedir}/comm.proto

# this places the comm_pb2.py file in a subdirectory of python
