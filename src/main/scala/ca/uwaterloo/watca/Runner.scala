package ca.uwaterloo.watca

import _root_.kafka.serializer.StringDecoder
import org.apache.log4j.{Level, Logger}
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.{HashPartitioner, SparkConf}

import scala.collection.JavaConversions
import scala.collection.mutable.ArrayBuffer


object Runner {

  // Create context with 1 second batch interval
  val COMPUTE_WINDOW_SIZE = Seconds(1)
  val COMPUTE_INTERVAL = COMPUTE_WINDOW_SIZE
  val DISPLAY_WINDOW_SIZE = Seconds(60)
  //  val sfn = new RegularScoreFunction
  val sfn = new GKScoreFunction


  def main(args: Array[String]) {

    if (args.length < 2) {
      System.err.println(
        s"""
           |Usage: Runner <brokers> <topics>
           |  <brokers> is a list of one or more Kafka brokers
           |  <topics> is a list of one or more kafka topics to consume from
           |
        """.stripMargin)
      System.exit(1)
    }

    Logger.getRootLogger.setLevel(Level.WARN)

    val Array(brokers, topics) = args

    val sparkConf = new SparkConf().setAppName("WatCA")
    val ssc = new StreamingContext(sparkConf, COMPUTE_INTERVAL)
    ssc.checkpoint("checkpoint")

    // Create direct kafka stream with brokers and topics
    val topicsSet = topics.split(",").toSet
    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)
    val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](
          ssc, kafkaParams, topicsSet)

    var counter = 0
    val lines = messages.filter(kv => {
      counter += 1
      //            printf("===This is No. %d Message \n", counter)
      //      printf("---key: %s \n", kv._1)
      //            printf("---value: %s \n", kv._2)
      kv._2 != ""
    }).map(_._2)

    // 0. collect all operations
    val temp = lines.map(line => new Operation(line)) // List[Operation]

    // 1. first add new operations
    val keyOpsMap = temp
      .map(op => (op.getKey, op))
      .groupByKey() // <String, List[Operation]>    Key,List[Operation]
    val initialRDD = ssc.sparkContext.parallelize(List[(String, History)]())
    val now = System.currentTimeMillis()
    val timeThreshold = now - COMPUTE_WINDOW_SIZE.milliseconds
    val keyHistMap = keyOpsMap.updateStateByKey[History](updateKeyHistMap(timeThreshold), new HashPartitioner(ssc.sparkContext.defaultParallelism), true, initialRDD)

    // 2. compute scores for each history
    val keyScoreMap = keyHistMap.map(h => {
      (h._1, h._2.getScores(sfn))
    })
    // 3. collect the scores
    val scoreList = new ArrayBuffer[Long]
    keyScoreMap.map(_._2).foreachRDD(_.foreachPartition(_.foreach(scores => {
      var index = 0
      while (index < scores.size) {
        scoreList += scores.get(index)
        index += 1
      }
    })))
    val outputSpectrum = scoreList

    // Compute staleness proportion.
    val _numScores = keyScoreMap.map(_._2).flatMap(x => JavaConversions.asScalaBuffer(x)).count()
    val _posScores = keyScoreMap.map(_._2).flatMap(x => JavaConversions.asScalaBuffer(x)).filter(_ > 0).count()
    val _mapNumScores = _numScores.map(x => ("", x)).reduceByKey(_ + _)
    val _mapNumPosScores = _posScores.map(x => ("", x)).reduceByKey(_ + _)
    _mapNumScores.join(_mapNumPosScores).foreachRDD(_.filter(_._1 == "").foreach(v => {
      val numScores = v._2._1
      val numPosScores = v._2._2
      if (numScores > 0) {
        val staleProp = numPosScores.toFloat / numScores
        println("Staleness proportion at time " + now + " is " + staleProp)
      }
    }))

    // TODO: add staleProp to outputStaleProp which is in the Database

    val posScores = scoreList.filter(_ > 0)
    val numPosScores = posScores.size

    // Compute staleness quartiles.
    val quarts = new ArrayBuffer[Long]
    if (numPosScores > 0) {
      var _scores = posScores
      _scores = _scores.sorted
      quarts += (_scores(0))
      quarts += (_scores((0.25 * (numPosScores - 1)).toInt))
      quarts += (_scores((0.75 * (numPosScores - 1)).toInt))
      quarts += (_scores(numPosScores - 1))
    } else {
      quarts += (0L)
      quarts += (0L)
      quarts += (0L)
      quarts += (0L)
    }

    // TODO: add quarts to outputStaleQuart which is in the database

    // Analyze throughput.
    var numOps = temp.count().map(v => ("", v)).reduceByKey(_ + _)
    var maxFinish = temp.reduce((x, y) => if (x.getFinish > y.getFinish) x else y).map(v => ("", v.getFinish)).reduceByKey(_ max _)
    var minFinish = temp.reduce((x, y) => if (x.getFinish < y.getFinish) x else y).map(v => ("", v.getFinish)).reduceByKey(_ min _)
    numOps.join(maxFinish).join(minFinish).foreachRDD(_.filter(_._1 == "").foreach(v => {
      val numOps = v._2._1._1
      val maxFinish = v._2._1._2
      val minFinish = v._2._2
      if (maxFinish > minFinish) {
        val thru = numOps.toFloat / ((maxFinish - minFinish) / 1000)
        println("Throughput at time " + now + " is " + thru + ", total numOps: " + numOps + ", maxFinish: " + maxFinish + ", minFinish" + minFinish)
      }
    }))
    // TODO: add throughput to outputThroughput which is the database

    // Analyze latency.
    var totLat: Long = 0
    val lats = new ArrayBuffer[Long]
    temp.foreachRDD(_.foreach(op => {
      val lat = op.getFinish - op.getStart
      totLat += lat
      lats += (lat)
    }))

    val _lats = lats.sorted

    // TODO: add latency to outputLatencyAvg and outputLatency95 which are in the database
    lines.map(x => ("", 1L))
      .reduceByKey(_ + _)
      .map(kv => {
        //        println("======== this batch message total: " + kv._2)
        kv
      })
      .updateStateByKey(updateRunningSum _)
      .foreachRDD(_.foreach(kv => {
        //        println("======== accumulative message total: " + kv._2)
        //        println("The whole computeMetrics take time " + (System.currentTimeMillis - now) + " ms.")
      }))


    // Start the computation
    ssc.start()
    ssc.awaitTermination()
  }


  val removeKeyHistMapOldOnes = (timeThreshold: Long) => {
    val func = (currValues: Seq[History], prevValueState: Option[History]) => {
      currValues.map(h => {
        h.deleteOld(timeThreshold)
        Some(h)
      })
    }
    func
  }


  /**
   *
   * iterator Seq[ List[Operation] ] because an interval may have different batches in different executors
   * in one batch, for each executor , this key has List[Operation]
   * in one batch, for different executor, this key has Seq[ List[Operation] ]
   * @return
   */
  val updateKeyHistMap = (timeThreashold: Long) => {
    val func = (iterator: Iterator[(String, Seq[Iterable[Operation]], Option[History])]) => {
      iterator.flatMap(t => {
        val h = t._3.getOrElse(new History(t._1))
        t._2.foreach(seqList => {
          seqList.foreach(op => {
            h.addOperation(op)
          })
        })
        h.deleteOld(timeThreashold) // delete old ones
        Some(t._1, h)
      })
    }
    func
  }


  /**
   *
   * @param currValues events in this batch
   * @param prevValueState old state
   * @return
   */
  def updateRunningSum(currValues: Seq[Long], prevValueState: Option[Long]) = {
    Some(prevValueState.getOrElse(0L) + currValues.sum)
  }
}

