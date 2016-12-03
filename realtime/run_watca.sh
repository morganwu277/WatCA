#!/bin/bash
set -x 
set -e 

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "${DIR}"

source settings.sh

cd "$DIR"/..

#bash build.sh
if [ "$1" == "build" ]; then
    mvn clean package
    cd "$DIR"/ycsb_wrapper
    javac -cp .:${DIR}/../lib/* ca/uwaterloo/watca/YCSBConnectorWrapper.java
fi

cd "$DIR"
# this line is only needed in the first time of running to send files.
echo "Make sure the file servers_public have changed with the host ip addresses"
rm -f STATE/*
rm -f gen_file/*
bash sync_files.sh init


# This file grows without bound!
rm -f scores.txt
export CLASSPATH=../target/classes/
# TODO Hua args "localhost 12347 javaControl" is meaning less, for historical reason, will be cleaned up
java ca.uwaterloo.watca.RealtimeMain $ServerLogPort $WebPort localhost 12347 javaControl
