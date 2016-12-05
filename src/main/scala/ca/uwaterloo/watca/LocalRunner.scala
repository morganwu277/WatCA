//import org.apache.spark.rdd.RDD
//import org.apache.spark.streaming.{Seconds, StreamingContext}
//
//import scala.collection.mutable.Queue
//
//// Create the context
//val ssc = new StreamingContext(sc, Seconds(1))
//
//// Create the queue through which RDDs can be pushed to
//// a QueueInputDStream
//val rddQueue = new Queue[RDD[Int]]()
//
//// Create the QueueInputDStream and use it do some processing
//val inputStream = ssc.queueStream(rddQueue)
//val start = System.currentTimeMillis
//val mappedStream = inputStream.map(x => (x % 10, 1))
//val reducedStream = mappedStream.reduceByKey(_ + _)
//reducedStream.foreachRDD(_.foreach(x => {
//  println("========= Time: " + System.currentTimeMillis() - start)
//}))
//
//reducedStream.print()
//ssc.start()
//
//// Create and push some RDDs into rddQueue
//for (i <- 1 to 30) {
//  rddQueue.synchronized {
//    rddQueue += ssc.sparkContext.makeRDD(1 to 1000, 10)
//  }
//  Thread.sleep(1000)
//}
//ssc.stop()
//
