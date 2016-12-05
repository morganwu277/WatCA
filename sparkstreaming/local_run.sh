#!/usr/bin/env bash

JARS=""
for i in `ls dependency`; do
    JARS+="hdfs:/user/ec2-user/dependency/$i,"
done

spark-submit --class ca.uwaterloo.watca.Runner \
    --master local[4] \
    --jars ${JARS} WatCA-1.0-SNAPSHOT.jar \
    ip-172-31-24-67:9092,ip-172-31-24-68:9092,ip-172-31-24-69:9092 test3
