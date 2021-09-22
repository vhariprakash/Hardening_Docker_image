#!/bin/bash
ROOT_DIR=$1
ISO_NAME_WITH_VERSION=$2
ISO_PACKAGE_DIR=$3
KINDLER_DIR="${ROOT_DIR}kindler/kindler/"
DEPLOY_DIR="${ROOT_DIR}devopsDeploy/"


if [ -f "${KINDLER_DIR}kindler.py" ]; then
   echo "kindler.py file exists in ${KINDLER_DIR} directory"
else
   echo "kindler.py file DOES'NT exists in ${KINDLER_DIR} directory"
   exit 1
fi

cd ${KINDLER_DIR}
echo "Minda00$" | sudo -S python kindler.py -s ../source.yaml -o app-install --app_pkg_path ${ISO_PACKAGE_DIR}

rc=$?

echo "kindler.py return Code : $rc"

if [[ $rc == 0 ]];
then
    echo "kindler.py execution successful"
    exit 0
else
    echo "Failure in kindler.py execution"
    exit 1
fi

