#!/bin/bash
## Define a Clean Up Job
clean_up_job () {
    echo "Cleanup the container"
    docker stop java-app && docker rm java-app
    echo "Cleanup the images"
    yes | docker image prune -a
    echo "Clean-up the logs"
    rm -rf docker-bench-security.sh.log
}
DESIRED_SCORE=$1
## Run the docker-bench security script
chmod +x ./docker-bench-security.sh
rm -rf docker-bench-security.sh.log
## Use Docker Content Trust - ## Docker Benchmark
export DOCKER_CONTENT_TRUST=1
/bin/bash docker-bench-security.sh -l docker-bench-security.sh.log -c container_images,container_runtime -e check_4_2,check_4_3,check_4_4,check_4_11,check_5_1 > /dev/null 2>&1
DOCKER_BENCHMARK_SCORE=$(cat  docker-bench-security.sh.log | grep Score: | awk '{print $3}')
DOCKER_BENCHMARK_CHECKS=$(cat  docker-bench-security.sh.log | grep Checks: | awk '{print $3}')
echo "==============================================================================="
echo "The Docker Benchmark score on $DOCKER_BENCHMARK_CHECKS checks is: $DOCKER_BENCHMARK_SCORE"
## FAIL THE BUILD IF SCORE IS LESS THAN 14
if [ $DOCKER_BENCHMARK_SCORE -lt $DESIRED_SCORE ]
then
   echo "The docker benchmark compliance failed. Please correct the Warnings. For reference hardened dockefile and docker-compose, visit this link"
   echo "https://devcloud.swcoe.ge.com/devspace/display/CZJTA/Container+Lifecycle+Management+-+dTDR"
   echo "==============================================================================="
## SHOW THE DOCKER BENCH WARNINGS GENERATED IF THE BUILD FAILS
   cat  docker-bench-security.sh.log | grep WARN
   clean_up_job
   exit 1
else
   echo "The docker benchmark compliance passed. Good Job!!"
   echo "==============================================================================="
   clean_up_job
fi





