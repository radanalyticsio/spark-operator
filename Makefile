IMAGE?=radanalyticsio/spark-operator

.PHONY: build
build: package image-build

.PHONY: package
package:
	MAVEN_OPTS="-Djansi.passthrough=true -Dplexus.logger.type=ansi $(MAVEN_OPTS)" ./mvnw clean package -DskipTests

.PHONY: test
test:
	MAVEN_OPTS="-Djansi.passthrough=true -Dplexus.logger.type=ansi $(MAVEN_OPTS)" ./mvnw clean test

.PHONY: image-build
image-build:
	docker build -t $(IMAGE):centos -f Dockerfile.centos .
	docker tag $(IMAGE):centos $(IMAGE):latest

.PHONY: buildah
buildah:
	command -v buildah || docker cp $$(docker create docker.io/tomkukral/buildah:latest ls):/usr/bin/buildah ./buildah
	echo -e "\n\nbuildah version: " && ./buildah -v || ./buildah -v && echo -e "\n"
	buildah bud -f Dockerfile.centos . /dev/null || ./buildah bud -f Dockerfile.centos .
	#buildah bud -f Dockerfile.alpine . /dev/null || ./buildah bud -f Dockerfile.alpine . # this fails for the alpine img

.PHONY: buildah-travis-deps
buildah-travis-deps:
	#sudo apt-get -y install software-properties-common
	sudo add-apt-repository -y ppa:alexlarsson/flatpak
	#sudo add-apt-repository -y ppa:gophers/archive
	sudo apt-add-repository -y ppa:projectatomic/ppa
	sudo apt-get -y -qq update
	#sudo apt-get -y install bats btrfs-tools git libapparmor-dev libdevmapper-dev libglib2.0-dev libgpgme11-dev libostree-dev libseccomp-dev libselinux1-dev skopeo-containers go-md2man
	sudo apt-get -y install libostree-dev libostree-1-1
	#sudo apt-get -y install golang-1.10

.PHONY: image-build-alpine
image-build-alpine:
	docker build -t $(IMAGE):alpine -f Dockerfile.alpine .

.PHONY: image-build-all
image-build-all: image-build image-build-alpine

.PHONY: image-publish-alpine
image-publish-alpine: image-build-alpine
	docker tag $(IMAGE):alpine $(IMAGE):alpine-`git rev-parse --short=8 HEAD`
	docker tag $(IMAGE):alpine $(IMAGE):latest-alpine
	docker push $(IMAGE):latest-alpine

.PHONY: image-publish
image-publish: image-build
	docker tag $(IMAGE):centos $(IMAGE):`git rev-parse --short=8 HEAD`-centos
	docker tag $(IMAGE):centos $(IMAGE):latest-centos
	docker push $(IMAGE):latest

.PHONY: image-publish-all
image-publish-all: build-travis image-build-all image-publish image-publish-alpine

.PHONY: devel
devel: build
	-docker kill `docker ps -q` || true
	oc cluster up
	sed 's;quay.io/radanalyticsio/spark-operator:latest-released;radanalyticsio/spark-operator:latest;g' manifest/operator.yaml > manifest/operator-devel.yaml && oc create -f manifest/operator-devel.yaml ; rm manifest/operator-devel.yaml || true
	until [ "true" = "`oc get pod -l app.kubernetes.io/name=spark-operator -o json 2> /dev/null | grep \"\\\"ready\\\": \" | sed -e 's;.*\(true\|false\),;\1;'`" ]; do printf "."; sleep 1; done
	oc logs -f `oc get pods --no-headers -l app.kubernetes.io/name=spark-operator | cut -f1 -d' '`

.PHONY: devel-kubernetes
devel-kubernetes:
	-minikube delete
	minikube start --vm-driver kvm2
	eval `minikube docker-env` && $(MAKE) build
	sed 's;quay.io/radanalyticsio/spark-operator:latest-released;radanalyticsio/spark-operator:latest;g' manifest/operator.yaml > manifest/operator-devel.yaml && kubectl create -f manifest/operator.yaml ; rm manifest/operator-devel.yaml || true
	until [ "true" = "`kubectl get pod -l app.kubernetes.io/name=spark-operator -o json 2> /dev/null | grep \"\\\"ready\\\": \" | sed -e 's;.*\(true\|false\),;\1;'`" ]; do printf "."; sleep 1; done
	kubectl logs -f `kubectl get pods --no-headers -l app.kubernetes.io/name=spark-operator | cut -f1 -d' '`
