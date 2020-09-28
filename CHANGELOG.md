# Changelog

## 2020-09-2020

- Change log added
- Top-level *pom.xml* copied from https://github.com/jvm-operators/operator-parent-pom and turned into
  a local, unpublished artifact named spark-operator-parent with submodules
- Subdirectory *annotator* copied from https://github.com/jvm-operators/abstract-operator and turned into
  a local, unpublished submodule named spark-operator-annotator
- Subdirectory *abstract-operator* copied from https://github.com/jvm-operators/abstract-operator and turned into
  a local,  unpublished submodule named spark-abstract-operator
- Source code for the spark operator moved to *spark-operator* subdirectory and modified to reference
  spark-operator-parent, spark-operator-annotator, and spark-abstract-operator as dependencies instead
  of their precursors published in maven repositories.
- Dockerfiles and Makefile modified to build with the new setup.
- Version of the fabric8 kubernetes client changed to 4.10.3
