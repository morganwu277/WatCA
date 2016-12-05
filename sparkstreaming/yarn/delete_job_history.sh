#!/usr/bin/env bash

ansible hadoop[2] -m shell -a 'sudo -u hdfs hdfs dfs -rm -r -f -skipTrash /user/ec2-user/.sparkStaging/application_*'
ansible hadoop[2] -m shell -a 'sudo -u hdfs hdfs dfs -rm -r -f -skipTrash /user/spark/applicationHistory/application_*'
ansible hadoop[2] -m shell -a 'sudo -u hdfs hdfs dfs -rm -r -f -skipTrash /tmp/logs/ec2-user/logs/application_*'
ansible hadoop[2] -m shell -a  'sudo -u hdfs hdfs dfs -expunge'
ansible hadoop[2] -m shell -a 'sudo -u hdfs hdfs dfs -du -h /' ## should be round 1.4GB
ansible hadoop -m shell -a 'df -h' -s

