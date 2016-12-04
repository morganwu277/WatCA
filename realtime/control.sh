#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${DIR}

source settings.sh
source gen_file/config.update

## execute command on servers of servers_public
run_on_servers() {
    cmd=$1
    signature=$2
    waitFinish=$3 # wait for script return, 0: don't need to wait, e.g. long-live service process; 1: need to wait,e.g. short-time running process

    if [ $signature == "loadYCSB" ] || [ $signature == "workYCSB" ]; then
        tunnelopts="-R $ServerLogPort:$ServerIP:$ServerLogPort"
    else
        tunnelopts=""
    fi
    #echo "Tunnel options: $tunnelopts"

    for host in `cat servers_public`
    do
        touch STATE/${host}"."${signature}".ing"
        (ssh -ax $tunnelopts -i $AWS_SSH_KEY $RemoteUser@${host} "${cmd}" 1> "STATE/${host}.${signature}.out"  \
                                                                               2> "STATE/${host}.${signature}.err"; \
                                                                               rm "STATE/${host}.${signature}.ing") &
    done

    if [ $waitFinish == 1 ];then
        remain=`ls STATE/*.${signature}.ing 2>/dev/null | wc -l`
        while [ $remain != 0 ]; do
            if [ $remain == 1 ]; then
                echo "waiting for ${remain} host"
            else
                echo "waiting for ${remain} hosts"
            fi
            sleep 3
            remain=`ls STATE/*.${signature}.ing 2>/dev/null | wc -l`
        done
    fi
}

control_kernel_net_delay() {
    # from gen_file/config.update
    delay=$kernel_net_delay
    if [ $delay == 0 ];then
        echo "Resetting simulated network delay"
        cmd="sudo tc qdisc del dev \`ifconfig | grep Ethernet | cut -d' ' -f1\` root"
    else
        echo "Setting simulated network delay to ${delay}ms"
        cmd="sudo tc qdisc del dev \`ifconfig | grep Ethernet | cut -d' ' -f1\` root; sudo tc qdisc add dev \`ifconfig | grep Ethernet | cut -d' ' -f1\` root netem delay "${delay}"ms"
    fi
    echo $cmd

    run_on_servers "${cmd}" "LinuxDelay" 1
    echo "====Kernel network delay setting done!.===="
}

update_storage_type() {
    type=$storage_type
    # depends on cassandra installed path and version.
    if [ "$type" == "Cassandra2_0" ];then
        cd $ScriptHostCassandraPath
        rm current-version
        ln -s apache-cassandra-2.0.17 current-version
    elif [ "$type" == "Cassandra2_2" ];then
        cd $ScriptHostCassandraPath
        rm current-version
        ln -s apache-cassandra-2.2.8 current-version
    else
        echo "Error DB type: "$type
    fi
}

kill_db() {
    shellPath=$WatcaPath/realtime
    cmd="cd ${shellPath} && bash control_stub.sh kill"
    echo $cmd

    run_on_servers "${cmd}" "KillDB" 1
    echo "NoSQL DB servers stopped."
}

start_DB() {
    echo "Begin to start NoSQL DB servers"
    shellPath=$WatcaPath/realtime
    cmd="cd ${shellPath} && bash control_stub.sh start"
    echo $cmd

    # the last parameter is not 1, because server will continue running
    run_on_servers "${cmd}" "startDB" 0

    # init schema on the seed node, i.e., the first cassandra node.
    seed=`head -n 1 servers_public`
    ssh -ax -i $AWS_SSH_KEY $RemoteUser@${seed} "cd ${shellPath} && bash control_stub.sh init_schema"

    echo "NoSQL DB server started."
}

load_YCSB() {

    echo "Generate partition workload files"
    serverNum=`cat servers_public | wc -l`
#    countPerHost=`echo ${keyspace} ${serverNum} | awk '{printf "%d", $1 / $2}'`
    countPerHost=${keyspace}
    update_prop=`echo 1 ${read_prop} | awk '{printf "%f", $1 - $2}'`

    start=0
    mySeq=0
    for public_ip in `cat servers_public`
    do
        host=`cat servers_public_private | grep $public_ip | awk '{print $2}'`
        sed -e s/RECORDCOUNT_Placeholder/${keyspace}/ \
            -e s/READPROPORTION_Placeholder/${read_prop}/ \
            -e s/UPDATEPROPORTION_Placeholder/${update_prop}/ \
            -e s/REQUESTDISTRIBUTION_Placeholder/${dist}/ \
            -e s/HOTSPOTDATAFRACTION_Placeholder/${hotspotdatafraction}/ \
            -e s/INSERTSTART_Placeholder/${start}/ \
            -e s/INSERTCOUNT_Placeholder/${countPerHost}/ \
            -e s/SEQ_Placeholder/${mySeq}/ \
            my_workload.template > gen_file/workload".${host}"
#        start=`expr $start + $countPerHost`
        let mySeq=$mySeq+1
    done

    echo "Sync files to servers"
    bash sync_files.sh

    echo "Run load phase of YCSB at servers"
    shellPath=$WatcaPath/realtime
    cmd="cd ${shellPath} && bash control_stub.sh load"
    echo $cmd

    run_on_servers "${cmd}" "loadYCSB" 1
    echo "Done YCSB load."

}

work_YCSB() {
    echo "Generate partion workload files"
    serverNum=`cat servers_public | wc -l`
    countPerHost=`echo ${keyspace} ${serverNum} | awk '{printf "%d", $1 / $2}'`
    update_prop=`echo 1 ${read_prop} | awk '{printf "%f", $1 - $2}'`

    start=0
    mySeq=0
    for public_ip in `cat servers_public`
    do
        host=`cat servers_public_private | grep $public_ip | awk '{print $2}'`
        sed -e s/RECORDCOUNT_Placeholder/${keyspace}/ \
            -e s/READPROPORTION_Placeholder/${read_prop}/ \
            -e s/UPDATEPROPORTION_Placeholder/${update_prop}/ \
            -e s/REQUESTDISTRIBUTION_Placeholder/${dist}/ \
            -e s/HOTSPOTDATAFRACTION_Placeholder/${hotspotdatafraction}/ \
            -e s/INSERTSTART_Placeholder/${start}/ \
            -e s/INSERTCOUNT_Placeholder/${countPerHost}/ \
            -e s/SEQ_Placeholder/${mySeq}/ \
            my_workload.template > gen_file/workload".${host}"
        start=`expr $start + $countPerHost`
        let mySeq=$mySeq+1
    done

    echo "Sync files to servers"
    bash sync_files.sh

    echo "Run work phase of YCSB at servers"
    shellPath=$WatcaPath/realtime
    cmd="cd ${shellPath} && bash control_stub.sh workYcsb"
    echo $cmd

    run_on_servers "${cmd}" "workYCSB" 1
    echo "Done YCSB work phase."
}

case $1 in
    kernel_net_delay)
        control_kernel_net_delay
        ;;
    start_db)
        kill_db
        bash sync_files.sh
        start_DB
        ;;
    kill_db)
        kill_db
        ;;
    load_ycsb)
        load_YCSB
        ;;
    work_ycsb)
        work_YCSB
        ;;
    storage_type)
        update_storage_type
        cd ${DIR}
        bash sync_files.sh init
        ;;
    *)
        echo default
esac

exit 0;

