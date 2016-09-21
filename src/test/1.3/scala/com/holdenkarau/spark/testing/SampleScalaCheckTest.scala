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
package com.holdenkarau.spark.testing

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.apache.spark.sql.types._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop.forAll
import org.scalatest.FunSuite
import org.scalatest.prop.Checkers

class SampleScalaCheckTest extends FunSuite with SharedSparkContext with RDDComparisons with Checkers {
  // tag::propertySample[]
  // A trivial property that the map doesn't change the number of elements
  test("map should not change number of elements") {
    val property =
      forAll(RDDGenerator.genRDD[String](sc)(Arbitrary.arbitrary[String])) {
        rdd => rdd.map(_.length).count() == rdd.count()
      }

    check(property)
  }
  // end::propertySample[]
  // A slightly more complex property check using RDDComparisions
  // tag::propertySample2[]
  test("assert that two methods on the RDD have the same results") {
    val property =
      forAll(RDDGenerator.genRDD[String](sc)(Arbitrary.arbitrary[String])) {
        rdd => compareRDD(filterOne(rdd), filterOther(rdd)).isEmpty
      }

    check(property)
  }
  // end::propertySample2[]

  test("test custom RDD generator") {

    val humanGen: Gen[RDD[Human]] =
      RDDGenerator.genRDD[Human](sc) {
        val generator: Gen[Human] = for {
          name <- Arbitrary.arbitrary[String]
          age <- Arbitrary.arbitrary[Int]
        } yield (Human(name, age))

        generator
      }

    val property =
      forAll(humanGen) {
        rdd => rdd.map(_.age).count() == rdd.count()
      }

    check(property)
  }

  test("assert rows' types like schema type") {
    val schema = StructType(List(StructField("name", StringType), StructField("age", IntegerType)))
    val rowGen: Gen[Row] = DataframeGenerator.getRowGenerator(schema)
    val property =
      forAll(rowGen) {
        row => row.get(0).isInstanceOf[String] && row.get(1).isInstanceOf[Int]
      }

    check(property)
  }

  test("test generating Dataframes") {
    val schema = StructType(List(StructField("name", StringType), StructField("age", IntegerType)))
    val sqlContext = new SQLContext(sc)
    val dataframeGen = DataframeGenerator.arbitraryDataFrame(sqlContext, schema)

    val property =
      forAll(dataframeGen.arbitrary) {
        dataframe => dataframe.schema === schema && dataframe.count >= 0
      }

    check(property)
  }

  test("test custom columns generators") {
    val schema = StructType(List(StructField("name", StringType), StructField("age", IntegerType)))
    val sqlContext = new SQLContext(sc)
    val ageGenerator = new ColumnGenerator("age", Gen.choose(10, 100))
    val dataframeGen = DataframeGenerator.arbitraryDataFrameWithCustomFields(sqlContext, schema)(ageGenerator)

    val property =
      forAll(dataframeGen.arbitrary) {
        dataframe => dataframe.schema === schema && dataframe.filter("age > 100 OR age < 10").count() == 0
      }

    check(property)
  }

  test("test multiple columns generators") {
    val schema = StructType(List(StructField("name", StringType), StructField("age", IntegerType)))
    val sqlContext = new SQLContext(sc)
    val nameGenerator = new ColumnGenerator("name", Gen.oneOf("Holden", "Hanafy")) // name should be on of those
    val ageGenerator = new ColumnGenerator("age", Gen.choose(10, 100))
    val dataframeGen = DataframeGenerator.arbitraryDataFrameWithCustomFields(sqlContext, schema)(nameGenerator, ageGenerator)

    val property =
      forAll(dataframeGen.arbitrary) {
        dataframe => dataframe.schema === schema &&
          dataframe.filter("(name != 'Holden' AND name != 'Hanafy') OR (age > 100 OR age < 10)").count() == 0
      }

    check(property)
  }

  test("generate rdd of specific size") {
    implicit val generatorDrivenConfig =
      PropertyCheckConfig(minSize = 10, maxSize = 20)
    val prop = forAll(RDDGenerator.genRDD[String](sc)(Arbitrary.arbitrary[String])){
      rdd => rdd.count() <= 20
    }
    check(prop)
  }

  test("test Array Type generator"){
    val schema = StructType(List(StructField("name", StringType, true),
      StructField("pandas", ArrayType(StructType(List(
        StructField("id", LongType, true),
        StructField("zip", StringType, true),
        StructField("happy", BooleanType, true),
        StructField("attributes", ArrayType(FloatType), true)))))))

    val sqlContext = new SQLContext(sc)
    val dataframeGen: Arbitrary[DataFrame] = DataframeGenerator.arbitraryDataFrame(sqlContext, schema)
    val property =
      forAll(dataframeGen.arbitrary) {
        dataframe => dataframe.schema === schema &&
          dataframe.select("pandas.attributes").rdd.map(_.getSeq(0)).count() >= 0
      }

    check(property)
  }

  test("timestamp and type generation") {
    val schema = StructType(List(StructField("timeStamp", TimestampType), StructField("date", DateType)))
    val sqlContext = new SQLContext(sc)
    val dataframeGen = DataframeGenerator.arbitraryDataFrame(sqlContext, schema)

    val property =
      forAll(dataframeGen.arbitrary) {
        dataframe => {
          dataframe.schema === schema && dataframe.count >= 0
        }
      }

    check(property)
  }

  test("map type generation") {
    val schema = StructType(List(StructField("map", MapType(LongType, IntegerType, true))))
    val sqlContext = new SQLContext(sc)
    val dataframeGen = DataframeGenerator.arbitraryDataFrame(sqlContext, schema)

    val property =
      forAll(dataframeGen.arbitrary) {
        dataframe => {
          dataframe.schema === schema && dataframe.count >= 0
        }
      }

    check(property)
  }

  test("second dataframe's evaluation has the same values as first") {
    implicit val generatorDrivenConfig =
      PropertyCheckConfig(minSize = 1, maxSize = 1)
    val fields = StructField("byteType", ByteType) ::
      StructField("shortType", ShortType) ::
      StructField("intType", IntegerType) ::
      StructField("longType", LongType) ::
      StructField("doubleType", DoubleType) ::
      StructField("stringType", StringType) ::
      StructField("binaryType", BinaryType) ::
      StructField("booleanType", BooleanType) ::
      StructField("timestampType", TimestampType) ::
      StructField("dateType", DateType) ::
      StructField("arrayType", ArrayType(TimestampType)) ::
      StructField("mapType", MapType(LongType, TimestampType, valueContainsNull = true)) ::
      StructField("structType", StructType(StructField("timestampType", TimestampType) :: Nil)) :: Nil

    val sqlContext = new SQLContext(sc)
    val dataframeGen = DataframeGenerator.arbitraryDataFrame(sqlContext, StructType(fields))

    val property =
      forAll(dataframeGen.arbitrary) {
        dataframe => {
          val firstEvaluation = dataframe.collect()
          val secondEvaluation = dataframe.collect()
          val zipped = firstEvaluation.zip(secondEvaluation)
          zipped.forall { case (r1, r2) => DataFrameSuiteBase.approxEquals(r1, r2, 0.0) }
        }
      }

    check(property)
  }

  private def filterOne(rdd: RDD[String]): RDD[Int] = {
    rdd.filter(_.length > 2).map(_.length)
  }

  private def filterOther(rdd: RDD[String]): RDD[Int] = {
    rdd.map(_.length).filter(_ > 2)
  }
}

case class Human(name: String, age: Int)
