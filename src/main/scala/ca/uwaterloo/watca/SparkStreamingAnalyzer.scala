package ca.uwaterloo.watca

import java.util
import java.util.concurrent.atomic.AtomicInteger

class SparkStreamingAnalyzer {
  val operations: util.List[Operation] = new util.ArrayList[Operation]
  val numLines = new AtomicInteger

  def processOperation(op: Operation) {
    operations synchronized {
      operations.add(op)
    }
    val n: Int = numLines.getAndIncrement
    if (n % 100000 == 0) {
      println("Num lines: " + n)
    }
  }

}
