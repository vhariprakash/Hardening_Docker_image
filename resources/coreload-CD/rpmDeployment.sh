#!/bin/bash
set -x
service_name=$1
rpmInstallPath=$2
rpmFile=$3
echo "check if rpm exists"
rpmname=`rpm -qa | grep ${service_name} | head -1`
if [[ ${rpmname} != '' ]]
then
    echo "Uninstalling rpm"
    rpm -e ${rpmname}
fi
echo "Installing rpm"
echo "${rpmInstallPath}/${rpmFile}"
rpm -ivh ${rpmInstallPath}/${rpmFile}
