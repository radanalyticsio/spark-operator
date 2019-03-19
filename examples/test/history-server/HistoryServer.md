# Using shared volume

Assumptions:
* Make sure the `/tmp/spark-events` on host is writable and readable by 'others', or use different directory in the PVs.
* Spark 2.4 is installed locally and `spark-submit` is on `$PATH`

```
oc apply -f examples/test/history-server/sharedVolume/
```

```
oc get route
```

Open `http://my-history-server-default.127.0.0.1.nip.io/` or similar url in browser.

```
oc get pods -lradanalytics.io/podType=master -owide
```

Instead of `172.17.0.2`, use the correct ip from the command above
```
_jar_path=`type -P spark-submit | xargs dirname`/../examples/jars/spark-examples_*.jar
spark-submit --master spark://172.17.0.2:7077 \
 --conf spark.eventLog.enabled=true \
 --conf spark.eventLog.dir=/tmp/spark-events/ \
 --class org.apache.spark.examples.SparkPi \
 --executor-memory 1G \
 $_jar_path 42
```


# Using external object storage

Assumptions:
* `aws` client is installed and configured on `$PATH`
* Spark 2.4 is installed locally and `spark-submit` is on `$PATH`



Deploy ceph-nano, Minio or use S3 from Amazon directly:

```
oc --as system:admin adm policy add-scc-to-user anyuid system:serviceaccount:default:default
oc apply -f examples/test/history-server/externalStorage/
```

Configure the aws client:

```
aws configure
AWS Access Key ID = foo
AWS Secret Access Key = bar
```

Create new emtpy bucket for the event log called `my-history-server`:

```
_ceph=http://`oc get svc ceph-nano-0 --template={{.spec.clusterIP}}`:8000
aws s3api create-bucket --bucket my-history-server --endpoint-url=$_ceph
```

```
oc get route
```

Open `http://my-history-server-default.127.0.0.1.nip.io/` or similar url in browser.


```
oc get pods -lradanalytics.io/podType=master -owide
```

Instead of `172.17.0.2`, use the correct ip from the command above

```
_jar_path=`type -P spark-submit | xargs dirname`/../examples/jars/spark-examples_*.jar
spark-submit --master spark://172.17.0.2:7077 \
 --packages com.amazonaws:aws-java-sdk-pom:1.10.34,org.apache.hadoop:hadoop-aws:2.7.3 \
 --conf spark.eventLog.enabled=true \
 --conf spark.eventLog.dir=s3a://my-history-server/ \
 --conf spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem \
 --conf spark.hadoop.fs.s3a.access.key=foo \
 --conf spark.hadoop.fs.s3a.secret.key=bar \
 --conf spark.hadoop.fs.s3a.endpoint=$_ceph \
 --conf spark.driver.extraJavaOptions=-Dcom.amazonaws.services.s3.enableV4=true \
 --class org.apache.spark.examples.SparkPi \
 --executor-memory 1G \
 $_jar_path 42
 ```

 Check if the event has been written to the bucket:

 ```
 aws s3 ls s3://my-history-server/ --endpoint-url=$_ceph
 ```
