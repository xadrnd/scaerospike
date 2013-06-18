package com.tapad.aerospike

import com.aerospike.client.async.{AsyncClientPolicy, MaxCommandAction, AsyncClient}
import java.util.concurrent.atomic.AtomicInteger
import com.aerospike.client.policy.{ClientPolicy, WritePolicy, ScanPolicy}
import com.aerospike.client.{Bin, Record, Key, ScanCallback}
import java.util
import scala.collection.JavaConverters._

object DataPump {
  def main(args: Array[String]) {

    val sourceAddr = args(0)
    val destAddr = args(1)
    val namespace = args(2)

    val clientPolicy = new AsyncClientPolicy
    clientPolicy.maxThreads = 32
    clientPolicy.asyncMaxCommandAction = MaxCommandAction.BLOCK

    val source = new AsyncClient(clientPolicy, sourceAddr, 3000)
    val destination = new AsyncClient(clientPolicy, destAddr ,3000)

    println("Copying all data from namespace %s from cluster at %s to %s...".format(namespace, sourceAddr, destAddr))

    val recordsMoved = new AtomicInteger()

    val scanPolicy = new ScanPolicy()
    scanPolicy.threadsPerNode = 4

    val writePolicy = new WritePolicy()

    var startTime = System.currentTimeMillis()
    val batchSize = 100000
    source.scanAll(scanPolicy, namespace, "", new ScanCallback {
      def scanCallback(key: Key, record: Record) {
        val bins = new util.ArrayList[Bin]()
        val i = record.bins.entrySet().iterator()
        while (i.hasNext) {
          val e = i.next()
          bins.add(new Bin(e.getKey, e.getValue))
        }
        destination.put(writePolicy, key, bins.asScala: _*)
        val count = recordsMoved.incrementAndGet()
        if (count % batchSize == 0) {
          val elapsed = System.currentTimeMillis() - startTime
          startTime = System.currentTimeMillis()
          println("Processed %(,d records, %d ms, %.2f records / sec".format(
            count, elapsed, batchSize.toFloat / elapsed * 1000)
          )
        }
      }
    })
    println("Done, a total of %d records moved...".format(recordsMoved.get()))
  }
}
