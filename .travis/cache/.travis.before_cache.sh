#!/bin/bash

set -x

main() {
  docker_cache
  maven_cache
}

docker_cache(){
  if [[ "$TRAVIS_JOB_NUMBER" == *.1 ]] || [[ "$TRAVIS_JOB_NUMBER" == *.10 ]]; then
    echo "Skipping docker cache for .1 and .10 jobs"
    exit 0
  fi

  if [[ "$BIN" = "oc" ]]; then
    specific=container-images-oc.txt
  elif [[ "$BIN" = "kubectl" ]]; then
    specific=container-images-k8s.txt
  else
    echo "Unknown or empty \$BIN variable, skipping before-cache script.."
    exit 1
  fi

  mkdir -p $HOME/docker
  docker images -a --filter='dangling=false' --format '{{.Repository}}:{{.Tag}} {{.ID}}' > $HOME/docker/${BIN}-list.txt
  cat container-images-common.txt ${specific} | while read c
  do
    cat $HOME/docker/${BIN}-list.txt | grep "$c" | xargs -n 2 -t sh -c 'test -e $HOME/docker/$1.tar.gz || docker save $0 | gzip -2 > $HOME/docker/$1.tar.gz'
  done
}

maven_cache(){
  mkdir -p $HOME/maven
  cd $HOME/.m2/repository
  tar cf - --exclude=io/radanalytics . | (cd $HOME/maven && tar xf - )
}

main
