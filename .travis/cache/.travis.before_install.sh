#!/bin/bash

set -x

main() {
  docker_cache
  maven_cache
}


docker_cache(){
  if [[ "$BIN" = "oc" ]]; then
    specific=container-images-oc.txt
  elif [[ "$BIN" = "kubectl" ]]; then
    specific=container-images-k8s.txt
  else
    echo "Unknown or empty \$BIN variable, skipping before-cache script.."
    exit 1
  fi

  if [[ "$TRAVIS_JOB_NUMBER" == *.1 ]] || [[ "$TRAVIS_JOB_NUMBER" == *.10 ]]; then
    images="container-images-common.txt"
  else
    images="container-images-common.txt ${specific}"
  fi

  if [[ -d $HOME/docker ]] && [[ -e $HOME/docker/${BIN}-list.txt ]]; then
    cat ${images} | while read c
    do
      cat ${BIN}-$HOME/docker/list.txt | grep "$c" | xargs -n 2 sh -c 'test -e $HOME/docker/$1.tar.gz && (zcat $HOME/docker/$1.tar.gz | docker load) || true'
    done
  fi
}

maven_cache(){
  cp -r $HOME/.m2/repository $HOME/.m2/repository/
}

main
