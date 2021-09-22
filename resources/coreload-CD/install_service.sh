#!/bin/sh

if [ -z "$1" ]; then
        echo "Provide installation directory location as argument..!"
        exit 1
fi

echo "provided installation folder for service : $1"


if [ -z "$2" ]; then
        echo "Provide iso mounted directory location as argument..!"
        exit 2
fi

echo "provided iso mounted folder for service : $2"

mkdir -p $1

m_dir=$2
install_dir=$1

DOCKER_IMAGE_LOCATION=$m_dir/docker_images/
HELM_CHART_LOCATION=$m_dir/helm/
GENERIC_LOCATION=$m_dir/data/aca/


if [ ! -d $HELM_CHART_LOCATION ] || [ ! -d $DOCKER_IMAGE_LOCATION ] || [ ! -d $GENERIC_LOCATION ]; then
        echo "helm or docker_images or data/generic directory doesn't exist...!!"
        exit 3
fi

echo "starting the installation...."

cp -R $m_dir/* $install_dir

echo "service installation complete...."
exit 0
