#!/bin/bash

DIR="${DIR:-$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )}"

cluster_up() {
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

testCreateOperator() {
  os::test::junit::declare_suite_start "operator/create"
  os::cmd::expect_success_and_text "oc create -f $DIR/../manifest/" 'deployment "spark-operator" created'
  os::cmd::try_until_text "oc get pod -l app.kubernetes.io/name=spark-operator -o yaml" 'ready: true'
  os::test::junit::declare_suite_end
}

testCreateCluster1() {
  os::test::junit::declare_suite_start "cluster/create1"
  os::cmd::expect_success_and_text "oc create -f $DIR/../examples/cluster.yaml" 'configmap "my-spark-cluster" created'
  os::cmd::try_until_text "oc get pod -l radanalytics.io/deployment=my-spark-cluster-w -o yaml" 'ready: true'
  os::cmd::try_until_text "oc get pod -l radanalytics.io/deployment=my-spark-cluster-m -o yaml" 'ready: true'
  os::test::junit::declare_suite_end
}

testCreateCluster2() {
  os::test::junit::declare_suite_start "cluster/create2"
  os::cmd::expect_success_and_text "oc create -f $DIR/../examples/with-prepared-data.yaml" 'configmap "spark-cluster-with-data" created'
  os::cmd::try_until_text "oc get pod -l radanalytics.io/deployment=spark-cluster-with-data-w -o yaml" 'ready: true'
  os::cmd::try_until_text "oc get pod -l radanalytics.io/deployment=spark-cluster-with-data-m -o yaml" 'ready: true'
  os::test::junit::declare_suite_end
}

testDownloadedData() {
  os::test::junit::declare_suite_start "cluster/downloaded"
  sleep 3
  local worker_pod=`oc get pod -l radanalytics.io/deployment=spark-cluster-with-data-w -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'`

  os::cmd::expect_success_and_text "oc exec $worker_pod ls" 'LA.csv'
  os::cmd::expect_success_and_text "oc exec $worker_pod ls" 'rows.csv'
  os::test::junit::declare_suite_end
}

testScaleCluster() {
  os::test::junit::declare_suite_start "cluster/scale"
  os::cmd::expect_success_and_text 'oc patch cm my-spark-cluster -p "{\"data\":{\"config\": \"workerNodes: 1\"}}"' 'configmap "my-spark-cluster" patched'
  os::cmd::try_until_text "oc get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster | wc -l" '2'
  os::test::junit::declare_suite_end
}

testDeleteCluster() {
  os::test::junit::declare_suite_start "cluster/delete"
  os::cmd::expect_success_and_text 'oc delete cm my-spark-cluster' 'configmap "my-spark-cluster" deleted'
  os::cmd::try_until_text "oc get pods --no-headers -l radanalytics.io/cluster=my-spark-cluster 2> /dev/null | wc -l" '0'
  os::test::junit::declare_suite_end
}

run_tests() {
  testCreateOperator
  testCreateCluster1
  testScaleCluster
  testDeleteCluster

  testCreateCluster2
  testDownloadedData
}

main() {
  cluster_up
  setup_testing_framework
  run_tests
  tear_down
}

main

