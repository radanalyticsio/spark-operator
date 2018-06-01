#!/bin/bash

set -xe

BRANCH="dist"
REPO="Jiri-Kremser/spark-operator"

generate() {
  cat manifest/openshift/{rbac-openshift,operator}.yaml > openshift-spark-operator.yaml
  cat manifest/kubernetes/{rbac-kubernetes,operator}.yaml > k8s-spark-operator.yaml
}

setup-git() {
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "Travis CI"
  set +x
  git remote add upstream https://${GH_TOKEN}@github.com/$REPO.git > /dev/null 2>&1
  set -x
}

commit() {
  git checkout -b $BRANCH
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
  commit
  push
}

main
