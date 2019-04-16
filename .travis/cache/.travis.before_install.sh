#!/bin/bash

set -x

DIR="${DIR:-$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )}"

main() {
  docker_cache
  maven_cache
}

docker_cache(){
  if [[ "$TRAVIS_JOB_NUMBER" == *.1 ]] || [[ "$TRAVIS_JOB_NUMBER" == *.10 ]]; then
    images="${DIR}/container-images-common.txt"
  else
    if [[ "$BIN" = "oc" ]]; then
      images="${DIR}/container-images-common.txt ${DIR}/container-images-oc.txt"
    elif [[ "$BIN" = "kubectl" ]]; then
      images="${DIR}/container-images-common.txt ${DIR}/container-images-k8s.txt"
    else
      echo "Unknown or empty \$BIN variable, skipping before-cache script.."
      exit 1
    fi
  fi

  if [[ -d $HOME/docker ]] && [[ -e $HOME/docker/${BIN:-oc}-list.txt ]]; then
    cat ${images} | while read c
    do
      cat $HOME/docker/${BIN:-oc}-list.txt | grep "$c" | xargs -n 2 sh -c 'test -e $HOME/docker/$1.tar.gz && (zcat $HOME/docker/$1.tar.gz | docker load) || true'

      # make sure it's there, this should be a cheap operation if the previous command was successful
      docker pull ${c}
    done
  fi
}

maven_cache(){
  mkdir -p $HOME/.m2/repository/
  cp -r $HOME/maven $HOME/.m2/repository/
}

main
