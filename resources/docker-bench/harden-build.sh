#!/bin/bash

## build the image
/usr/local/bin/docker-compose  build --no-cache

## Run the container
/usr/local/bin/docker-compose up -d

## Limit PID Limit - Docker Benchmark
#docker container update --pids-limit=20 java-app
sleep 10
## Use Docker Content Trust - ## Docker Benchmark
export DOCKER_CONTENT_TRUST=1
## Run Benchmark
cd /root/docker-bench-security && sh docker-bench-security.sh

## Run Benchmark on  Image and container
echo "RUNNING THE BENCHMARK ONLY ON IMAGE AND CONTAINER RUNTIME"
sh docker-bench-security.sh -l /tmp/docker-bench-security.sh.log -c container_images,container_runtime -e check_4_2,check_4_3,check_4_4,check_4_11,check_5_1

## Remove the container
docker stop java-app && docker rm java-app

## Remove the built image
yes | docker image prune -a
