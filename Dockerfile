FROM registry.access.redhat.com/ubi8/ubi:8.10-1785749648 as ubi8-base

ARG MMTC_VERSION

ENV JAVA_HOME=/
ENV MMTC_HOME=/opt/local/mmtc
ENV TK_CONFIG_PATH=/opt/local/mmtc/conf

# CLI container image
FROM ubi8-base as mmtc-cli
ARG MMTC_VERSION

ENV JAVA_HOME=/usr/lib/jvm/jre-1.8.0

# install Java, optionally using extra CA certs provided at build time
RUN --mount=type=secret,id=extra_ca_cert,target=/run/secrets/extra-ca-cert.crt \
    if [ -s /run/secrets/extra-ca-cert.crt ]; then \
      cp /run/secrets/extra-ca-cert.crt /etc/pki/ca-trust/source/anchors/extra-ca-cert.crt && \
      update-ca-trust; \
    fi && \
    yum install -y java-1.8.0-openjdk.x86_64 && \
    yum clean all && \
    rm -rf /var/cache/yum /var/cache/dnf && \
    rm -f /etc/pki/ca-trust/source/anchors/extra-ca-cert.crt && \
    update-ca-trust

COPY build/distributions/mmtc-${MMTC_VERSION}.tar.gz /tmp/
RUN mkdir -p /opt/local/
RUN tar -xzf /tmp/mmtc-${MMTC_VERSION}.tar.gz -C /opt/local/
RUN mv /opt/local/mmtc-${MMTC_VERSION} /opt/local/mmtc

WORKDIR /opt/local/mmtc
ENTRYPOINT ["/opt/local/mmtc/bin/mmtc"]

# webapp container image
FROM ubi8-base as mmtc-webapp
ARG MMTC_VERSION

ENV JAVA_HOME=/usr/lib/jvm/jre-17

# install Java, optionally using extra CA certs provided at build time
RUN --mount=type=secret,id=extra_ca_cert,target=/run/secrets/extra-ca-cert.crt \
    if [ -s /run/secrets/extra-ca-cert.crt ]; then \
      cp /run/secrets/extra-ca-cert.crt /etc/pki/ca-trust/source/anchors/extra-ca-cert.crt && \
      update-ca-trust; \
    fi && \
    yum install -y java-17-openjdk.x86_64 && \
    yum clean all && \
    rm -rf /var/cache/yum /var/cache/dnf && \
    rm -f /etc/pki/ca-trust/source/anchors/extra-ca-cert.crt && \
    update-ca-trust

COPY build/distributions/mmtc-webapp-${MMTC_VERSION}.tar.gz /tmp/
RUN mkdir -p /opt/local/
RUN tar -xzf /tmp/mmtc-webapp-${MMTC_VERSION}.tar.gz -C /opt/local/
RUN mv /opt/local/mmtc-webapp-${MMTC_VERSION} /opt/local/mmtc

WORKDIR /opt/local/mmtc
ENTRYPOINT ["/opt/local/mmtc/bin/mmtc-webapp"]
