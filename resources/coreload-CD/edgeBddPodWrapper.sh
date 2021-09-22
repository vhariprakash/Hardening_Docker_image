#!/bin/bash
set -x
serviceName=$1 #isoname
targetNodeRootDir=$2
bddPodNamespace=$3
bddGitRepoName=$4
bddGitRepoGroupName=$5
bddGitRepoUsername=$6
bddGitRepoPassword=$7
bddGitBranch=$8
bddExecutionCommand=${9}
componentTestingBaseDirStructure=${10}
tarFileName=${11}
bddPublisherNodeUsername=${12}
bddPublisherNodePassword=${13}
publishDir=${14}
ApiToken=${15}
artifactoryLoc=${16}
targetNodeRootUsername=${17}
targetNodeRootPassword=${18}
targetNodeIp=${19}
targetNodePort=${20}
bddScriptsLocation="~/${serviceName}/devopsDsl/resources/coreload-CD/"
devSourceCodeLocation="~/${serviceName}/devSourceCode/"
edgeReportMountLocation="~/${serviceName}/devSourceCode/"

completeBuildCommand="bash ${bddScriptsLocation}/bddPodExecuteBDD.sh ${devSourceCodeLocation}/deploy.yml ${bddPodNamespace} ${bddGitRepoGroupName} ${bddGitRepoName} ${bddGitRepoUsername} ${bddGitRepoPassword} ${bddGitBranch} ${componentTestingBaseDirStructure} '${bddExecutionCommand}'"

#Connect to k8s master terminal and execute the series of commands
sudo -S python /${targetNodeRootDir}/kindler/kindler/utils/terminal_utils.py << EOF
${targetNodeRootPassword}
set -x
rm -rf ~/${serviceName}/
sshpass -p ${targetNodeRootPassword} scp -rCP ${targetNodePort} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${targetNodeRootUsername}@${targetNodeIp}:${targetNodeRootDir}/${serviceName}/ ~/ || echo "scp the ${serviceName} to kubectl machine failed " && exit 1
chmod 755 ${bddScriptsLocation}/ || echo "chmod 755 ${bddScriptsLocation}/ failed" && exit 1
cd ~/${serviceName}/ || echo "cd ~/${serviceName}/ failed" && exit 1
bash ${bddScriptsLocation}/bddPodDelete.sh ${devSourceCodeLocation}/deploy.yml || echo "bddPodDelete.sh failed" && exit 1
bash ${bddScriptsLocation}/bddPodDeploy.sh ${devSourceCodeLocation}/deploy.yml || echo "bddPodDeploy.sh failed" && exit 1
bash ${bddScriptsLocation}/bddPodStatusCheck.sh ${devSourceCodeLocation}/deploy.yml || echo "bddPodStatusCheck.sh failed" && exit 1
${completeBuildCommand} || echo "bdd execution failed" && exit 1
mkdir -p /${edgeReportMountLocation}/${serviceName}/ || exit 1
bash ${bddScriptsLocation}/bddPodReportPackage.sh ${devSourceCodeLocation}/deploy.yml ${bddPodNamespace} ${bddGitRepoName} ${componentTestingBaseDirStructure} /${reportMountLocation}/${tarFileName} || exit 1
curl --silent -H X-JFrog-Art-Api:${ApiToken} -X PUT ${artifactoryLoc}/${serviceName}/bdd-reports/${tarFileName} -T /${edgeReportMountLocation}/${tarFileName} || exit 1
sshpass -p ${bddPublisherNodePassword} scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null /${edgeReportMountLocation}/${tarFileName} ${bddPublisherNodeUsername}@${bdd_publisher_node}:${publishDir} || exit 1
EOF