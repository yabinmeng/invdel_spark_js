/**
 * This code is based on old SparkContext API
 */

package com.example

import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.ReadConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.cassandra._
import com.typesafe.config.Config
import org.scalactic._
import spark.jobserver.SparkSessionJob
import spark.jobserver.api._

import scala.collection.mutable.ListBuffer

object InventoryCleanup_sesn extends SparkSessionJob {
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

  def validate(sparkSession: SparkSession, runtime: JobEnvironment, config: Config) :
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

  case class FacilityDetail
  (
    facility_id: String,
    division: String,
    store: String
  )

  def runJob(sparkSession: SparkSession, runtime: JobEnvironment, inputParams: JobData) : JobOutput = {

    val inv_keyspace = "inventory_balance"
    val facility_detail_tbl = "facility_detail_by_facility_id"
    val inventory_tbl = "current_item_balance_by_base_upc"

    val store_name = inputParams(0)
    val division_name = inputParams(1)

    import sparkSession.implicits._

    // read facility details info, by the store and division
    //   info that are provided as input parameters
    var facilityDetailDF = sparkSession
      .read
      .cassandraFormat(facility_detail_tbl, inv_keyspace)
      .options(ReadConf.SplitSizeInMBParam.option(32))
      .load()
      .select("facility_id", "division", "store")
      .as[FacilityDetail]


    if (!isEmptyStr(store_name)) {
      facilityDetailDF = facilityDetailDF.where("store = '" + store_name + "'")
    }

    if (!isEmptyStr(division_name)) {
      facilityDetailDF = facilityDetailDF.where("division = '" + division_name + "'")
    }

    //facilityDetailDF.cache()

    val facilityList = facilityDetailDF
      .map(fd => fd.facility_id)
      .collect()
      .toList


    // read current_item_balance table with all facilities that match
    //   the selected list from above
    var inventoryDF = sparkSession
      .read
      .cassandraFormat(inventory_tbl, inv_keyspace)
      .options(ReadConf.SplitSizeInMBParam.option(32))
      .load()
      .select("facility_id", "base_upc", "location")
      .filter($"facility_id".isin(facilityList:_*))

    inventoryDF.cache()

    // Delete the inventories satisfying the store/division conditions
    inventoryDF.rdd.deleteFromCassandra(inv_keyspace, inventory_tbl)

    val resultStr = "" + inventoryDF.count() +
      " inventories deleted by condition: store [" + store_name + "], division [" + division_name + "]"

    return resultStr
  }
}
