#!/bin/bash

DIR="${DIR:-$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )}"
BIN=${BIN:-oc}
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
  [ "$CRD" = "1" ] && FOO="-crd" || FOO=""
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../manifest/operator$FOO.yaml" '"?spark-operator"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l app.kubernetes.io/name=spark-operator -o yaml" 'ready: true'
  if [ "$CRD" = "1" ]; then
    os::cmd::try_until_text "${BIN} get crd" 'sparkclusters.radanalytics.io'
  fi
  sleep 10
  export operator_pod=`${BIN} get pod -l app.kubernetes.io/name=spark-operator -o='jsonpath="{.items[0].metadata.name}"' | sed 's/"//g'`
}

testCreateCluster() {
  info
  [ "$CRD" = "1" ] && FOO="-cr" || FOO=""
  os::cmd::expect_success_and_text "${BIN} create -f $DIR/../examples/cluster$FOO.yaml" '"?my-spark-cluster"? created' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=my-spark-cluster-w -o yaml" 'ready: true' && \
  os::cmd::try_until_text "${BIN} get pod -l radanalytics.io/deployment=my-spark-cluster-m -o yaml" 'ready: true'
}

testKillOperator() {
  info
  os::cmd::expect_success_and_text "${BIN} delete pod $operator_pod" 'pod "?'$operator_pod'"? deleted' && \
  sleep 7
}

testScaleCluster() {
  info
  if [ "$CRD" = "1" ]; then
    os::cmd::expect_success_and_text '${BIN} patch sparkcluster my-spark-cluster -p "{\"spec\":{\"worker\": {\"replicas\": 1}}}" --type=merge' '"?my-spark-cluster"? patched' || errorLogs
  else
    os::cmd::expect_success_and_text '${BIN} patch cm my-spark-cluster -p "{\"data\":{\"config\": \"worker:\n  replicas: 1\"}}"' '"?my-spark-cluster"? patched' || errorLogs
  fi
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=my-spark-cluster | wc -l" '2'
}

testDeleteCluster() {
  info
  os::cmd::expect_success_and_text '${BIN} delete ${KIND} my-spark-cluster' '"?my-spark-cluster"? deleted' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/sparkcluster=my-spark-cluster 2> /dev/null | wc -l" '0'
}

testApp() {
  info
  [ "$CRD" = "1" ] && FOO="test/cr/" || FOO=""
  os::cmd::expect_success_and_text '${BIN} create -f examples/${FOO}app.yaml' '"?my-spark-app"? created' && \
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
  [ "$CRD" = "1" ] && FOO="app" || FOO="cm"
  os::cmd::expect_success_and_text '${BIN} delete ${FOO} my-spark-app' '"my-spark-app" deleted' && \
  os::cmd::try_until_text "${BIN} get pods --no-headers -l radanalytics.io/app=my-spark-app 2> /dev/null | wc -l" '0'
}

run_tests() {
  testKillOperator || errorLogs
  testCreateCluster || errorLogs
  testKillOperator || errorLogs
  testScaleCluster || errorLogs
  testKillOperator || errorLogs
  testDeleteCluster || errorLogs
  testKillOperator || errorLogs

  sleep 10
  testApp || errorLogs
  testKillOperator || errorLogs
  testAppResult || errorLogs
  logs
}

main() {
  export total=17
  export testIndex=0
  tear_down
  setup_testing_framework
  os::test::junit::declare_suite_start "operator/tests-restarts"
  cluster_up
  testCreateOperator || { ${BIN} get events; ${BIN} get pods; exit 1; }
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
