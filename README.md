# oshinko-operator

[![Build status](https://travis-ci.org/Jiri-Kremser/oshinko-operator.svg?branch=master)](https://travis-ci.org/Jiri-Kremser/oshinko-operator)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

`DeploymentConfig`-based approach for managing the Spark clusters in K8s and OpenShift.

# Quick Start

Run the `oshinko-operator` deployment:
```bash
oc create -f manifest/openshift/
```

Create new cluster from the prepared example:

```bash
oc create -f examples/cluster.yaml
```

After issuing the commands above, you should be able to see a new Spark cluster running in the current namespace.

```bash
oc get pods
NAME                               READY     STATUS    RESTARTS   AGE
my-spark-cluster-m-1-5kjtj         1/1       Running   0          10s
my-spark-cluster-w-1-m8knz         1/1       Running   0          10s
my-spark-cluster-w-1-vg9k2         1/1       Running   0          10s
oshinko-operator-510388731-852b2   1/1       Running   0          27s
```

Once you don't need the cluster anymore, you can delete it by deleting the config map resource by:
```bash
oc delete cm my-spark-cluster
```

### Kubernetes

For deployment on Kubernetes use
```bash
kubectl create -f manifest/kubernetes/
kubectl create -f examples/cluster.yaml
```

# Very Quick Start

Kubernetes:
```bash
kubectl create -f https://git.io/vhtr9
cat <<EOF | kubectl create -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-cluster
  labels:
    radanalytics.io/kind: cluster
data:
  worker-nodes: "2"
EOF
```

You will see one error during creation of RBAC resources, this is harmless. It's there because OpenShift and Kubernetes don't use the
same syntax for defining `RoleBindings` and the [operator-all.yaml](./manifest/universal/operator-all.yaml) file contains both of them.

### Images
[![Layers info](https://images.microbadger.com/badges/image/jkremser/oshinko-operator.svg)](https://microbadger.com/images/jkremser/oshinko-operator)
`jkremser/oshinko-operator:latest`

[![Layers info](https://images.microbadger.com/badges/image/jkremser/oshinko-operator:centos-latest.svg)](https://microbadger.com/images/jkremser/oshinko-operator:centos-latest)
`jkremser/oshinko-operator:centos-latest`
