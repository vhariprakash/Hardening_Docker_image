#!/bin/bash
ROOT_DIR=$1
ISO_NAME_WITH_VERSION=$2
#ISO_LOCATION_IN_ARTIFACTORY=$3
#DEPLOY_REPO_NAME=$4
#ISO_PACKAGE_DIR=$3
KINDLER_DIR="${ROOT_DIR}kindler/kindler/"
DEPLOY_DIR="${ROOT_DIR}devopsDeploy/"
#PACKAGE_METADATA_DIR="${DEPLOY_DIR}/Package-Metadata/"


if [ -f "${KINDLER_DIR}kindler.py" ]; then
   echo "kindler.py file exists in /home/wrsroot/kindler/kindler directory"
else
   echo "kindler.py file DOES'NT exists in /home/wrsroot/kindler/kindler directory"
   exit 1
fi

sudo -S python /home/wrsroot/kindler/site_utils/package_manager.py --remove ${ISO_NAME_WITH_VERSION} << 'EOF'
Minda00$
y
EOF

rc=$?

echo "package_manager.p return Code : $rc"

if [[ $rc == 0 ]];
then
    echo "Success in ISO cleanup execution"
else
    echo "Failure in ISO cleanup execution "
fi
