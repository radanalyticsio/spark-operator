#!/bin/bash

set -xe

BRANCH="dist"
REPO="Jiri-Kremser/spark-operator"

generate() {
  cat manifest/openshift/{rbac-openshift,operator}.yaml > /tmp/openshift-spark-operator.yaml
  cat manifest/kubernetes/{rbac-kubernetes,operator}.yaml > /tmp/k8s-spark-operator.yaml
}

setup-git() {
  git config --local user.email "jkremser@redhat.com"
  git config --local user.name "Jirka Kremser"
  set +x
  git remote add upstream https://${GH_TOKEN}@github.com/$REPO.git > /dev/null 2>&1
  set -x
}

switch-branch() {
  git fetch origin +refs/heads/$BRANCH:refs/remotes/origin/$BRANCH
  git remote set-branches --add origin $BRANCH
  git checkout --track -b $BRANCH origin/$BRANCH
  git pull
}

copy() {
  cp /tmp/openshift-spark-operator.yaml .
  cp /tmp/k8s-spark-operator.yaml .
}

commit() {
  git add -A
  git commit -m "Travis build: $TRAVIS_BUILD_NUMBER"
}

push() {
  set +x
  git push --quiet --set-upstream upstream $BRANCH
  set -x
}

main() {
  setup-git
  generate
  switch-branch
  copy
  commit
  push
}

main
