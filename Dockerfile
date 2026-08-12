FROM registry.access.redhat.com/ubi8/ubi:8.10-1785749648 as ubi8-base

ARG MMTC_VERSION

ENV JAVA_HOME=/
ENV MMTC_HOME=/opt/local/mmtc
ENV TK_CONFIG_PATH=/opt/local/mmtc/conf

RUN yum install -y unzip

# CLI container image
FROM ubi8-base as mmtc-cli

ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0

RUN yum install -y java-1.8.0-openjdk.x86_64

COPY build/distributions/mmtc-${MMTC_VERSION}.zip /tmp/
RUN unzip /tmp/mmtc-${MMTC_VERSION}.zip -d /opt/local/
RUN mv /opt/local/mmtc-${MMTC_VERSION} /opt/local/mmtc

# webapp container image
FROM ubi8-base as mmtc-webapp

ENV JAVA_HOME=/usr/lib/jvm/java-17

RUN yum install -y java-17-openjdk.x86_64

COPY build/distributions/mmtc-webapp-${MMTC_VERSION}.zip /tmp/
RUN unzip /tmp/mmtc-webapp-${MMTC_VERSION}.zip -d /opt/local/
RUN mv /opt/local/mmtc-webapp-${MMTC_VERSION} /opt/local/mmtc
