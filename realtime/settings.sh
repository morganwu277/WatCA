#!/bin/bash

# local cassandra current version path, for database version update
ScriptHostCassandraPath='/Users/morganwu/Developer/workspace/WatCA/WatCA/realtime/cassandra'
#local WatCA Project directory
ScriptHostWatcaPath='/Users/morganwu/Developer/workspace/WatCA/WatCA'

# remote host server user
RemoteUser="ec2-user"
export AWS_SSH_KEY="private_aws_key/Amy_Morgan_aws.pem"

# local WatCA Server
ServerIP=localhost
WebPort=12346
# local port to receive remote YCSB output
ServerLogPort=12345


# remote WatCA Server root directory, mainly for remote shell scripts and gen_file config_update
WatcaPath="/home/$RemoteUser/WatCA"

# RemoteCassandraPath
CassandraPath="/home/$RemoteUser/cassandra"
# remote cassandra, for clear data
CassandraDataPath="/var/lib/cassandra"

# remote YCSB Path
YCSBPath="/home/$RemoteUser/ycsb"

alias ssh="ssh -i $AWS_SSH_KEY"
alias scp="scp -i $AWS_SSH_KEY"

