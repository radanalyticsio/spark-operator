#!/bin/bash

set -xe

download_kubectl() {
  echo "Downloading kubectl binary for KUBECTL_VERSION=${KUBECTL_VERSION}"
  curl -Lo kubectl https://storage.googleapis.com/kubernetes-release/release/v1.9.0/bin/linux/amd64/kubectl && \
  chmod +x kubectl && \
  sudo mv kubectl /usr/local/bin/ && \
  kubectl version
}

download_minikube() {
  echo "Downloading minikube binary for MINIKUBE_VERSION=${MINIKUBE_VERSION}"
  curl -Lo minikube https://storage.googleapis.com/minikube/releases/v0.25.2/minikube-linux-amd64 && \
  chmod +x minikube && \
  sudo mv minikube /usr/local/bin/ && \
  minikube version
}

setup_manifest() {
  sed -i'' 's;imagePullPolicy: IfNotPresent;imagePullPolicy: Never;g' manifest/operator.yaml
}

main() {
  download_kubectl
  download_minikube
  setup_manifest
}

main