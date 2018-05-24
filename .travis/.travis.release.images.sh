#!/bin/bash

set -xe

OWNER="${OWNER:-jkremser}"
IMAGE="${IMAGE:-oshinko-operator}"

# if building the release tag
if [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then

  # push the latest and ${TRAVIS_TAG} images
  docker tag $OWNER/$IMAGE:slim $OWNER/$IMAGE:${TRAVIS_TAG}-slim
  docker tag $OWNER/$IMAGE:centos $OWNER/$IMAGE:${TRAVIS_TAG}-centos
  docker push $OWNER/$IMAGE:${TRAVIS_TAG}-slim
  docker push $OWNER/$IMAGE:${TRAVIS_TAG}-centos
else
  echo "Not doing the docker push, because the tag '${TRAVIS_TAG}' is not of form x.y.z.Final"
fi