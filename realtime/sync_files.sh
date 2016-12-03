#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${DIR}

source settings.sh

rsync_rsh="ssh -i $AWS_SSH_KEY"

arg1=$1

if [ -z $ScriptHostWatcaPath ];then
    echo ERROR ScriptHostWatcaPath empty.
    exit 1;
fi

seq=0
for host in `cat servers_public servers_public_ycsb`
do
    echo "sync files for ${host}"
    if [ "$arg1" == "init" ];then # if update Cassandra Version, this need to be execute, actually just execute the soft link of current-version
        mkdir -p gen_file
        mkdir -p STATE
        echo "Sync $WatcaPath"
        rsync -acvz --rsh="$rsync_rsh" --exclude-from=EXCLUDE_FILES $ScriptHostWatcaPath/ $RemoteUser@$host:$WatcaPath
        echo "Sync $CassandraPath"
        rsync -acvz --rsh="$rsync_rsh" $ScriptHostCassandraPath/ $RemoteUser@$host:$CassandraPath
    else
        genFilePath=$ScriptHostWatcaPath"/realtime/gen_file"
        echo $host > $genFilePath"/myIP" # get my current IP
        echo $seq > $genFilePath"/mySeq" # seems no use
        rsync -acvz --rsh="$rsync_rsh" --exclude-from=EXCLUDE_FILES $genFilePath/ $RemoteUser@$host:$WatcaPath"/realtime/gen_file"
    fi

    seq=$((seq + 1))
    echo Finish sync file for this host.
done


