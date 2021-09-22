#!/bin/bash
set -x
ISO_PACKAGE_DIR=$1
ISO_GROUP_NAME=$2

if [ -f "$EML_HOME/scripts/app_uninstall.sh" ]; then
   echo "app_uninstall.sh file exists in $EML_HOME/scripts/app_uninstall.sh in the deployment node"
else
   echo "app_uninstall.sh file DOES'NT exists in $EML_HOME/scripts/app_uninstall.sh in the deployment node"
   exit 1
fi

echo 'su -c "eml service --all stop" - emadmin'
su -c "eml service --all stop" - emadmin
rc=$?
echo "all service stop return Code : $rc"

echo "su -c \"$EML_HOME/scripts/app_uninstall.sh  -a ${ISO_GROUP_NAME} --no-deps \" - emadmin"
su -c "$EML_HOME/scripts/app_uninstall.sh  -a ${ISO_GROUP_NAME} --no-deps " - emadmin
rc=$?
echo "app_uninstall.sh return Code : $rc"

if [[ $rc == 2 ]];
then
   echo "App-Group with name $ISO_GROUP_NAME is not installed..!"
elif [[ $rc == 0 ]];
then
    echo "app_uninstall.sh execution successful"
else
    echo "app_uninstall.sh execution failed"
    exit 1
fi
echo "testing...(2) $rc"
#echo "deleting folders ..."
#rm -Rf $EML_HOME/devopsDeploy  $EML_HOME/devopsDsl $EML_HOME/devSourceCode $ISO_PACKAGE_DIR
#echo "deleting folders done"
