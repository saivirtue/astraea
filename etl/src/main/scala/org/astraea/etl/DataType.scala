/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.astraea.etl

sealed abstract class DataType(dataType: String) {
  def value: String = {
    dataType
  }
}

/** Column Types supported by astraea dispatcher. */
object DataType {
  private val STRING_TYPE = "string"
  private val BOOLEAN_TYPE = "boolean"
  private val DATE_TYPE = "date"
  private val DOUBLE_TYPE = "double"
  private val BYTE_TYPE = "byte"
  private val INTEGER_TYPE = "integer"
  private val LONG_TYPE = "long"
  private val SHORT_TYPE = "short"
  private val TIMESTAMP_TYPE = "timestamp"

  case object StringType extends DataType(STRING_TYPE)
  case object BooleanType extends DataType(BOOLEAN_TYPE)
  case object DateType extends DataType(DATE_TYPE)
  case object DoubleType extends DataType(DOUBLE_TYPE)
  case object ByteType extends DataType(BYTE_TYPE)
  case object IntegerType extends DataType(INTEGER_TYPE)
  case object LongType extends DataType(LONG_TYPE)
  case object ShortType extends DataType(SHORT_TYPE)
  case object TimestampType extends DataType(TIMESTAMP_TYPE)

  /** @param str
    *   String that needs to be parsed as a DataType.
    * @return
    *   DataType
    */
  def of(str: String): DataType = {
    val value = all.filter(_.value == str)
    if (value.isEmpty) {
      throw new IllegalArgumentException(
        s"$str is not supported data type.The data types supported ${all.mkString(",")}."
      )
    }
    value.head
  }

  /** @param map
    *   A set of string needs to be parsed as DataType
    * @return
    *   Map[String, DataType]
    */
  def of(map: Map[String, String]): Map[String, DataType] = {
    map.map(x => (x._1, of(x._2)))
  }

  /** @return
    *   All supported data types.
    */
  def all: Seq[DataType] = {
    Seq(
      StringType,
      BooleanType,
      DateType,
      DoubleType,
      ByteType,
      IntegerType,
      LongType,
      ShortType
    )
  }
}
