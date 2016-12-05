#!/usr/bin/env bash

set -e

ssh_identity="/Users/morganwu/Downloads/Amy_Morgan_aws.pem"
rsync_rsh="ssh -i $ssh_identity"
rsync -acvz --progress --rsh="$rsync_rsh" --exclude-from=EXCLUDE_FILES .. ec2-user@ip-172-31-24-68:/home/ec2-user/WatCA
