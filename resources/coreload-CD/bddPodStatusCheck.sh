#!bin/bash

deployFileName=$1

n=1
echo "Checking BDD pod name existence"
while [ $n -le 5 ]
do
checkPodName=`kubectl get -f $deployFileName | grep -i "^pod/bdd-runner"`
checkPodNameRC=$?
checkPodName2=`kubectl get -f $deployFileName | grep -i "bdd-runner"`
checkPodNameRC2=$?
if [ $checkPodNameRC == 0 ];
   then
   echo "$checkPodName"
   statusCommand=`kubectl get -f $deployFileName | grep -i "^pod/bdd-runner"  | awk -F " " '{print $3}'`
elif [ $checkPodNameRC2 == 0 ];
   then
   echo "$checkPodName2"
   statusCommand=`kubectl get -f $deployFileName | grep -i "bdd-runner"  | awk -F " " '{print $3}'`
else
   echo "ERROR: Not able to find BDD pod name"
   exit 1
fi

echo "Checking BDD pod stability"
sleep 1m
if [ $statusCommand == "Running" ]
   then
   echo "BDD pod status $statusCommand"
   (( n++ ))
   sleep 5s
elif [ $statusCommand == "ContainerCreating" ] && [ $n -le 5 ]
   then
   echo "Current status is $statusCommand and iteration is $n, skipping check for this one"
   (( n++ ))
   sleep 2s
else
  echo "ERROR: BDD Pod NOT in running state"
  echo "BDD Pod status $statusCommand"
  exit 1
fi
done
