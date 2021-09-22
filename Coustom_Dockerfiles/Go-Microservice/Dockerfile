FROM hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-eis-all/build-tools-eis-repo:1.0.0
RUN export http_proxy=http://proxy-privzen.jfwtc.ge.com:80 && \
    export https_proxy=http://proxy-privzen.jfwtc.ge.com:80 && \
	export no_proxy=127.0.0.1,localhost,.ge.com &&\
	yum update -y && yum install go -y && \
	go get github.com/deepmap/oapi-codegen/cmd/oapi-codegen
RUN cp /root/go/bin/oapi-codegen /usr/sbin

RUN yum install -y yum-utils && \
	yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

RUN yum install docker-ce docker-ce-cli containerd.io -y 
