#!bin/bash

deployFileName=$1
#bddPodName=`kubectl get -f deploy.yml -o wide | grep -i "^pod/bdd-runner" | awk '{print $1}' | cut -d "/" -f 2`
bddPodNamespace=$2
bddGitRepoGroupName=$3
bddGitRepoName=$4
bddGitRepoUsername=$5
bddGitRepoPassword=$6
bddGitBranch=$7
componentTestDir=$8
bddExecutionCommand=$9

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

#inputList=( ["deployFileName"]="$deployFileName" ["bddPodNamespace"]="$bddPodNamespace" ["bddGitRepoGroupName"]="$bddGitRepoGroupName" ["bddGitRepoName"]="$bddGitRepoName" ["bddGitRepoUsername"]="$bddGitRepoUsername" ["bddGitRepoPassword"]="$bddGitRepoPassword" ["componentTestDir"]="$componentTestDir" ["bddExecutionCommand"]="$bddExecutionCommand" )

#if [ -z $bddPodName ] || [ -z $bddPodNamespace ] || [ -z $bddGitRepoGroupName ] || [ -z $bddGitRepoName ] || [ -z $bddGitRepoUsername ] || [ -z $bddGitRepoPassword ] || [ -z $bddExecutionCommand ] || [ -z $componentTestDir ];
#then
# echo "Please check values in below map shouldn't be NULL"
# echo  ${inputList}
# exit 1
#fi

kubectl exec -i --namespace=$bddPodNamespace $bddPodName -- bash -c "rm -rf ${bddGitRepoName}"
kubectl exec -i --namespace=$bddPodNamespace $bddPodName -- bash -c "git clone -b ${bddGitBranch} https://$bddGitRepoUsername:$bddGitRepoPassword@gitlab-gxp.cloud.health.ge.com/$bddGitRepoGroupName/${bddGitRepoName}.git"
rc=$?

if [ $rc == 0 ];
  then
  echo "Git repo $bddGitRepoName cloned successfully in the bdd runner pod"
else
  echo "Problem Cloning the $bddGitRepoName repo"
  exit 1
fi

kubectl exec -i --namespace=$bddPodNamespace $bddPodName -- bash -x -c "cd ${bddGitRepoName}/${componentTestDir}; ${bddExecutionCommand}"

bddExecutionRC=$?

if [ $bddExecutionRC == 0 ];
  then
  echo "Component test execution PASSED"
else
  echo "ERROR: Component test execution failed" #exit 1 is after report publish
fi
