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

.PHONY: image-build-all
image-build-all: image-build image-build-slim

.PHONY: image-publish-slim
image-publish-slim: image-build-slim
	docker tag $(IMAGE):slim $(IMAGE):slim-`git rev-parse --short=8 HEAD`
	docker tag $(IMAGE):slim $(IMAGE):latest
	docker push $(IMAGE):slim-`git rev-parse --short=8 HEAD`
	docker push $(IMAGE):latest

.PHONY: image-publish
image-publish: image-build
	docker tag $(IMAGE):centos $(IMAGE):centos-`git rev-parse --short=8 HEAD`
	docker tag $(IMAGE):centos $(IMAGE):centos-latest
	docker push $(IMAGE):centos-`git rev-parse --short=8 HEAD`
	docker push $(IMAGE):centos-latest

.PHONY: image-publish-all
image-publish-all: image-build-all image-publish image-publish-slim
