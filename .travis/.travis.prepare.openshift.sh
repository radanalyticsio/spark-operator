#!/bin/bash

set -xe

download_openshift() {
  echo "Downloading oc binary for VERSION=${VERSION}"
  sudo docker cp $(docker create docker.io/openshift/origin:$VERSION):/bin/oc /usr/local/bin/oc
  oc version
}

setup_insecure_registry() {
  sudo cat /etc/default/docker
  sudo service docker stop
  sudo sed -i -e 's/sock/sock --insecure-registry 172.30.0.0\/16/' /etc/default/docker
  sudo cat /etc/default/docker
  sudo service docker start
  sudo service docker status
  sudo mount --make-rshared /
}

setup_manifest() {
  sed -i'' 's;imagePullPolicy: .*;imagePullPolicy: Never;g' manifest/operator.yaml
  sed -i'' 's;imagePullPolicy: .*;imagePullPolicy: Never;g' manifest/operator-crd.yaml
#  [ "$CRD" = "1" ] && sed -i'' '/^.*#- name: CRD$/{$!{N;s/^.*#- name: CRD\n.*#  value: "true"$/- name: CRD\n  value: true/;ty;P;D;:y}}' manifest/operator.yaml
  echo -e "'\nmanifest:\n-----------\n"
  cat manifest/operator.yaml
}

main() {
  download_openshift
  setup_insecure_registry
  setup_manifest
}

main
