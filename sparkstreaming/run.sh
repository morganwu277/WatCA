#!/usr/bin/env bash

HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-/etc/hadoop/conf}

JARS=""
for i in `ls dependency`; do
    JARS+="hdfs:/user/ec2-user/dependency/$i,"
done

# 当在YARN上运行Spark作业时，每个Spark executor作为一个YARN容器(container)运行。
# Spark可以使得多个Tasks在同一个容器(container)里面运行。
# yarn-cluster和yarn-client模式的区别其实就是Application Master进程的区别
# 在yarn-cluster模式下，driver运行在AM(Application Master)中，它负责向YARN申请资源，并监督作业的运行状况。
###### 当用户提交了作业之后，就可以关掉Client，作业会继续在YARN上运行。
# 然而yarn-cluster模式不适合运行交互类型的作业。 在yarn-client模式下，Application Master仅仅向YARN请求executor，
###### client会和请求的container通信来调度他们工作，也就是说Client不能离开。
# http://www.jianshu.com/p/9b243c0a7410

#1、每个节点可以起一个或多个Executor。一个Executor 就是一个 CoarseGrainedExecutorBackend. 一个 Executor 的Xms和Xmx都为--executor-memory
### 启动Executors: 在启动 Executors 之前，先要通过 yarnAllocator 获取到 numExecutors 个 Container，然后在Container中启动 Executors。
### 启动 Executors 是通过ExecutorRunnable实现的，而ExecutorRunnable内部是启动CoarseGrainedExecutorBackend的
### 如果开启了动态分配机制 spark.dynamicAllocation.enabled = true 和 spark.shuffle.service.enabled = true 则无需设置 --num-executors, 由 core 和 memory自行决定
### 例如: 一个集群有 12 Core + 21GB 内存,请求--executor-cores 1 和 --executor-memory 1G, 会开启12个container, 其中一个container 跑driver,也就是Application Master,其他的跑 executor
######## 每个executor使用 1G, executor 内存总共使用12GB. 但是还有 spark.yarn.executor.memoryOverhead, spark.yarn.driver.memoryOverhead, spark.yarn.am.memoryOverhead 默认都是 384 MB
######## 但是 yarn.scheduler.increment-allocation-mb = 512MB 所以额外需要的384MB当做了512MB, 所以总分配了 1536MB = 1.5GB
######## 所以总共使用内存 12*1.5GB = 18GB
#2、每个Executor由若干core组成，每个Executor的每个core一次只能执行一个Task。
#3、每个Task执行的结果就是生成了目标RDD的一个partition 。
#4、一个 vCore 是 Executor 的工作线程 而非机器的物理CPU核, Task被执行的并发度 = Executor数目 * 每个Executor核数。


spark-submit --class ca.uwaterloo.watca.Runner --master yarn \
    --deploy-mode cluster \
    --executor-cores 2 \
    --executor-memory 3G \
    --jars ${JARS} WatCA-1.0-SNAPSHOT.jar \
    ip-172-31-24-67:9092,ip-172-31-24-68:9092,ip-172-31-24-69:9092 test3
