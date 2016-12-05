#!/usr/bin/env bash

# see how many executors are in the background
ansible hadoop[0:2] -m shell -a 'ps -ef|grep org.apache.spark.executor.CoarseGrainedExecutorBackend' |grep -v grep |grep -v bash | wc -l

