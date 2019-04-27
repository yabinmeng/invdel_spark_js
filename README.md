# Overview

Since version 4.8, DataStax Enterprise (DSE) has integrated [Spark Jobserver](https://github.com/spark-jobserver/spark-jobserver) into its product suite, as an alternate way to manage submitted Spark jobs in a DSE cluster, via REST APIs. In order to use Spark jobserver to manage a Spark job, there is some unique requirements regarding how to write the Spark job. Unfortunately, there is not much document and/or example to follow on how to do so. This repo is intended to address this issue by providing both a step-by-step guideline document and a working example for submitting a Spark job against a DSE cluster.

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

### Upload Appliation Jar Files to DSE Spark Jobserver

In order to use DSE Spark jobserver to manage the Spark job application execution against a DSE cluster, the application jar file needs to be uploaded to Spark jobserver first, through its REST API. In the example below, an application jar file named ***invdel_spark_js-assembly-1.0.jar*** is uploaded to DSE Spark Jobserver under the name of **invdel**. The "curl" command is executed from the directory where the jar file is located. 
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
  $ curl -X GET <DSE_Spark_Jobserver_IP>:8090/binaries
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

### Submitting Spark Jobs to DSE from Spark Jobserver 

After the application jar file is uploaded, you can execute it from Spark Jobserver. The execution is actually done by DSE cluster because internally Spark Jobserver will submit the job to DSE cluster.

```
  $  curl -d "<App_Input_Parameters>" "<DSE_Spark_Jobserver_IP>:8090/jobs?appName=invdel&classPath=com.example.InventoryCleanup_cntx"
  {
    "duration": "Job not done yet",
    "classPath": "com.example.InventoryCleanup",
    "startTime": "2019-04-26T16:09:10.635Z",
    "context": "28c96d17-com.example.InventoryCleanup",
    "status": "STARTED",
    "jobId": "1173eee8-c2da-44c6-b20b-987178bab7a9"
  }
```

There are a few things that need to point out here:
* For "-d <App_Input_Parameters>" part, it specifies the input parameters required by the application. It can take other forms to pass in the input parameters, which will talk a little bit more in the next chapter.

* For "appName=invdel" part, the string after "appName=" is the application name that was given when the jar file was uploaded to the Spark Jobserver

* For "classPath=com.example.InventoryCleanup_cntx" part, the string after "classPath=" is the full application class name.

The screen output above shows the job ID assigned to the job. You can use it to query the on-going job status. In the example below, the job has successfully completed with customized result/repsonse as returned in the **result** field.
```
  $ curl 34.229.41.46:8090/jobs/1173eee8-c2da-44c6-b20b-987178bab7a9
  {
    "duration": "25.139 secs",
    "classPath": "com.example.InventoryCleanup",
    "startTime": "2019-04-26T16:09:10.635Z",  
    "context": "28c96d17-com.example.InventoryCleanup",
    "result": "3 inventories deleted by condition: store [store_1], division [dallas]",
    "status": "FINISHED",
    "jobId": "1173eee8-c2da-44c6-b20b-987178bab7a9"
  }
```

# Develop a Spark Jobserver Ready Spark Application

As the first try of my effort, I uploaded a regular Spark application jar file (to be used in **dse spark-submit** command) to DSE Spark Jobserver and tried to run it. I got the following error message. Apparently, there is some unique requirements about writing a Spark job application that can be executed via Spark Jobserver. 
```
{
  "status": "JOB LOADING FAILED",
  "result": {
    "message": "com.example.InventoryCleanup$ cannot be cast to spark.jobserver.api.SparkJobBase",
    "errorClass": "java.lang.ClassCastException",
    "stack": "java.lang.ClassCastException: com.example.InventoryCleanup$ cannot be cast to 
              spark.jobserver.api.SparkJobBase\n\tat
              ... ..."
  }
}
```

Compared with a regular Spark application, the changes to be made for a Spark Jobserver ready Spark application are minimal and actually quite standard. I'll highlight these changes in the following sections. For the complete documentation, please refer to OSS Spark Jobserver documentation [here](https://github.com/spark-jobserver/spark-jobserver#create-a-job-server-project)


## SBT Project with Dependency Libraries 

The example in this repo is a SBT project, with the following Spark Jobserver library (version 0.8.0) included:
```
  resolvers += "Job Server Bintray" at "https://dl.bintray.com/spark-jobserver/maven"
  libraryDependencies += "spark.jobserver" %% "job-server-api" % "0.8.0" % "provided"
```

If a SQL or Hive job/context is desired (which is not the case for this repo), please include the following library as well:
```
  libraryDependencies += "spark.jobserver" %% "job-server-extras" % "0.8.0" % "provided"
```

## Overall Program Structure 

A Spark application that is intended to be submitted for execution through Spark Jobserver needs to have the following program structure. In this repo, program [InventoryCleanup_cntx.scala](https://github.com/yabinmeng/invdel_spark_js/blob/master/src/main/scala/com/example/InventoryCleanup_cntx.scala) follows this structure.
```
  object WhatEverAppName extends SparkJob {
    type JobData = <type_for_input_parameters>
    type JobOutput = <type_for_output_results>

    def runJob(sc: SparkContext, runtime: JobEnvironment, data: JobData): JobOutput = {
   
    }

    def validate(sc: SparkContext, runtime: JobEnvironment, config: Config):
      JobData Or Every[ValidationProblem] = {
      
    }
}
```

For Spark 2.x, if we want a SparkSession context for Spark-SQL and Hive support, the program should follow the following (similar) structure. In this repo, program [InventoryCleanup_sesn.scala](https://github.com/yabinmeng/invdel_spark_js/blob/master/src/main/scala/com/example/InventoryCleanup_sesn.scala) follows this structure.
```
  object WhatEverAppName extends SparkSessionJob {
    type JobData = <type_for_input_parameters>
    type JobOutput = <type_for_output_results>

    def runJob(sparkSession: SparkSession, runtime: JobEnvironment, data: JobData): JobOutput = {
   
    }

    def validate(sparkSession: SparkSession, runtime: JobEnvironment, config: Config):
      JobData Or Every[ValidationProblem] = {
      
    }
}
```

With such a structure,

1) The application needs to implement Spark Jobserver's [**SparkJob**](https://github.com/spark-jobserver/spark-jobserver/blob/1ef0178cdb3095c1da3d867e94c702b6ca74bfeb/job-server-api/src/main/scala/spark/jobserver/SparkJob.scala) trait or [**SparkSessionJob**](https://github.com/spark-jobserver/spark-jobserver/blob/1ef0178cdb3095c1da3d867e94c702b6ca74bfeb/job-server-extras/src/main/scala/spark/jobserver/SparkSessionJob.scala) trait, which both extends [SparkJobBase](https://github.com/spark-jobserver/spark-jobserver/blob/1ef0178cdb3095c1da3d867e94c702b6ca74bfeb/job-server-api/src/main/scala/spark/jobserver/api/SparkJobBase.scala) trait.

2) There are two main methods need to be implemented:

   * **runJob**: This is where the application's main logic is defined. But unlike a reglar Spark application, you don't need to create the SparkSession in this method. Instead, it is managed by the Spark JobServer and will be provided to the job through this method.
   
   * **validate**: In this method, we're doing an initial validation of the context and any provided configuration, such as for the input parameter validity check. It also generates the final paramaters that are needed by the job execution.

3) Validated Application Input Paramters and Return Results

The actual job execution (**runJob()**) takes whatever input from **JobData** result that is returnded from the validation method (**validate()**). JobData can be defined to any type that you want it to be.  Please note that the contents in **JobData** may not be the same as the raw input parameters that you might've provided through the APIs. It is all up to the actual validation and processing logic of method **validate()**. Simply speakig, you can think of **JobData** as the validated (and possibly transformed) application input parameters.

Application output that will be returned back to the client (the response of the REST API call) needs to put in **JobOutput**. Again, the actual type can be any you want it to be.


## Process Raw Application Input Parameters

The raw application input parameter can be provided in a format that conforms the configuration library for JVM lanugages: https://github.com/lightbend/config. In particular, the following formats:
* Java properties, 
* JSON
* human-friendly JSON superset

The raw input parameter is taken in and processed by the validation method (**validate()**), through its "config: Config" parameter. 

In its simpliest form, the raw input parameters can be provided through 'curl' command "**-d or --data**" option (assuming the REST API is called through 'curl' command). Multiple parameters are separated by comma.
```
  $ curl -d "store_name = store_1, division_name = dallas" "<REST_API_Endpoint>
```

The input parameters can also be put in a JSON file and when using 'curl' command, use "**--data-binary**' option to specify the JSON file name.
```
  $ curl --data-binary @MyInput.json "<DSE_Spark_Jobserver_IP>:8090/jobs?appName=invdel&classPath=com.example.InventoryCleanup_cntx"
  
  $ cat MyInput.json
  {
    store_name: "store_1",
    division_name: "dallas"
  }
```

# Manage Jobserver Context

## Ad-hoc, Transient Context

So far, the application program (***InventoryCleanup_cntx.scala***) has been executed through Spark Jobserver in an ad-hoc, transient mode. This means that Spark Jobserver will create a temporary, transient execution context (in this case, SparkContext) to submit the application to DSE cluster for execution.

The ad-hoc transient context provided (as default) by Spark jobserve, however, may not statisfy certain requirements. For example, if we want to run Spark SQL code and requires other types contexts, or just SparkSession (for Spark 2.0+), as per another demo program (***InventoryCleanup_sesn.scala***) in this repo, we'll run into the following error:

```
  $ curl -d "store_name = store_2" "<DSE_Spark_Jobserver_IP>:8090/jobs/?appName=invdel2&classPath=com.example.InventoryCleanup_sesn"
  {
    "status": "ERROR",
    "result": "Invalid job type for this context"
  }
```

## Pre-created, Permanent Context

In this case, we need to create in advance a non-default, customized context of the specified type. Because this type of context is pre-created, it is faster than the ad-hoc, transient context. For more detailed description of custom contexts, please refer to the document [OSS Spark Jobserver: contexts.md](https://github.com/spark-jobserver/spark-jobserver/blob/452d4b66e82466765d82498d61394f441a717c12/doc/contexts.md)

For the demo program  (***InventoryCleanup_sesn.scala***), we need to create a context from **spark.jobserver.context.SessionContextFactory** context factory type. 

The command below creates a "SparkSession" type context in Spark Sqlserver and name it as **MySessionContext**, with 2 CPU cores (***num-cpu-cores=2***) and 512 MB executor memory per node (memory-per-node=512M).

```
  $ curl -d "" "http://<DSE_Spark_Jobserver_IP>:8090/contexts/MySessionContext?num-cpu-cores=2&memory-per-node=512M&context- factory=spark.jobserver.context.SessionContextFactory"
  HTTP/1.1 200 OK
  Server: spray-can/1.3.4
  Date: Fri, 26 Apr 2019 20:29:35 GMT
  Access-Control-Allow-Origin: *
  Content-Type: application/json; charset=UTF-8
  Content-Length: 60

  {
    "status": "SUCCESS",
    "result": "Context initialized"
  }
```

We can view and delete pre-created contexts through the following commands respectively:

```
  $ curl -X GET <DSE_Spark_Jobserver_IP>:8090/contexts
  ["MySessionContext"]

  $ curl -X DELETE <DSE_Spark_Jobserver_IP>:8090/contexts/MySessionContext
  {
    "status": "SUCCESS",
   "result": "Context stopped"
  }
```

Once the required type of context is available, we can execute the application with the following command. Please note that last part of the command **&conext=MySessionContext** is where the pre-created permanent context is specified for the job execution.

```
  $ curl -d "store_name = store_2" "<DSE_Spark_Jobserver_IP>:8090/jobs/?appName=invdel2&classPath=com.example.InventoryCleanup_sesn&conext=MySessionContext"
  {
    "duration": "Job not done yet",
   "classPath": "com.example.InventoryCleanup_sesn",
   "startTime": "2019-04-27T03:18:11.829Z",
   "context": "MySqlContext",
   "status": "STARTED",
   "jobId": "4e183b91-75a3-45c3-a923-ad63daf1de72"
  }
```

### Intialize a Pre-created, Permanent Context Automatically

Instead of manually creating a permanent context every time, we can automate its creation by adding the following configuration in DSE Spark Jobserver main configuration file (e.g. /usr/share/dse/spark/spark-jobserver/dse.conf)

```
  #predefined Spark contexts
  contexts {
    MySessionContext {
      num-cpu-cores = 2             # Number of cores to allocate.  Required.
      memory-per-node = 512m         # Executor memory per node, -Xmx style eg 512m, 1G, etc.
    }
    
    # define additional contexts here
  }
```


# TODO - Future Work
2) DSE User authentication
3) HTTPS / DSE SSL

