#!/bin/bash

set -xe

BRANCH="dist"

cat manifest/openshift/{rbac-openshift,operator}.yaml > openshift-spark-operator.yaml
cat manifest/kubernetes/{rbac-kubernetes,operator}.yaml > k8s-spark-operator.yaml