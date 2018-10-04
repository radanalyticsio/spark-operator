IMAGE?=radanalyticsio/spark-operator

.PHONY: build
build: package image-build

.PHONY: build-travis
build-travis: install-lib build

.PHONY: install-parent
install-parent:
	rm -rf ./operator-parent-pom ; git clone --depth=1 --branch master https://github.com/jvm-operators/operator-parent-pom.git && cd operator-parent-pom && MAVEN_OPTS="-Djansi.passthrough=true -Dplexus.logger.type=ansi $(MAVEN_OPTS)" ./mvnw clean install && cd - && rm -rf ./operator-parent-pom

.PHONY: install-lib
install-lib: install-parent
	rm -rf ./abstract-operator ; git clone --depth=1 --branch master https://github.com/jvm-operators/abstract-operator.git && cd abstract-operator && MAVEN_OPTS="-Djansi.passthrough=true -Dplexus.logger.type=ansi $(MAVEN_OPTS)" ./mvnw clean install && cd - && rm -rf ./abstract-operator

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

.PHONY: image-build-alpine
image-build-alpine:
	docker build -t $(IMAGE):alpine -f Dockerfile.alpine .

.PHONY: image-build-all
image-build-all: image-build image-build-alpine

.PHONY: image-publish-alpine
image-publish-alpine: image-build-alpine
	docker tag $(IMAGE):latest-alpine $(IMAGE):alpine-`git rev-parse --short=8 HEAD`
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
	oc create -f manifest/operator.yaml
	until [ "true" = "`oc get pod -l app.kubernetes.io/name=spark-operator -o json 2> /dev/null | grep \"\\\"ready\\\": \" | sed -e 's;.*\(true\|false\),;\1;'`" ]; do printf "."; sleep 1; done
	oc logs -f `oc get pods --no-headers -l app.kubernetes.io/name=spark-operator | cut -f1 -d' '`

.PHONY: devel-kubernetes
devel-kubernetes:
	-minikube delete
	minikube start --vm-driver kvm2
	eval `minikube docker-env` && $(MAKE) build
	kubectl create -f manifest/operator.yaml
	until [ "true" = "`kubectl get pod -l app.kubernetes.io/name=spark-operator -o json 2> /dev/null | grep \"\\\"ready\\\": \" | sed -e 's;.*\(true\|false\),;\1;'`" ]; do printf "."; sleep 1; done
	kubectl logs -f `kubectl get pods --no-headers -l app.kubernetes.io/name=spark-operator | cut -f1 -d' '`
