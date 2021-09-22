#!/bin/sh

propFileName=$1
if [ ! -f $propFileName ]; then
	echo "Property file not found...!"
	exit 1
fi

echo "==================================================================================="
while IFS='=' read -r key value
do
    eval ${key}=${value}
done < "$propFileName"

docker version

if [ $? != 0 ]; then
	echo "Docker not found...!!"
	exit 1
fi
echo "******************Docker Operations Pull, Rename and Save***************************"
echo "Docker image location : "${DOCKER_IMAGE_LOCATION}

#Clearing exisiting image start
echo "***************Clearing image" $DOCKER_IMAGE_NAME "****************"
docker images | grep $DOCKER_IMAGE_NAME | awk '{print $3}' | xargs docker rmi --force
if [ $? == 0 ]; then
 echo "Existing image cleared."
fi
echo "***************Clearing image" $DOCKER_IMAGE_NAME "*********DONE***"
#Clearing exisiting image end



docker pull ${DOCKER_IMAGE_LOCATION}

if [ $? != 0 ]; then
	echo "Docker Pull failed for image : "$DOCKER_IMAGE_LOCATION
	exit 1
fi

if [ -z $DOCKER_IMAGE_NAME ];then
        echo "Provide Image name as second arg..!"
        exit 2
fi

docker tag $DOCKER_IMAGE_LOCATION $DOCKER_IMAGE_NAME

if [ $? != 0 ];then
	echo "Docker rename failed...!"
	exit 3
fi

ISO_FOLDER_NAME="SERVICE-ISO"
if [ -d $ISO_FOLDER_NAME ]; then
	echo "Deleting ISO content directory.."
	rm -rf $ISO_FOLDER_NAME
fi
mkdir $ISO_FOLDER_NAME
ISO_HELM_CHART_LOCATION="${ISO_FOLDER_NAME}/helm"
ISO_DOCKER_IMAGES_LOCATION="${ISO_FOLDER_NAME}/docker_images"
ISO_GENERIC="${ISO_FOLDER_NAME}/data/generic/"
ISO_ACA="${ISO_FOLDER_NAME}/data/aca"
ISO_APP_MANIFEST="${ISO_FOLDER_NAME}/manifest"
mkdir $ISO_DOCKER_IMAGES_LOCATION 
mkdir $ISO_HELM_CHART_LOCATION
mkdir -p $ISO_ACA
mkdir -p $ISO_GENERIC
mkdir -p $ISO_APP_MANIFEST
SCRIPTS_SOURCE=./scripts

cp -r $SCRIPTS_SOURCE $ISO_GENERIC
echo "Scripts Copied from : ${SCRIPTS_SOURCE} to : "$ISO_GENERIC_SCRIPTS

cp $APP_MANIFEST_FILE $ISO_APP_MANIFEST
echo "Copied the Application MANIFEST File to : "$ISO_FOLDER_NAME

tarFileName=$(echo $DOCKER_TAR_NAME | tr /: -)
tarFileName="${tarFileName}.tgz"
echo "Saving docker image to : $DOCKER_IMAGES_LOCATION/${tarFileName}"
docker save -o "$ISO_DOCKER_IMAGES_LOCATION/${tarFileName}" $DOCKER_IMAGE_NAME

if [ $? != 0 ];then
	echo "Failed to save as tar : ${tarFileName}"
	exit 1
fi

echo "Succefully Saved Docker image as tar...!!"

echo "****************************Creating Helm Chart************************************"
HELM_TGZ_FILE_NAME="${HELM_TGZ_FILE_NAME}.tgz"

CHART_LOC="${HELM_CHART_LOCATION%/*}"
echo "HELM CHART LOCATION : $CHART_LOC"
CHART_NAME="${HELM_CHART_LOCATION##*/}"
echo "HELM CHART NAME : $CHART_NAME"
tar -C $CHART_LOC -cvzf ${ISO_HELM_CHART_LOCATION}/${HELM_TGZ_FILE_NAME} $CHART_NAME

if [ $? != 0 ]; then
	echo "Failed to create helm chart...!!!"
	exit 4
fi
echo "Successfully created helm chart : $ISO_HELM_CHART_LOCATION/$HELM_TGZ_FILE_NAME"

echo "****************************Creating ISO************************************"
ISO_NAME="${ISO_NAME}-${SERVICE_VERSION}.iso"
mkisofs -J -R -v -o $ISO_NAME $ISO_FOLDER_NAME
if [ $? != 0 ]; then
        echo "Failed to create ISO...!!!"
        exit 5
fi
echo "Successfully created ISO : ${ISO_NAME}"

exit 0
