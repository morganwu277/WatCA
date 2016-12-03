#!/bin/bash

# install java 8
java -version > /dev/null 2>&1 || sudo yum install java-1.8.0-openjdk.x86_64 java-1.8.0-openjdk-devel.x86_64 -y
# test
java -version
sudo yum -y install wget
sudo yum -y install ntp
sudo systemctl enable ntpd
sudo systemctl start ntpd

#YCSB VERSION
YCSBVER=0.10.0

# install YCSB
mkdir -p ~/ycsb
cd  ~/ycsb
if [ ! -e ycsb-$YCSBVER.tar.gz ]; then
    curl -O --location https://github.com/brianfrankcooper/YCSB/releases/download/$YCSBVER/ycsb-$YCSBVER.tar.gz
    tar xfvz ycsb-$YCSBVER.tar.gz
fi
[ -h current-version ] && unlink current-version && rm -f current-version
ln -s ycsb-$YCSBVER current-version

echo "Here is YCSB Package: "
ls -al ~/ycsb
