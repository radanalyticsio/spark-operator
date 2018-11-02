FROM fabric8/java-centos-openjdk8-jdk:1.5.1

ENV JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=2 -XshowSettings:vm"

LABEL BASE_IMAGE="fabric8/java-centos-openjdk8-jdk:1.5.1"

ADD target/spark-operator-*.jar /spark-operator.jar

CMD ["/usr/bin/java", "-jar", "/spark-operator.jar"]
