# Overview

Since version 4.8, DataStax Enterprise (DSE) has integrated [Spark jobserver](https://github.com/spark-jobserver/spark-jobserver) into its product suite, as an alternate way to manage submitted Spark jobs in a DSE cluster, via REST APIs. In order to use Spark jobserver to manage a Spark job, there is some unique requirements regarding how to write the Spark job. Unfortunately, there is not much document and/or example to follow on how to do so. This repo is intended to address this issue by providing both a step-by-step guideline document and a working example for submitting a Spark job against a DSE cluster.

## Testing DSE Environment and Scenario Description

The testing DSE environment is based on version 6.7.2, with the following characteristics:
* 3 node, single DC cluster
* SearchAnalytics workload enabled (Solr + Spark)

The Spark appliction example is based on my previous [spark application example](https://github.com/yabinmeng/invdel_spark) about doing a mass-deletion of inventories based on facility division and store location. The C* table schema can some testing data can be found from [here](https://github.com/yabinmeng/invdel_spark_js/blob/master/src/resources/schema.cql) under "src/resources" directory.

# DSE Spark Jobserver Overview

## Start/Stop DSE Spark Jobserver

DSE Spark Jobserver is simply a packaged version of the OSS Spark Jobserver. There is no functional difference between them. Starting DSE Spark Jobserver is esay, just by executing the following command with the screen output as below:
```
  $ dse spark-jobserver start
  JMX_PORT empty, using default 9999
```

Once started, a folder (as below) will be created under the user's home directory. This folder is where the uploaded application jar files, temporary files, and log files are kept.
```
  $HOME/.spark-jobserver
```

Meanwhile, once started, the DSE Spark job server web UI is accessible from the following URL:
```
  http://<DSE_Spark_Jobserver_IP>:8090/
```

This port, if needed, can be changed from DSE Spark Jobserver main configuration file (**dse.conf**) under the Spark Jobserver installation directory, as below:
```
# Spark Cluster / Job Server configuration
spark {
  ... ...

  jobserver {
    port = 8090
  }
  
  ... ...
}
```

There are also some other key settings in this configuration file such as "default number of CPUs for jobs", "predefiend spark context memory and CPU", and etc, that can be fine tuned. 

The default installation directory of the Spark Jobserver depends on the type of installation:
```
  Package installations: /usr/share/dse/spark/spark-jobserver
  Tarball installations: installation_location/resources/spark/spark-jobserver
```

## Uploading Application Jar Files and Executing Them

In order to use DSE Spark jobserver to manage the Spark job application execution against a DSE cluster, the application jar file needs to be uploaded to Spark jobserver first, through its REST API. In the example below, an application jar file named ***invdel_spark_js-assembly-1.0.jar*** is uploaded to DSE Spark Jobserver under the name **invdel**. The "curl" command is executed from the directory where the jar file is located. 
```
  $ curl -X POST <DSE_Spark_Jobserver_IP>:8090/jars/invdel -H "Content-Type: application/java-archive" --data-binary @invdel_spark_js-assembly-1.0.jar
  {
    "status": "SUCCESS",
    "result": "Jar uploaded"
  }
```

You can also view the uploaded application jar files and delete them via REST APIs:
```
  ## List uploaded binary files
  $ curl -X GET 34.229.41.46:8090/binaries
  {
      "invdel": {
        "binary-type": "Jar",
        "upload-time": "2019-04-26T15:48:37.528Z"
      }
  }

  ## List uploaded jar files (one specific type of binary)
  $ curl -X GET <DSE_Spark_Jobserver_IP>:8090/jars
  {
    "invdel": "2019-04-26T15:48:37.528Z"
  }
  
  ## Delete an uploaded binary
  $ curl -X DELETE <DSE_Spark_Jobserver_IP>:8090/binaries/invdel
  OK
```


