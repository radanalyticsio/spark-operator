#!/bin/bash

DIR="${DIR:-$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )}"
BIN=${BIN:-oc}

cluster_up() {
  echo -e "\n$(tput setaf 3)docker images:$(tput sgr0)\n"
  docker images
  echo
  if [ "$BIN" = "oc" ]; then
    set -x
    oc cluster up
    set +x
  else
    echo "minikube"
    start_minikube
    eval `minikube docker-env`
  fi
}

start_minikube() {
  set -x
  export CHANGE_MINIKUBE_NONE_USER=true
  sudo minikube start --vm-driver=none --kubernetes-version=${VERSION} && \
  minikube update-context
  JSONPATH='{range .items[*]}{@.metadata.name}:{range @.status.conditions[*]}{@.type}={@.status};{end}{end}'
  until kubectl get nodes -o jsonpath="$JSONPATH" 2>&1 | grep -q "Ready=True"; do sleep 1; done
  kubectl cluster-info

  # kube-addon-manager is responsible for managing other k8s components, such as kube-dns, dashboard, storage-provisioner..
  JSONPATH='{range .items[*]}{@.metadata.name}:{range @.status.conditions[*]}{@.type}={@.status};{end}{end}'
  until kubectl -n kube-system get pods -lcomponent=kube-addon-manager -o jsonpath="$JSONPATH" 2>&1 | grep -q "Ready=True"; do
    sleep 1
    echo "waiting for kube-addon-manager to be available"
    kubectl get pods
  done

  # Wait for kube-dns to be ready.
  JSONPATH='{range .items[*]}{@.metadata.name}:{range @.status.conditions[*]}{@.type}={@.status};{end}{end}'
  until kubectl -n kube-system get pods -lk8s-app=kube-dns -o jsonpath="$JSONPATH" 2>&1 | grep -q "Ready=True"; do
    sleep 1
    echo "waiting for kube-dns to be available"
    kubectl get pods
  done
  set +x
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
  ${BIN} logs $operator_pod
  echo
}

errorLogs() {
  echo -e "\n\n$(tput setaf 1)\n  😱 😱 😱\nBUILD FAILED\n\n😱 bad things have happened 😱$(tput sgr0)"
  logs
  exit 1
}

info() {
  ((testIndex++))
  echo "$(tput setaf 3)[$testIndex / $total] - Running ${FUNCNAME[1]}$(tput sgr0)"
}

testCreateOperator() {
  info
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../manifest/" '"?spark-operator"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l app.kubernetes.io/name=spark-operator -o yaml" 'ready: true'
}

testCreateCluster1() {
  info
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/cluster.yaml" '"?my-spark-cluster"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=my-spark-cluster-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=my-spark-cluster-m -o yaml" 'ready: true'
}

testScaleCluster() {
  info
  os::cmd::expect_success_and_text '${BIN} patch cm my-spark-cluster -p "{\"data\":{\"config\": \"workerNodes: 1\"}}"' '"?my-spark-cluster"? patched' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster | wc -l" '2'
}

testDeleteCluster() {
  info
  os::cmd::expect_success_and_text '${BIN} delete cm my-spark-cluster' '"?my-spark-cluster"? deleted' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster 2> /dev/null | wc -l" '0'
}

testCreateCluster2() {
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/with-prepared-data.yaml" '"?spark-cluster-with-data"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=spark-cluster-with-data-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=spark-cluster-with-data-m -o yaml" 'ready: true'
}

testDownloadedData() {
  info
  sleep 2
  local worker_pod=`${BIN} get pod -l radanalytics.io/deployment=spark-cluster-with-data-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod ls" 'LA.csv' && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod ls" 'rows.csv' && \
  os::cmd::expect_success_and_text '${BIN} delete cm spark-cluster-with-data' 'configmap "spark-cluster-with-data" deleted'
}

testFullConfigCluster() {
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create cm my-config --from-file=$DIR/../examples/spark-defaults.conf" '"?my-config"? created' && \
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/cluster-with-config.yaml" '"?sparky-cluster"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-m -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=sparky-cluster | wc -l" '3' && \
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
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/cluster-1.yaml" '"?my-spark-cluster-1"? created' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod" 'Unable to parse yaml definition of configmap' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod" 'w0rkerNodes' && \
  os::cmd::expect_success_and_text '${BIN} delete cm my-spark-cluster-1' 'configmap "my-spark-cluster-1" deleted'
}

testCustomCluster2() {
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/cluster-2.yaml" '"?my-spark-cluster-2"? created' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod | grep my-spark-cluster-2" "created" && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster-2 | wc -l" '3' && \
  os::cmd::expect_success_and_text '${BIN} delete cm my-spark-cluster-2' 'configmap "my-spark-cluster-2" deleted' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod | grep my-spark-cluster-2" "deleted" && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster-2 | wc -l" '0'
}

testCustomCluster3() {
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/cluster-with-config-1.yaml" '"?sparky-cluster-1"? created' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=sparky-cluster-1 | wc -l" '2' && \
  local worker_pod=`${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-1-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::try_until_text "${BIN} exec $worker_pod cat /opt/spark/conf/spark-defaults.conf" 'spark.executor.memory 1g' && \
  os::cmd::expect_success_and_text "${BIN} exec $worker_pod ls" 'README.md' && \
  os::cmd::expect_success_and_text '${BIN} delete cm sparky-cluster-1' 'configmap "sparky-cluster-1" deleted'
}

testCustomCluster4() {
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/cluster-with-config-2.yaml" '"?sparky-cluster-2"? created' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=sparky-cluster-2 | wc -l" '2' && \
  local worker_pod=`${BIN} get pod -l radanalytics.io/deployment=sparky-cluster-2-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::try_until_text "${BIN} exec $worker_pod cat /opt/spark/conf/spark-defaults.conf" 'spark.executor.memory 3g' && \
  os::cmd::expect_success_and_text '${BIN} delete cm sparky-cluster-2' 'configmap "sparky-cluster-2" deleted'
}

testCustomCluster5() {
  # empty config map should just works with the defaults
  info
  sleep 2
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/test/cluster-with-config-3.yaml" '"?sparky-cluster-3"? created' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=sparky-cluster-3 | wc -l" '2' && \
  os::cmd::try_until_text "${BIN} logs $operator_pod | grep sparky-cluster-3" "created" && \
  os::cmd::expect_success_and_text '${BIN} delete cm sparky-cluster-3' 'configmap "sparky-cluster-3" deleted' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster-3 | wc -l" '0'
}

testApp() {
  info
  os::cmd::expect_success_and_text '${BIN} create -f examples/app.yaml' '"?my-spark-app"? created' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/app=my-spark-app 2> /dev/null | wc -l" '3'
}

testAppResult() {
  info
  sleep 2
  local driver_pod=`${BIN} get pods --no-headers -l radanalytics.io/app=my-spark-app -l spark-role=driver -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::try_until_text "${BIN} logs $driver_pod" 'Pi is roughly 3.1'
}

testDeleteApp() {
  info
  os::cmd::expect_success_and_text '${BIN} delete cm my-spark-app' 'configmap "my-spark-app" deleted' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/app=my-spark-app 2> /dev/null | wc -l" '0'
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
  testApp || errorLogs
  testAppResult || errorLogs
  testDeleteApp || errorLogs
  logs
}

main() {
  export total=15
  export testIndex=0
  tear_down
  cluster_up
  setup_testing_framework
  os::test::junit::declare_suite_start "operator/tests"
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
