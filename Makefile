IMAGE?=jkremser/oshinko-operator

.PHONY: build
build: package image-build-slim

.PHONY: package
package:
	./mvnw package -DskipTests

.PHONY: test
test:
	./mvnw test -B

.PHONY: image-build
image-build:
	docker build -t $(IMAGE):centos -f Dockerfile.centos .

.PHONY: image-build-slim
image-build-slim:
	docker build -t $(IMAGE):slim -f Dockerfile.slim .
	docker tag $(IMAGE):slim $(IMAGE):latest

.PHONY: image-build-all
image-build-all: image-build image-build-slim

.PHONY: image-publish-slim
image-publish-slim: image-build-slim
	docker tag $(IMAGE):slim $(IMAGE):slim-`git rev-parse --short=8 HEAD`
	#docker push $(IMAGE):slim-`git rev-parse --short=8 HEAD`
	docker push $(IMAGE):latest

.PHONY: image-publish
image-publish: image-build
	docker tag $(IMAGE):centos $(IMAGE):centos-`git rev-parse --short=8 HEAD`
	docker tag $(IMAGE):centos $(IMAGE):centos-latest
	#docker push $(IMAGE):centos-`git rev-parse --short=8 HEAD`
	docker push $(IMAGE):centos-latest

.PHONY: image-publish-all
image-publish-all: image-build-all image-publish image-publish-slim

.PHONY: devel
devel: build
	-docker kill `docker ps -q` || true
	oc cluster up
	oc create -f manifest/openshift/
	until [ "true" = "`oc get pod -l app.kubernetes.io/name=oshinko-operator -o json 2> /dev/null | grep \"\\\"ready\\\": \" | sed -e 's;.*\(true\|false\),;\1;'`" ]; do printf "."; sleep 1; done
	oc logs -f `oc get pods --no-headers -l app.kubernetes.io/name=oshinko-operator | cut -f1 -d' '`
