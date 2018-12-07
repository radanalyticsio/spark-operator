#!/bin/bash

DIR="${DIR:-$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )}"
BIN=${BIN:-oc}
MANIFEST_SUFIX=${MANIFEST_SUFIX:-""}
if [ "$CRD" = "1" ]; then
  CR="cr/"
  KIND="sparkcluster"
else
  CR=""
  KIND="cm"
fi

cluster_up() {
  echo -e "\n$(tput setaf 3)docker images:$(tput sgr0)\n"
  docker images
  echo
  if [ "$BIN" = "oc" ]; then
    set -x
    oc cluster up
    [ "$CRD" = "1" ] && oc login -u system:admin
    set +x
  else
    echo "minikube"
    start_minikube
  fi
}

start_minikube() {
  export CHANGE_MINIKUBE_NONE_USER=true
  sudo minikube start --vm-driver=none --kubernetes-version=${VERSION} && \
  minikube update-context
  os::cmd::try_until_text "${BIN} get nodes" '\sReady'

  kubectl cluster-info


  # kube-addon-manager is responsible for managing other k8s components, such as kube-dns, dashboard, storage-provisioner..
  os::cmd::try_until_text "${BIN} -n kube-system get pod -lcomponent=kube-addon-manager -o yaml" 'ready: true'

  # Wait for kube-dns to be ready.
  os::cmd::try_until_text "${BIN} -n kube-system get pod -lk8s-app=kube-dns -o yaml" 'ready: true'
}

tear_down() {
  docker kill `docker ps -q` || true
}

setup_testing_framework() {
  source "$(dirname "${BASH_SOURCE}")/../test/lib/init.sh"
  os::util::environment::setup_time_vars
}

logs() {
  echo -e "\n$(tput setaf 3)oc get all:$(tput sgr0)\n"
  ${BIN} get all
  echo -e "\n$(tput setaf 3)Logs:$(tput sgr0)\n"
  ${BIN} logs $operator_pod || {
    export operator_pod=`${BIN} get pod -l app.kubernetes.io/name=spark-operator -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'`
    ${BIN} logs $operator_pod || true
  }
  echo
}

errorLogs() {
  echo -e "\n\n$(tput setaf 1)\n  😱 😱 😱\nBUILD FAILED\n\n😱 bad things have happened 😱$(tput sgr0)"
  logs
  exit 1
}

appErrorLogs() {
  echo -e "\n$(tput setaf 3)Spark Application Logs:$(tput sgr0)\n"
  export submitter_pod=`${BIN} get pod -l radanalytics.io/kind=sparkapplication -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'`
  ${BIN} get all
  ${BIN} logs $submitter_pod
  errorLogs
}

info() {
  ((testIndex++))
  echo "$(tput setaf 3)[$testIndex / $total] - Running ${FUNCNAME[1]}$(tput sgr0)"
}

testCreateOperator() {
  info
  [ "$CRD" = "1" ] && FOO="-crd" || FOO=""
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../manifest/operator$FOO$MANIFEST_SUFIX.yaml" '"?spark-operator"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l app.kubernetes.io/name=spark-operator -o yaml" 'ready: true'
  if [ "$CRD" = "1" ]; then
    os::cmd::try_until_text "${BIN} get crd" 'sparkclusters.radanalytics.io'
  fi
  sleep 10
}

testCreateCluster1() {
  info
  [ "$CRD" = "1" ] && FOO="-cr" || FOO=""
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/cluster$FOO.yaml" '"?my-spark-cluster"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=my-spark-cluster-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=my-spark-cluster-m -o yaml" 'ready: true'
}

testScaleCluster() {
  info
  if [ "$CRD" = "1" ]; then
    os::cmd::expect_success_and_text '${BIN} patch sparkcluster my-spark-cluster -p "{\"spec\":{\"worker\": {\"instances\": 1}}}" --type=merge' '"?my-spark-cluster"? patched' || errorLogs
  else
    os::cmd::expect_success_and_text '${BIN} patch cm my-spark-cluster -p "{\"data\":{\"config\": \"worker:\n  instances: 1\"}}"' '"?my-spark-cluster"? patched' || errorLogs
  fi
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=my-spark-cluster | wc -l" '2'
}

testDeleteCluster() {
  info
  os::cmd::expect_success_and_text '${BIN} delete ${KIND} my-spark-cluster' '"?my-spark-cluster"? deleted' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=my-spark-cluster 2> /dev/null | wc -l" '0'
}

testCreateCluster2() {
  info
  sleep 2
  [ "$CRD" = "1" ] && return 0
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/with-prepared-data.yaml" '"?spark-cluster-with-data"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=spark-cluster-with-data-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=spark-cluster-with-data-m -o yaml" 'ready: true'
}

testDownloadedData() {
  info
  sleep 2
  [ "$CRD" = "1" ] && return 0
  local worker_pod=`${BIN} get pod -l radanalytics.io/deployment=spark-cluster-with-data-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod ls" 'LA.csv' && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod ls" 'rows.csv' && \
  os::cmd::expect_success_and_text '${BIN} delete cm spark-cluster-with-data' 'configmap "spark-cluster-with-data" deleted'
}

testFullConfigCluster() {
  info
  sleep 2
  [ "$CRD" = "1" ] && return 0
  os::cmd::expect_success_and_text "${BIN} create cm my-config --from-file=$DIR/../examples/spark-defaults.conf" '"?my-config"? created' && \
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/cluster-with-config.yaml" '"?sparky-cluster"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-m -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=sparky-cluster | wc -l" '3' && \
  sleep 10 && \
  local worker_pod=`${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod ls" 'README.md' && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod env" 'FOO=bar' && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod env" 'SPARK_WORKER_CORES=2' && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod cat /opt/spark/conf/spark-defaults.conf" 'spark.history.retainedApplications 100' && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod cat /opt/spark/conf/spark-defaults.conf" 'autoBroadcastJoinThreshold 20971520' && \
  os::cmd::expect_success_and_text '${BIN} delete cm sparky-cluster' 'configmap "sparky-cluster" deleted'
}

testCustomCluster1() {
  info
  sleep 2
  [ "$CRD" = "1" ] && return 0
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/cluster-1.yaml" '"?my-spark-cluster-1"? created' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod" 'Unable to parse yaml definition of configmap' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod" 'w0rker' && \
  os::cmd::expect_success_and_text '${BIN} delete cm my-spark-cluster-1' 'configmap "my-spark-cluster-1" deleted'
}

testCustomCluster2() {
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/${CR}cluster-2.yaml" '"?my-spark-cluster-2"? created' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod | grep my-spark-cluster-2" "created" && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=my-spark-cluster-2 | wc -l" '3' && \
  os::cmd::expect_success_and_text '${BIN} delete ${KIND} my-spark-cluster-2' '"my-spark-cluster-2" deleted' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod | grep my-spark-cluster-2" "deleted" && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=my-spark-cluster-2 | wc -l" '0'
}

testCustomCluster3() {
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/${CR}cluster-with-config-1.yaml" '"?sparky-cluster-1"? created' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=sparky-cluster-1 | wc -l" '2' && \
  local worker_pod=`${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-1-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::try_until_text "${BIN} exec $worker_pod cat /opt/spark/conf/spark-defaults.conf" 'spark.executor.memory 1g' && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod ls" 'README.md' && \
  os::cmd::expect_success_and_text '${BIN} delete ${KIND} sparky-cluster-1' '"sparky-cluster-1" deleted'
}

testCustomCluster4() {
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/${CR}cluster-with-config-2.yaml" '"?sparky-cluster-2"? created' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=sparky-cluster-2 | wc -l" '2' && \
  local worker_pod=`${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-2-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::try_until_text "${BIN} exec $worker_pod cat /opt/spark/conf/spark-defaults.conf" 'spark.executor.memory 3g' && \
  os::cmd::expect_success_and_text '${BIN} delete ${KIND} sparky-cluster-2' '"sparky-cluster-2" deleted'
}

testCustomCluster5() {
  # empty config map should just works with the defaults
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/${CR}cluster-with-config-3.yaml" '"?sparky-cluster-3"? created' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=sparky-cluster-3 | wc -l" '2' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod | grep sparky-cluster-3" "created" && \
  os::cmd::expect_success_and_text '${BIN} delete ${KIND} sparky-cluster-3' '"sparky-cluster-3" deleted' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=my-spark-cluster-3 | wc -l" '0'
}

testApp() {
  info
  [ "$CRD" = "1" ] && FOO="test/cr/" || FOO=""
  os::cmd::expect_success_and_text '${BIN} create -f examples/${FOO}app.yaml' '"?my-spark-app"? created' && \
  # number of pods w/ spark app \geq to 3
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkapplication=my-spark-app 2> /dev/null | wc -l | sed -e 's/\(.*\)/\1>=3/' | bc -l" '1'
}

testAppResult() {
  info
  sleep 2
  local driver_pod=`${BIN} get pods --no-headers -l radanalytics.io/sparkapplication=my-spark-app -l spark-role=driver -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::try_until_text "${BIN} logs $driver_pod" 'Pi is roughly 3.1'
}

testDeleteApp() {
  info
  [ "$CRD" = "1" ] && FOO="sparkapplication" || FOO="cm"
  os::cmd::expect_success_and_text '${BIN} delete ${FOO} my-spark-app' '"my-spark-app" deleted' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkapplication=my-spark-app 2> /dev/null | wc -l" '0'
}

testPythonApp() {
  info
  [ "$CRD" = "1" ] && return 0
  os::cmd::expect_success_and_text '${BIN} create -f examples/apps/pyspark-ntlk.yaml' '"?ntlk-example"? created' && \
  # number of pods w/ spark app \geq to 3 (1 executor, 1 driver, 1 submitter)
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkapplication=ntlk-example 2> /dev/null | wc -l | sed -e 's/\(.*\)/\1>=3/' | bc -l" '1'
}

testPythonAppResult() {
  info
  [ "$CRD" = "1" ] && return 0
  sleep 2
  local driver_pod=`${BIN} get pods --no-headers -l radanalytics.io/sparkapplication=ntlk-example -l spark-role=driver -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::try_until_text "${BIN} logs $driver_pod" 'Lorem'
}

testMetricServer() {
  info
  os::cmd::expect_success_and_text '${BIN} set env deployment/spark-operator METRICS=true' 'updated' || errorLogs
  os::cmd::expect_success_and_text '${BIN} expose deployment spark-operator --port=8080' '"?spark-operator"? exposed' || errorLogs
  sleep 1
  os::cmd::try_until_text "${BIN} get pod -l app.kubernetes.io/name=spark-operator -o yaml" 'ready: true'
  local SVC_IP=`${BIN} get service/spark-operator -o='jsonpath="{.spec.clusterIP}"'|sed 's/"//g'`
  os::cmd::try_until_text "curl $SVC_IP:8080" 'operator_running_clusters'
  sleep 1
  export operator_pod=`${BIN} get pod -l app.kubernetes.io/name=spark-operator -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'`
}

run_custom_test() {
    testCustomCluster1 || errorLogs
    testCustomCluster2 || errorLogs
    testCustomCluster3 || errorLogs
    testCustomCluster4 || errorLogs
    testCustomCluster5 || errorLogs
}

run_tests() {
  testCreateCluster1 || errorLogs
  testScaleCluster || errorLogs
  testDeleteCluster || errorLogs
  sleep 5

  testCreateCluster2 || errorLogs
  testDownloadedData || errorLogs
  sleep 5

  testFullConfigCluster || errorLogs
  sleep 5

  run_custom_test || errorLogs

  sleep 10
  testApp || appErrorLogs
  testAppResult || appErrorLogs
  testDeleteApp || appErrorLogs

  sleep 5
  testPythonApp || appErrorLogs
  testPythonAppResult || appErrorLogs

  testMetricServer || errorLogs
  logs
}

main() {
  export total=18
  export testIndex=0
  tear_down
  setup_testing_framework
  os::test::junit::declare_suite_start "operator/tests"
  cluster_up
  testCreateOperator || { ${BIN} get events; ${BIN} get pods; exit 1; }
  export operator_pod=`${BIN} get pod -l app.kubernetes.io/name=spark-operator -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'`
  if [ "$#" -gt 0 ]; then
    # run single test that is passed as arg
    $1
  else
    run_tests
  fi
  os::test::junit::declare_suite_end
  tear_down
}

main $@
