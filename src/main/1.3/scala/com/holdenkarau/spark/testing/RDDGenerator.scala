/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Generator for RDDs for testing with ScalaCheck
 */
package com.holdenkarau.spark.testing

import scala.reflect.ClassTag

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.annotation.Experimental
import org.apache.spark.mllib.random._
import org.scalacheck._

@Experimental
object RDDGenerator {

  /**
   * Generate an RDD of the desired type. Attempt to try different number of partitions
   * so as to catch problems with empty partitions, etc.
   * minPartitions defaults to 1, but when generating data too large for a single machine choose a larger value.
   *
   * @param sc            Spark Context
   * @param minPartitions defaults to 1
   * @param generator     used to create the generator. This function will be used to create the generator as
   *                      many times as required.
   * @tparam T The required type for the RDD
   * @return
   */
  def genRDD[T: ClassTag](sc: SparkContext, minPartitions: Int = 1)(generator: => Gen[T]): Gen[RDD[T]] = {
    arbitraryRDD(sc, minPartitions)(generator).arbitrary
  }

  /**
   * Generate an RDD of the desired type. Attempt to try different number of partitions
   * so as to catch problems with empty partitions, etc.
   * minPartitions defaults to 1, but when generating data too large for a single machine choose a larger value.
   *
   * @param sc            Spark Context
   * @param minPartitions defaults to 1
   * @param generator     used to create the generator. This function will be used to create the generator as
   *                      many times as required.
   * @tparam T The required type for the RDD
   * @return
   */
  def arbitraryRDD[T: ClassTag](sc: SparkContext, minPartitions: Int = 1)(generator: => Gen[T]): Arbitrary[RDD[T]] = {
    Arbitrary {
      Gen.sized(sz =>
        sz match {
          case 0 => sc.emptyRDD[T]
          case size => {
            // Generate different partition sizes
            val mp = minPartitions
            val specialPartitionSizes = List(size, (size / 2), mp, mp + 1, mp + 3).filter(_ > mp)
            val partitionsGen = for {
            // TODO change 1 to minPartitions
              partitionCount <- Gen.chooseNum(1, 2 * size, specialPartitionSizes: _*)
            } yield partitionCount
            // Wrap the scalacheck generator in a Spark generator
            val sparkElemGenerator = new WrappedGenerator(generator)
            val rdds = partitionsGen.map { numPartitions =>
              RandomRDDs.randomRDD(sc, sparkElemGenerator, size, numPartitions)
            }
            rdds
          }
        })
    }
  }
}

/**
 * A WrappedGenerator wraps a ScalaCheck generator to allow Spark's RandomRDD to use it
 */
private[testing] class WrappedGenerator[T](getGenerator: => Gen[T]) extends RandomDataGenerator[T] {
  lazy val random = new scala.util.Random()
  lazy val params = Gen.Parameters.default.withRng(random)
  lazy val generator: Gen[T] = getGenerator

  def nextValue(): T = {
    generator(params).get
  }

  def copy() = {
    new WrappedGenerator(getGenerator)
  }

  override def setSeed(seed: Long): Unit = random.setSeed(seed)
}
