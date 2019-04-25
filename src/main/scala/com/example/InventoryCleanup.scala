package com.example

import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.ReadConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.{Dataset, SQLContext}
import org.apache.spark.sql.cassandra._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalactic._
import spark.jobserver.api.{SparkJob => NewSparkJob, _}

import scala.collection.mutable.ListBuffer
import org.apache.spark.util.AccumulatorV2



object InventoryCleanup extends NewSparkJob {
  type JobData = List[String]
  type JobOutput = String

  def isEmptyStr(x: String) = Option(x).forall(_.isEmpty)

  def getConfigString(config: Config, path: String): String = {
    var parmValue = ""

    if (config.hasPath(path)) {
      parmValue = config.getString(path)
    }

    return parmValue
  }

  def validate(sc: SparkContext, runtime: JobEnvironment, config: Config) :
    JobData Or Every[ValidationProblem] = {

    val storeName = getConfigString(config, "store_name")
    val divisionName = getConfigString(config,"division_name")

    if ( isEmptyStr(storeName) && isEmptyStr(divisionName) ) {
      return Bad(One(SingleProblem("Must specify at least store_name or division_name")))
    }
    else {
      var jobParams = ListBuffer[String]()
      jobParams += storeName.toString
      jobParams += divisionName.toString

      return Good(jobParams.toList)
    }
  }


  def runJob(sc: SparkContext, runtime: JobEnvironment, inputParams: JobData) : JobOutput = {

    val inv_keyspace = "inventory_balance"
    val facility_detail_tbl = "facility_detail_by_facility_id"
    val inventory_tbl = "current_item_balance_by_base_upc"

    val storeName = inputParams(0)
    val divisionName = inputParams(1)

    case class FacilityDetail
    (
      facility_id: String,
      division: String,
      store: String
    )

    /**
      * NOTE: Spark job server (v 0.8.0) doesn't support SparkSession
      *
      */

    val sqlContext = new SQLContext(sc)

    import sqlContext.implicits._

    // read facility details info, by the store and division
    //   info that are provided as input parameters
    var facilityDetailDS = sqlContext
      .read
      .cassandraFormat(facility_detail_tbl, inv_keyspace)
      .options(ReadConf.SplitSizeInMBParam.option(32))
      .load()
      .select("facility_id", "division", "store")
      // Compilation error:
      //   Unable to find encoder for type FacilityDetail. An implicit Encoder[FacilityDetail] is needed to store FacilityDetail instances in a Dataset.
      //   Primitive types (Int, String, etc) and Product types (case classes) are supported by importing spark.implicits._
      //   Support for serializing other types will be added in future releases.
      //.as[FacilityDetail]


    if (!isEmptyStr(storeName)) {
      facilityDetailDS = facilityDetailDS.filter($"store"  ===  storeName)
    }

    if (!isEmptyStr(divisionName)) {
      facilityDetailDS = facilityDetailDS.filter($"division" === divisionName)
    }

    //facilityDetailDF.cache()

    val facilityList = facilityDetailDS
      .map(fd => fd.getString(0))
      .collect()
      .toList


    // read current_item_balance table with all facilities that match
    //   the selected list from above
    var inventoryDS = sqlContext
      .read
      .cassandraFormat(inventory_tbl, inv_keyspace)
      .options(ReadConf.SplitSizeInMBParam.option(32))
      .load()
      .select("facility_id", "base_upc", "location")
      .filter($"facility_id".isin(facilityList:_*))

    inventoryDS.cache()

    // Delete the inventories satisfying the store/division conditions
    inventoryDS.rdd.deleteFromCassandra(inv_keyspace, inventory_tbl)

    val resultStr = "" + inventoryDS.count() +
      " inventories deleted by condition: store [" + storeName + "], division [" + divisionName + "]"

    return resultStr
  }
}