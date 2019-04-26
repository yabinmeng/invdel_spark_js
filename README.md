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

### Submitting Spark Jobs to DSE from Spark Jobserver 

After the application jar file is uploaded, you can execute it from Spark Jobserver. The execution is actually done by DSE cluster because internally Spark Jobserver will submit the job to DSE cluster.

```
  $  curl -d "<App_Input_Parameters>" "<DSE_Spark_Jobserver_IP>:8090/jobs?appName=invdel&classPath=com.example.InventoryCleanup"
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


he screen output above shows the job ID assigned to the job. You can use it to query the on-going job status. In the example below, the job has successfully completed with customized result/repsonse as returned in the **result** field.
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
    "stack": "java.lang.ClassCastException: com.example.InventoryCleanup$ cannot be cast to spark.jobserver.api.SparkJobBase\n\tat spark.jobserver.context.ScalaContextFactory$class.loadAndValidateJob(SparkContextFactory.scala:87)\n\tat spark.jobserver.context.DefaultSparkContextFactory.loadAndValidateJob(SparkContextFactory.scala:139)\n\tat spark.jobserver.JobManagerActor.startJobInternal(JobManagerActor.scala:292)\n\tat spark.jobserver.JobManagerActor$$anonfun$wrappedReceive$1.applyOrElse(JobManagerActor.scala:192)\n\tat scala.runtime.AbstractPartialFunction.apply(AbstractPartialFunction.scala:36)\n\tat spark.jobserver.common.akka.ActorStack$$anonfun$receive$1.applyOrElse(ActorStack.scala:33)\n\tat scala.runtime.AbstractPartialFunction.apply(AbstractPartialFunction.scala:36)\n\tat spark.jobserver.common.akka.Slf4jLogging$$anonfun$receive$1$$anonfun$applyOrElse$1.apply$mcV$sp(Slf4jLogging.scala:25)\n\tat spark.jobserver.common.akka.Slf4jLogging$class.spark$jobserver$common$akka$Slf4jLogging$$withAkkaSourceLogging(Slf4jLogging.scala:34)\n\tat spark.jobserver.common.akka.Slf4jLogging$$anonfun$receive$1.applyOrElse(Slf4jLogging.scala:24)\n\tat scala.runtime.AbstractPartialFunction.apply(AbstractPartialFunction.scala:36)\n\tat spark.jobserver.common.akka.ActorMetrics$$anonfun$receive$1.applyOrElse(ActorMetrics.scala:23)\n\tat akka.actor.Actor$class.aroundReceive(Actor.scala:484)\n\tat spark.jobserver.common.akka.InstrumentedActor.aroundReceive(InstrumentedActor.scala:8)\n\tat akka.actor.ActorCell.receiveMessage(ActorCell.scala:526)\n\tat akka.actor.ActorCell.invoke(ActorCell.scala:495)\n\tat akka.dispatch.Mailbox.processMailbox(Mailbox.scala:257)\n\tat akka.dispatch.Mailbox.run(Mailbox.scala:224)\n\tat akka.dispatch.Mailbox.exec(Mailbox.scala:234)\n\tat scala.concurrent.forkjoin.ForkJoinTask.doExec(ForkJoinTask.java:260)\n\tat scala.concurrent.forkjoin.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1339)\n\tat scala.concurrent.forkjoin.ForkJoinPool.runWorker(ForkJoinPool.java:1979)\n\tat scala.concurrent.forkjoin.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:107)\n"
  }
}
```

Compared with a regular Spark application, the changes to be made for a Spark Jobserver ready Spark application are minimal and actually quite standard. I'll highlight these changes in the following sections.

## 


