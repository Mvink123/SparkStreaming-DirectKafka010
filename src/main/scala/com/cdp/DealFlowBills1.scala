package com.cdp

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
  * 2017/04/05
  * cdp
  * 每次都是从kafka的最新偏移量开始消费数据
  * 会有spark意外退出导致丢数据的现象发生
  */

object DealFlowBills1 {
  def main(args: Array[String]): Unit = {

    //输入参数
    val Array(output, topic, kafkaid, group, sec) = args

    //spark信息
    val conf = new SparkConf().setAppName("DealFlowBills1")
    val ssc = new StreamingContext(conf, Seconds(sec.toInt))

    //kafka参数
    val topics = Array(topic)
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> kafkaid,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> group,
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    //创建DStream
    val lines = KafkaUtils
      .createDirectStream[String, String](ssc, PreferConsistent, Subscribe[String, String](topics, kafkaParams))
      .map(_.value())

    //每一个stream都是一个ConsumerRecord,输出接收行
    lines.count().print()

    //计算DStream
    val result = lines
      .filter(_.split(",").length == 21)
      .map {
        mlines =>
          val line = mlines.split(",")
          (line(3), s"${line(4)},${line(2)}")
      }
      .groupByKey()
      .map {
        case (k, v) =>
          val result = v
            .flatMap {
              fmlines =>
                fmlines.split(",").toList.zipWithIndex
            }
            .groupBy(_._2)
            .map {
              case (v1, v2) =>
                v2.map(_._1)
            }
          (k, result)
      }

    //计算结果存hdfs
    result.saveAsTextFiles(output + s"/output/" + "010")

    ssc.start()
    ssc.awaitTermination()


  }
}
