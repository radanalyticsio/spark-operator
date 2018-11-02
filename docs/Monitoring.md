oc login -u system:admin
oc apply -f docs/prometheus-operator.yaml


kubectl apply -f https://raw.githubusercontent.com/coreos/prometheus-operator/release-0.17/bundle.yaml

```bash
kubectl apply -f http://bit.ly/sparkop
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: spark-cluster-with-metrics
  labels:
    radanalytics.io/kind: sparkcluster
data:
  config: |-
    metrics: true
---
apiVersion: v1
kind: Service
metadata:
  name: masters-metrics
spec:
  type: NodePort
  ports:
  - name: metrics
    port: 7777
    protocol: TCP
  selector:
    radanalytics.io/sparkcluster: spark-cluster-with-metrics
    radanalytics.io/podType: master
---
apiVersion: v1
kind: Service
metadata:
  name: workers-metrics
spec:
  type: NodePort
  ports:
  - name: metrics
    port: 7777
    protocol: TCP
  selector:
    radanalytics.io/sparkcluster: spark-cluster-with-metrics
    radanalytics.io/podType: worker
EOF
```


```bash
oc adm policy add-scc-to-user anyuid -z default
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: prometheus
---
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRole
metadata:
  name: prometheus
rules:
- apiGroups: [""]
  resources:
  - nodes
  - services
  - endpoints
  - pods
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources:
  - configmaps
  verbs: ["get"]
- nonResourceURLs: ["/metrics"]
  verbs: ["get"]
---
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRoleBinding
metadata:
  name: prometheus
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: prometheus
subjects:
- kind: ServiceAccount
  name: prometheus
  namespace: myproject
EOF
```


```bash
cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: workers-monitor
  labels:
    team: spark-cluster-with-metrics
spec:
  selector:
    matchLabels:
      radanalytics.io/sparkcluster: spark-cluster-with-metrics
      radanalytics.io/podType: worker
  endpoints:
  - port: metrics
EOF
```

```bash
cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: masters-monitor
  labels:
    team: spark-cluster-with-metrics
spec:
  selector:
    matchLabels:
      radanalytics.io/sparkcluster: spark-cluster-with-metrics
      radanalytics.io/podType: master
  endpoints:
  - port: metrics
EOF
```

```bash
cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: Prometheus
metadata:
  name: prometheus
spec:
  serviceMonitorSelector:
    matchLabels:
      team: spark-cluster-with-metrics
  resources:
    requests:
      memory: 400Mi
EOF
```

oc expose svc/prometheus-operated
oc policy add-role-to-user edit system:serviceaccount:myproject:default
