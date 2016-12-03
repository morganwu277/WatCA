#!/bin/bash

# Cassandra version.
CVER=2.2.8

# install java 8
java -version > /dev/null 2>&1 || sudo yum install java-1.8.0-openjdk.x86_64 java-1.8.0-openjdk-devel.x86_64 -y
# test
java -version
sudo yum -y install wget
sudo yum -y install ntp
sudo systemctl enable ntpd
sudo systemctl start ntpd

# install python and cassandra driver
sudo yum install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
sudo yum install -y python-pip
sudo pip install cqlsh

# stop cassandra if running
cassandra_pid=$(ps auwx | grep cassandra | grep -v grep | awk {'print $2'})
[ "$cassandra_pid" != "" ] && kill $cassandra_pid

# install cassandra
mkdir -p ~/cassandra
cd  ~/cassandra
if [ ! -e apache-cassandra-$CVER-bin.tar.gz ]; then
    wget http://www.eu.apache.org/dist/cassandra/$CVER/apache-cassandra-$CVER-bin.tar.gz
    tar -xzvf apache-cassandra-$CVER-bin.tar.gz
fi
[ ! -e /var/lib/cassandra ] && sudo mkdir /var/lib/cassandra
[ ! -e /var/log/cassandra ] && sudo mkdir /var/log/cassandra
sudo chown -R $USER:$GROUP /var/lib/cassandra
sudo chown -R $USER:$GROUP /var/log/cassandra
cd ~/cassandra
[ -h current-version ] && unlink current-version && rm -f current-version
ln -s apache-cassandra-$CVER current-version

export CASSANDRA_HOME=~/cassandra/current-version
export PATH=$PATH:$CASSANDRA_HOME/bin

# start
sh current-version/bin/cassandra

echo "Here is Cassandra Package: "
ls -al ~/cassandra
