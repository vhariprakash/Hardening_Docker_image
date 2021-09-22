#!/bin/sh

if [ -z "$1" ]; then
	echo "Provide mount directory location as argument..!"
	exit 8
fi

echo "==================================Deploying Service============================================"
m_dir=$1
DOCKER_IMAGE_LOCATION=$m_dir/docker_images/
HELM_CHART_LOCATION=$m_dir/helm/

if [ ! -d $HELM_CHART_LOCATION ] || [ ! -d $DOCKER_IMAGE_LOCATION ]; then
	echo "helm or docker_images directory  doesn't exist...!!"
	exit 1
fi

echo "Loading Docker images"
docker version

if [ $? != 0 ]; then
        echo "Docker not found...!!"
        exit 1
fi
echo "*****************************************************************************************"
for docker_image_file in "$DOCKER_IMAGE_LOCATION"/*
do
  echo "Loading docker image : $docker_image_file"
  docker load -i $docker_image_file
  if [ $? != 0 ]; then
        echo "Docker load failed...!!"
        exit 2
  fi
done
echo "**************************Docker Images loaded Successfully******************************"
helm version
if [ $? != 0 ]; then
	echo "Failed to install helm chart, Helm client not installed or not running...!!"
        exit 3
fi
echo "Installing helm charts"

setValue=""
if [ -z "${SDCIMAGEPOOL}" ]; then
	echo "No SDCIMAGEPOOL Defined"
else
	echo "SDCIMAGEPOOL Defined, running in coreload environment"
	tempLocation="${SDCIMAGEPOOL}/reserved/T"
	setValue=" --set deployment.temp_location=$tempLocation"
fi

for helm_chart_file in "$HELM_CHART_LOCATION"/*
do
  echo "Installing helm chart : $helm_chart_file"
  helm install $helm_chart_file $setValue
  if [ $? != 0 ]; then
        echo "Failed to install helm chart...!!"
        exit 2
  fi
done
echo "Helm Chart installed Successfully"
echo "========================================================================================="

exit 0
