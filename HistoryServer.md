```
oc apply -f examples/test/history-server/sharedVolume/
```

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
