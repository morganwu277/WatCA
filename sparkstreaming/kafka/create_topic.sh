#!/usr/bin/env bash

ansible hadoop[2] -m shell -a 'kafka-topics --zookeeper localhost:2181 --create --topic test3 --replication-factor 2 --partitions 24'
ansible hadoop[2] -m shell -a 'kafka-topics --zookeeper localhost:2181 --list'
ansible hadoop[2] -m shell -a 'kafka-topics --zookeeper localhost:2181 --describe --topic test3'
ansible hadoop -m shell -a 'df -h' -s

