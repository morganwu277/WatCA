#!/usr/bin/env bash

# first we got data.log from YCSB side, i.e., /tmp/not_need_log_file.log

/opt/cloudera/parcels/KAFKA/bin/kafka-console-producer \
    --broker-list ip-172-31-24-67:9092,ip-172-31-24-68:9092,ip-172-31-24-69:9092 \
    --topic test3 < ~/data.log
