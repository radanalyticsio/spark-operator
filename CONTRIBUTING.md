# Maven Project Structure

## Version 1.0 branch

In the 1.0 branch, the spark-operator depends on the [abstract-operator library](https://github.com/jvm-operators/abstract-operator)
and the [operator-parent-pom](https://github.com/jvm-operators/operator-parent-pom). The top-level (only) pom.xml file in the
1.0 branch is for the spark-operator itself.

Any work on a version of the spark-operator that maintains these dependencies should happen on the 1.0 branch.
New versions/tags should use a 1.0.x versioning scheme.
The 1.0 branch was created on 9/28/2020.

## Master branch

After the creation of the 1.0 branch, the master branch of spark-operator was changed to eliminate the
external dependencies on [abstract-operator](https://github.com/jvm-operators/abstract-operator) and [operator-parent-pom](https://github.com/jvm-operators/operator-parent-pom).

Copies of the abstract-operator source code and the operator-parent-pom were added to the spark-operator repository, and their artifact
ids were changed to *spark-abstractor-operator* and *spark-operator-parent*. The intention is to **not** publish these jars to
a maven repository, but only reference them locally in building the spark-operator image.

The top-level pom.xml file in the spark-operator repository is now the parent pom, and the following subdirectories
contain all necessary source code for the spark-operator:

* annotator
* abstract-operator
* spark-operator

This change simplifies the build and release process and makes it easier to make changes in the abstract-operator code for the
benefit of the spark-operator.

Going forward, changes to the spark-operator source code itself that are independent of changes to the abstract-operator can
be merged from the master branch into the 1.0 branch if applicable. Changes that are dependent on additional changes to the
abstract-operator however cannot be merged into the 1.0 branch.

## CHANGELOG.md

The changelog should be updated when changes are made to the repository. In particular, changes to the top-level pom file and/or
the abstract-operator should be noted to comply with the Apache license in the original repositories for operator-parent-pom
and abstract-operator.
