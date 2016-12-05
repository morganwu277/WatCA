#!/usr/bin/env bash

set -e

cd ..
mvn clean package
mvn dependency:copy-dependencies

ssh_identity="/Users/morganwu/Downloads/Amy_Morgan_aws.pem"
rsync_rsh="ssh -i $ssh_identity"
rsync -acvz --progress --rsh="$rsync_rsh" target/dependency ec2-user@ip-172-31-24-68:~
rsync -acvz --progress --rsh="$rsync_rsh" target/WatCA-1.0-SNAPSHOT.jar ec2-user@ip-172-31-24-68:~

#ssh -i $ssh_identity ec2-user@ip-172-31-24-68 "bash ~/run.sh" > /dev/null &

