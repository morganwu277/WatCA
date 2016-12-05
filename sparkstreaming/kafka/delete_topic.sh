#!/usr/bin/env bash

ansible hadoop[2] -m shell -a "kafka-topics --zookeeper localhost:2181 --delete --topic test3"
ansible hadoop -m shell -a 'rm -rf /var/local/kafka/data/test3-*'
ansible hadoop[2] -m shell -a '/usr/bin/zookeeper-client rmr /consumers/test/offsets/test3'
ansible hadoop -m shell -a 'du -sh /var/local/kafka/data'

