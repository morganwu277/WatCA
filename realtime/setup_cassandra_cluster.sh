#!/bin/bash

USR=ec2-user

echo Make sure your servers_public file is populated with IPs!

for S in `cat servers_public`
do
    echo Setting up server $S
    scp -i $AWS_SSH_KEY setup_cassandra.sh $USR@$S:~
    ssh -i $AWS_SSH_KEY $USR@$S "bash setup_cassandra.sh"
done
