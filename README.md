# spark-operator

[![Build status](https://travis-ci.org/radanalyticsio/spark-operator.svg?branch=master)](https://travis-ci.org/radanalyticsio/spark-operator)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

`{ConfigMap|CRD}`-based approach for managing the Spark clusters in Kubernetes and OpenShift.

This operator uses [abstract-operator](https://github.com/jvm-operators/abstract-operator) library.

<!--
asciinema rec -i 3
docker run -\-rm -v $PWD:/data asciinema/asciicast2gif -s 1.18 -w 104 -h 27 -t monokai 189204.cast demo.gif
-->
[![Watch the full asciicast](https://github.com/radanalyticsio/spark-operator/raw/master/ascii.gif)](https://asciinema.org/a/189204?&cols=104&rows=27&theme=monokai)

# How does it work
![UML diagram](https://github.com/radanalyticsio/spark-operator/raw/master/standardized-UML-diagram.png "UML Diagram")

# Quick Start

Run the `spark-operator` deployment:
```bash
kubectl apply -f manifest/operator.yaml
```

Create new cluster from the prepared example:

```bash
kubectl apply -f examples/cluster.yaml
```

After issuing the commands above, you should be able to see a new Spark cluster running in the current namespace.

```bash
kubectl get pods
NAME                               READY     STATUS    RESTARTS   AGE
my-spark-cluster-m-5kjtj           1/1       Running   0          10s
my-spark-cluster-w-m8knz           1/1       Running   0          10s
my-spark-cluster-w-vg9k2           1/1       Running   0          10s
spark-operator-510388731-852b2     1/1       Running   0          27s
```

Once you don't need the cluster anymore, you can delete it by deleting the config map resource by:
```bash
kubectl delete cm my-spark-cluster
```

# Very Quick Start

```bash
# create operator
kubectl apply -f http://bit.ly/sparkop

# create cluster
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-cluster
  labels:
    radanalytics.io/kind: sparkcluster
data:
  config: |-
    worker:
      instances: "2"
EOF
```

### OpenShift

For deployment on OpenShift use the same commands as above, but with `oc` instead of `kubectl`.

### Custom Resource Definitions (CRD)

This operator can also work with CRDs. Assuming the admin user is logged in, you can install the operator with:

```bash
kubectl apply -f manifest/operator-crd.yaml
```

and then create the Spark clusters by creating the custom resources (CR).

```bash
kubectl apply -f examples/cluster-cr.yaml
kubectl get sparkclusters
```

### Images

Image name         | Description | Layers | quay.io | docker.io
------------------ | ----------- | ------ | ------- | ----------
`:latest-released` | represents the latest released version | [![Layers info](https://images.microbadger.com/badges/image/radanalyticsio/spark-operator:latest-released.svg)](https://microbadger.com/images/radanalyticsio/spark-operator:latest-released) | [![quay.io repo](https://quay.io/repository/radanalyticsio/spark-operator/status "quay.io repo")](https://quay.io/repository/radanalyticsio/spark-operator?tab=tags) | [![docker.io repo](https://img.shields.io/docker/pulls/radanalyticsio/spark-operator.svg "docker.io repo")](https://hub.docker.com/r/radanalyticsio/spark-operator/tags/)
`:latest`          | represents the master branch | [![Layers info](https://images.microbadger.com/badges/image/radanalyticsio/spark-operator:latest.svg)](https://microbadger.com/images/radanalyticsio/spark-operator:latest) |  | 
`:x.y.z`           | one particular released version | [![Layers info](https://images.microbadger.com/badges/image/radanalyticsio/spark-operator:0.1.5.svg)](https://microbadger.com/images/radanalyticsio/spark-operator:0.1.5) |  | 

For each variant there is also available an image with `-alpine` suffix based on Alpine for instance [![Layers info](https://images.microbadger.com/badges/image/radanalyticsio/spark-operator:latest-released-alpine.svg)](https://microbadger.com/images/radanalyticsio/spark-operator:latest-released-alpine)
