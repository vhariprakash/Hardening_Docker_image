#! /bin/bash
serviceName=edison-licensing-service

echo "Usage : ./uninstall-"$serviceName" [retryLimit]"
echo "Default retryLimit is [5]"


echo "**************************Helm deleting********************"
helm ls | grep edison-licensing-service | awk '{print $1}' | xargs helm delete
echo "*********************Helm "$serviceName" service deleted.******"



retryLimit=${1:-5}
echo "Retry limit is:"$retryLimit


n=1
   until [ $n -ge $retryLimit ]
   do
      docker images | grep edison-licensing | awk '{print $3}' | xargs docker rmi --force && break
      echo "Retrying..."
      n=$[$n+1]
      sleep 5
   done

echo "Uninstalled edison-licensing successfully."
echo "=================================================================="
exit 0
