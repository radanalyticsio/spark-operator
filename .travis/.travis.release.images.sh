#!/bin/bash

set -xe

OWNER="${OWNER:-jkremser}"
IMAGE="${IMAGE:-spark-operator}"

# if building the release tag
if [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then

  # build centos image
  make package image-build

  # tag and push the latest-released and ${TRAVIS_TAG} images (and also -centos images)
  docker tag $OWNER/$IMAGE:slim $OWNER/$IMAGE:${TRAVIS_TAG}
  docker tag $OWNER/$IMAGE:slim $OWNER/$IMAGE:latest-released
  docker tag $OWNER/$IMAGE:centos $OWNER/$IMAGE:${TRAVIS_TAG}-centos
  docker tag $OWNER/$IMAGE:centos $OWNER/$IMAGE:latest-released-centos
  docker push $OWNER/$IMAGE:${TRAVIS_TAG}
  docker push $OWNER/$IMAGE:latest-released
  docker push $OWNER/$IMAGE:${TRAVIS_TAG}-centos
  docker push $OWNER/$IMAGE:latest-released-centos
else
  echo "Not doing the docker push, because the tag '${TRAVIS_TAG}' is not of form x.y.z.Final"
fi