#!/bin/bash

USR=ec2-user
rm -f servers_public*
rm -f servers_public_private*

echo
echo Note: configure your AWS_ACCESS_KEY and AWS_SECRET_KEY environment variables first!
echo

echo
echo Reading list of EC2 regions for ec2_regions
echo

for REGION in `cat ec2_regions`
do
  echo Region: $REGION
  export EC2_URL=https://ec2.${REGION}.amazonaws.com
  ## Cassandra
  aws ec2 describe-instances \
    --query 'Reservations[*].Instances[*].[Placement.AvailabilityZone, State.Name, InstanceId, PublicIpAddress, PrivateIpAddress, Placement.GroupName]' --output text \
   | grep 'running' | grep 'cassandra' | cut -d$'\t' -f4 > servers_public
  echo Found Cassandra Public IP `cat servers_public`
  aws ec2 describe-instances \
    --query 'Reservations[*].Instances[*].[Placement.AvailabilityZone, State.Name, InstanceId, PublicIpAddress, PrivateIpAddress, Placement.GroupName]' --output text \
   | grep 'running' | grep 'cassandra' | awk '{print $4" "$5}' > servers_public_private

  ## YCSB
  aws ec2 describe-instances \
    --query 'Reservations[*].Instances[*].[Placement.AvailabilityZone, State.Name, InstanceId, PublicIpAddress, PrivateIpAddress, Placement.GroupName]' --output text \
   | grep 'running' | grep 'ycsb' | cut -d$'\t' -f4 > servers_public_ycsb
  echo Found YCSB Public IP `cat servers_public_ycsb`
  aws ec2 describe-instances \
    --query 'Reservations[*].Instances[*].[Placement.AvailabilityZone, State.Name, InstanceId, PublicIpAddress, PrivateIpAddress, Placement.GroupName]' --output text \
   | grep 'running' | grep 'ycsb' | awk '{print $4" "$5}' > servers_public_private_ycsb
done

echo
echo Wrote public and private IP addresses to servers_public and servers_public_private
cat servers_public_private
