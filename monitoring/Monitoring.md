# OpenShift
Start the operator if it's not running
```bash
oc apply -f http://bit.ly/sparkop
```

Add prometheus operator and example spark cluster that will be monitored
```bash
oc login -u system:admin
oc policy add-role-to-user edit system:serviceaccount:myproject:default
oc adm policy add-scc-to-user anyuid -z default
oc apply -f monitoring/prometheus-operator.yaml
sleep 10
oc apply -f monitoring/example-cluster-with-monitoring.yaml
oc expose svc/prometheus-operated
oc get routes
```

To verify the monitoring, use for instance the `jvm_memory_bytes_used` as the expression for PromQL.