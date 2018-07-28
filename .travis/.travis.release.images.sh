#!/bin/bash

set -xe

OWNER="${OWNER:-jkremser}"
IMAGE="${IMAGE:-spark-operator}"
[ "$TRAVIS_BRANCH" = "master" -a "$TRAVIS_PULL_REQUEST" = "false" ] && LATEST=1

main() {
  if [[ "$LATEST" = "1" ]]; then
    echo "Pushing the :latest and :latest-centos images to docker.io and quay.io"
    loginDockerIo
    pushLatestImages
    loginQuayIo
    pushLatestImages
  else if [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Pushing the '${TRAVIS_TAG}' and :latest-released images to docker.io and quay.io"
    buildReleaseImages
    loginDockerIo
    pushReleaseImages
    loginQuayIo
    pushReleaseImages
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

pushLatestImages() {
  make image-publish-all
  docker logout
}

buildReleaseImages() {
  # build centos image
  make package image-build

  docker tag $OWNER/$IMAGE:slim $OWNER/$IMAGE:${TRAVIS_TAG}
  docker tag $OWNER/$IMAGE:slim $OWNER/$IMAGE:latest-released
  docker tag $OWNER/$IMAGE:centos $OWNER/$IMAGE:${TRAVIS_TAG}-centos
  docker tag $OWNER/$IMAGE:centos $OWNER/$IMAGE:latest-released-centos
}

pushReleaseImages() {
  # push the latest-released and ${TRAVIS_TAG} images (and also -centos images)
  docker push $OWNER/$IMAGE:${TRAVIS_TAG}
  docker push $OWNER/$IMAGE:latest-released
  docker push $OWNER/$IMAGE:${TRAVIS_TAG}-centos
  docker push $OWNER/$IMAGE:latest-released-centos
  docker logout
}

main