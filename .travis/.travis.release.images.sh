#!/bin/bash

set -xe

OWNER="${OWNER:-jkremser}"
IMAGE="${IMAGE:-spark-operator}"
[ "$TRAVIS_BRANCH" = "master" -a "$TRAVIS_PULL_REQUEST" = "false" ] && LATEST=1

main() {
  if [[ "$LATEST" = "1" ]]; then
    echo "Pushing the :latest and :latest-centos images to docker.io and quay.io"
    loginDockerIo
    pushLatestImagesDockerIo
    loginQuayIo
    pushLatestImagesQuayIo
  elif [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Pushing the '${TRAVIS_TAG}' and :latest-released images to docker.io and quay.io"
    buildReleaseImages
    loginDockerIo
    pushReleaseImages "docker.io"
    loginQuayIo
    pushReleaseImages "quay.io"
  else
    echo "Not doing the docker push, because the tag '${TRAVIS_TAG}' is not of form x.y.z.Final"
    echo "and also it's not a build of the master branch"
  fi
}

loginDockerIo() {
  set +x
  docker login -u "$DOCKER_USERNAME" -p "$DOCKER_PASSWORD"
  set -x
}

loginQuayIo() {
  set +x
  docker login -u "$DOCKER_USERNAME" -p "$QUAY_PASSWORD" quay.io
  set -x
}

pushLatestImagesDockerIo() {
  make image-publish-all
  docker logout
}

pushLatestImagesQuayIo() {
  docker tag $OWNER/$IMAGE:latest "quay.io/"$OWNER/$IMAGE:latest
  docker tag $OWNER/$IMAGE:latest-centos "quay.io/"$OWNER/$IMAGE:latest-centos
  docker push "quay.io/"$OWNER/$IMAGE:latest
  docker push "quay.io/"$OWNER/$IMAGE:latest-centos
}

buildReleaseImages() {
  # build centos image
  make build-travis image-build-all
}

pushReleaseImages() {
  if [[ $# != 1 ]] && [[ $# != 2 ]]; then
    echo "Usage: pushReleaseImages image_repo" && exit
  fi
  REPO="$1"

  docker tag $OWNER/$IMAGE:slim $REPO/$OWNER/$IMAGE:${TRAVIS_TAG}
  docker tag $OWNER/$IMAGE:slim $REPO/$OWNER/$IMAGE:latest-released
  docker tag $OWNER/$IMAGE:centos $REPO/$OWNER/$IMAGE:${TRAVIS_TAG}-centos
  docker tag $OWNER/$IMAGE:centos $REPO/$OWNER/$IMAGE:latest-released-centos

  # push the latest-released and ${TRAVIS_TAG} images (and also -centos images)
  docker push $REPO/$OWNER/$IMAGE:${TRAVIS_TAG}
  docker push $REPO/$OWNER/$IMAGE:latest-released
  docker push $REPO/$OWNER/$IMAGE:${TRAVIS_TAG}-centos
  docker push $REPO/$OWNER/$IMAGE:latest-released-centos
  docker logout
}

main
