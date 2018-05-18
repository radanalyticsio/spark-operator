IMAGE?=jkremser/oshinko-operator

.PHONY: build
build:
	docker build -t $(IMAGE) -f Dockerfile .

.PHONY: build-slim
build-slim:
	docker build -t $(IMAGE):slim -f Dockerfile.slim .

.PHONY: build-all
build-all: build build-slim
