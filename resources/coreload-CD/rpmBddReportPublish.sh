#!/bin/bash
set -x
rpmBddReportArtifactoryLocation=$1
rpmBddReportDir=$2
rpmFileName=$3
PASSWORD=$4
pwd
if [ -f $rpmBddReportDir/${rpmFileName} ]
then
	unset https_proxy
	echo       "curl -H \"X-JFrog-Art-Api:$PASSWORD\" -X PUT ${rpmBddReportArtifactoryLocation} -T ${rpmBddReportDir}/${rpmFileName}"
	statusCode=`curl -H "X-JFrog-Art-Api:$PASSWORD" -X PUT ${rpmBddReportArtifactoryLocation} -T ${rpmBddReportDir}/${rpmFileName}`
else
	echo "RPM BDD Report : ${rpmFileName}  not found"
	exit 1
fi
