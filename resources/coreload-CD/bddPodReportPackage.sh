#!bin/bash

deployFileName=$1
#bddPodName=`kubectl get -f $deployFileName | grep -i "^pod/bdd-runner" | awk -F " " '{print $1}'`
bddPodNamespace=$2
bddGitRepoName=$3
componentTestDir=$4
tarFileName=$5
tarFileLocation="${bdGitRepoName}/${componentTestDir}/${tarFileName}"

#kubectl exec -it --namespace=$bddPodNamespace $bddPodName bash -c "git clone https://502782741:Pa55word@gitlab-gxp.cloud.health.ge.com/Imaging-platform/utility-services.git"

checkPodName=`kubectl get -f $deployFileName | grep -i "^pod/bdd-runner"`
checkPodNameRC=$?
checkPodName2=`kubectl get -f $deployFileName | grep -i "bdd-runner"`
checkPodNameRC2=$?
if [ $checkPodNameRC == 0 ];
   then
   echo "$checkPodName"
   bddPodName=`kubectl get -f $deployFileName -o wide | grep -i "^pod/bdd-runner" | awk '{print $1}' | cut -d "/" -f 2`
   echo "BDD pod name is $bddPodName"
elif [ $checkPodNameRC2 == 0 ];
   then
   echo "$checkPodName2"
   bddPodName=`kubectl get -f $deployFileName -o wide | grep -i "bdd-runner" | awk '{print $1}' | cut -d "/" -f 2`
   echo "BDD pod name is $bddPodName"
else
   echo "Not able to find BDD pod name"
   exit 1
fi


if [ -z $bddPodName ]
then
 echo "BDD pod name [bddPodName] cannot be NULL, please check the value"
 exit 1
elif [ -z $bddPodNamespace ]
then
 echo "BDD pod namespace [bddPodNamespace] cannot be NULL, please check the value"
 exit 1
elif [ -z $bddGitRepoName ]
then
 echo "Project repo name [bddGitRepoName] cannot be NULL, please check the value"
 exit 1
elif [ -z $componentTestDir ]
then
 echo "Component test directory [componentTestDir] cannot be NULL, please check the value"
 exit 1
fi

echo " kubectl exec -i --namespace=$bddPodNamespace $bddPodName -- bash -c [ -d ${bddGitRepoName}/${componentTestDir}/target ] "
kubectl exec -i --namespace=$bddPodNamespace $bddPodName -- bash -c "[ -d ${bddGitRepoName}/${componentTestDir}/target ]"

targetDirCheckRC=$?

if [ $targetDirCheckRC == 0 ];
  then
  echo "Build and Target directories exist"
else
  echo "ERROR: Build and Target directories doesn''t exist"
  exit 1
fi

echo " kubectl exec -i --namespace=$bddPodNamespace $bddPodName -- bash -c tar -cvf ${tarFileName} ${bddGitRepoName}/${componentTestDir}/target "

kubectl exec -i --namespace=$bddPodNamespace $bddPodName -- bash -c "tar -cvf ${tarFileName} ${bddGitRepoName}/${componentTestDir}/target"

tarPackageRC=$?

if [ $tarPackageRC == 0 ];
  then
  echo "Target directories exist"
else
  echo "ERROR: Target directories doesn't exist"
  exit 1
fi

cpCmd="kubectl cp $bddPodNamespace/$bddPodName:${tarFileName} ${tarFileName}"
cmd1=`echo $cpCmd | sed 's/\/\//\//g'`
echo "Executing Copy command:  ${cmd1}"
exeCmd1=`$cmd1`

cpTarFile=$?
if [ $cpTarFile == 0 ];
  then
  echo "Copied tar file successfully on TargetNode"
else
  echo "ERROR: Failed while Copying tar file on TargetNode"
  exit 1
fi

