#! /bin/bash

if [ -z "$1" ]; then
        echo "MISSING [ISO Location]."
	echo "Usage : start-dicom-service.sh  [ISO Location] [ISO File Name]"
     exit 1
fi
echo "Provided ISO location. :" $1

#if [ -z "$2" ]; then
# 	echo "MISSING [Service Name],  one of these: [stow,qido,wado,delete]"
#	echo "Usage : install-dicom.sh [ISO Location] [Service Name] [ISO File Name]"
#     exit 1
#fi
#echo "Provided service name as:" $2


if [ -z "$2" ]; then
        echo "MISSING [ISO File Name]."
	echo "Usage : install-dicom.sh [ISO Location] [ISO File Name]"
     exit 1
fi
echo "Provided ISO :" $2


ISO_HOME=$1
serviceName=iso
SERVICE=`echo $serviceName | tr [a-z] [A-Z]`
SERVICE_MOUNT_DIR=$ISO_HOME/$SERVICE-MNT

#COPY DIRECTORIES
SERVICE_CP_DIR=$ISO_HOME/$SERVICE-CP


#INSTLLATION Directories
SERVICE_HOME=./$SERVICE-INSTALL

#ISO NAMES
SERVICE_ISO_NAME=$2

#Make Directories
#0.0 Create directories for mounting
mkdir $SERVICE_MOUNT_DIR
echo "Mount Directories Created Successfully."
#0.1 Create directories for copying mounted files/folders.
mkdir $SERVICE_CP_DIR 

echo "Copy location directories Created Successfully."
#0.2 Create installation directories.
mkdir $SERVICE_HOME 
echo "Installation directories Created Successfully."


#1.Mounting all iso to created mount directories.
mount -o loop $ISO_HOME/$SERVICE_ISO_NAME $SERVICE_MOUNT_DIR
echo $SERVICE_ISO_NAME "mounted to" $SERVICE_MOUNT_DIR " Successfully."
ls $SERVICE_MOUNT_DIR

#2.Copy all mounted files/folders to cp directories.
cp -r $SERVICE_MOUNT_DIR/* $SERVICE_CP_DIR
echo "[" $SERVICE "] Copied mounted files/folders to " $SERVICE_CP_DIR

chown -R $SDCUSER:$SDCUSER $SERVICE_CP_DIR
echo "Changed owner of "$SERVICE_CP_DIR" to "$SDCUSER

#3.Update Permission to execute
sed -i -e 's/\r$//' $SERVICE_CP_DIR/data/generic/scripts/*.sh
chmod 777 $SERVICE_CP_DIR/data/generic/scripts/*.sh

echo "Permission applied for install_service & deploy_service sh files."

#4.Install
echo "Executing stow install_service.sh..."
$SERVICE_CP_DIR/data/generic/scripts/install_service.sh $SERVICE_HOME $SERVICE_MOUNT_DIR
echo "Executing " $SERVICE " install_service.sh..."

# Applied execute permission for the installed files.
sed -i -e 's/\r$//' $SERVICE_HOME/data/generic/scripts/*.sh
chmod 777 $SERVICE_HOME/data/generic/scripts/*.sh

#5.Deploy
echo "Executing " $SERVICE " deploy_service.sh..."
$SERVICE_HOME/data/generic/scripts/deploy_service.sh $SERVICE_HOME

#6.Unmount
umount $SERVICE_MOUNT_DIR && rm -r $SERVICE_MOUNT_DIR
rm -rf $SERVICE_CP_DIR $SERVICE_HOME
exit 0
