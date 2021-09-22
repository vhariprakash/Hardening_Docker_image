#!/bin/bash
set -x
ISO_PACKAGE_DIR=$1

if [ -f "$EML_HOME/scripts/app_install.sh" ]; then
   echo "app_install.sh file exists in $EML_HOME/scripts/app_install.sh in the deployment node"
else
   echo "app_install.sh file DOES'NT exists in $EML_HOME/scripts/app_install.sh in the deployment node"
   exit 1
fi

#echo "su -c \"$EML_HOME/scripts/app_install.sh -i ${ISO_PACKAGE_DIR} -e True -s True -–no-deps \" - emadmin"
#su -c "$EML_HOME/scripts/app_install.sh -i ${ISO_PACKAGE_DIR} -e True -s True --no-deps" - emadmin
echo "su -c \"$EML_HOME/scripts/app_install.sh -i ${ISO_PACKAGE_DIR} -–no-deps \" - emadmin"
su -c "$EML_HOME/scripts/app_install.sh -i ${ISO_PACKAGE_DIR} --no-deps" - emadmin

rc=$?
echo "app_install.sh return Code : $rc"

if [ $rc == 0 ];
then
    echo "app_install.sh execution successful"
else
    echo "app_install.sh execution failed"
    exit 1
fi

su -c "eml service --all start" - emadmin
rc=$?
echo "eml start service return Code : $rc"
if [ $rc == 0 ];
then
    echo "eml service start successful"
    exit 0
else
    echo "eml service start failed"
    exit 1
fi
