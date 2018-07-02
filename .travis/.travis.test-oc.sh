#!/bin/bash

DIR="${DIR:-$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )}"

cluster_up() {
  echo -e "\n$(tput setaf 3)docker images:$(tput sgr0)\n"
  docker images
  echo
  set -x
  oc cluster up
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
  oc get all
  echo -e "\n$(tput setaf 3)Logs:$(tput sgr0)\n"
  oc logs `oc get pod -l app.kubernetes.io/name=spark-operator -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'`
  echo
}

errorLogs() {
  echo -e "\n\n(tput setaf 1)BUILD FAILED$(tput sgr0)\n\n:scream: bad things have happened :scream:"
  logs
  exit 1
}

testCreateOperator() {
  os::cmd::expect_success_and_text "oc create -f $DIR/../manifest/" 'deployment "spark-operator" created' && \
  os::cmd::try_until_text "oc get pod -l app.kubernetes.io/name=spark-operator -o yaml" 'ready: true'
}

testCreateCluster1() {
  os::cmd::expect_success_and_text "oc create -f $DIR/../examples/cluster.yaml" 'configmap "my-spark-cluster" created' && \
  os::cmd::try_until_text "oc get pod -l radanalytics.io/deployment=my-spark-cluster-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "oc get pod -l radanalytics.io/deployment=my-spark-cluster-m -o yaml" 'ready: true'
}

testCreateCluster2() {
  os::cmd::expect_success_and_text "oc create -f $DIR/../examples/with-prepared-data.yaml" 'configmap "spark-cluster-with-data" created' && \
  os::cmd::try_until_text "oc get pod -l radanalytics.io/deployment=spark-cluster-with-data-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "oc get pod -l radanalytics.io/deployment=spark-cluster-with-data-m -o yaml" 'ready: true'
}

testDownloadedData() {
  sleep 3
  local worker_pod=`oc get pod -l radanalytics.io/deployment=spark-cluster-with-data-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'` && \
  os::cmd::expect_success_and_text "oc exec $worker_pod ls" 'LA.csv' && \
  os::cmd::expect_success_and_text "oc exec $worker_pod ls" 'rows.csv'
}

testScaleCluster() {
  os::cmd::expect_success_and_text 'oc patch cm my-spark-cluster -p "{\"data\":{\"config\": \"workerNodes: 1\"}}"' 'configmap "my-spark-cluster" patched' && \
  os::cmd::try_until_text "oc get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster | wc -l" '2'
}

testDeleteCluster() {
  os::cmd::expect_success_and_text 'oc delete cm my-spark-cluster' 'configmap "my-spark-cluster" deleted' && \
  os::cmd::try_until_text "oc get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster 2> /dev/null | wc -l" '0'
}

run_tests() {
  os::test::junit::declare_suite_start "operator/tests"
  testCreateOperator || errorLogs
  testCreateCluster1 || errorLogs
  testScaleCluster || errorLogs
  testDeleteCluster || errorLogs

  testCreateCluster2 || errorLogs
  testDownloadedData || errorLogs
  os::test::junit::declare_suite_end
  logs
}

main() {
  cluster_up
  setup_testing_framework
  run_tests
  tear_down
}

main

