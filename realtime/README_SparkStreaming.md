# Real-time consistency analysis using WatCA

**Instructions for EC2, RedHat 7.3 Server, Cassandra 2.2.8, YCSB 0.10.0**

Step 0: SSH Login without password

- Place your AWS Key Pem file in the `./private_aws_key` directory, and update the `AWS_SSH_KEY` of `settings.sh` file. 

Step 1: Launch instances

- Launch RedHat 7.3 Server (64-bit) Cassandras in one or more geographical regions.
- Use the security group in EC2 that allow all traffic. (TODO: need to be change in step 2) 

Step 2: Prepare security group (TODO) 

- Manually edit the default security group, adding an inbound rule that allows unrestricted traffic from the same group.
- Remove all other inbound rules that may have been added by the scripts in prior runs.

Step 3: Obtain list of host IPs

- Configure your AWS_ACCESS_KEY and AWS_SECRET_KEY environment variables first, make sure `awscli` works as expected.
- Write your EC2 region in the `ec2_regions` file
- Execute `configure_ips_ec2.sh` to determine public and private IPs of your VMs, this will generate public and private IPs of Cassandra and YCSB
- Alternatively, populate the `servers_public` and `servers_public_private` (Cassandras) files and `servers_public_ycsb` and `servers_public_private_ycsb` files manually.

Step 4: Storage system setup

- Run the `setup_cassandra_cluster.sh` script for Cassandra Cluster.
- Alternatively, at each host clone the git repository and then execute `setup_ubuntu.sh` to download and install Cassandra.
- Most of the installation is non-interactive, but be prepared to accept the Oracle Java license agreement at each host.
- Double-check that Cassandra got installed.  Update the `CVER` variable in `setup_cassandra.sh` if you get an HTTP 404 response when downloading Cassandra.

Step 5: YCSB setup

- Run the `setup_ycsb.sh` script for YCSB Server. 

Step 6: Configure tool

- Edit `settings.sh`.
- Override the first two settings, try to use defaults for the others.

Step 7: Launch tool

- Run `run_watca.sh`, and wait a few seconds for the tool to initialize before opening a browser to connect to the web interface.
- By default, the web interface binds to port 12346.
