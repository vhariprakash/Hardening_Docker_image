package org.utils;
// Utility for logs display related things

/**
 * Function Name : dockerDeployment
 * Description   :
 * Arguments     :
 * Returns       :
 */

def dockerDeployment(dockerDeploymentDict){
    println "dockerDeployment[start]"
    println dockerDeploymentDict
    def branch_type = "${dockerDeploymentDict['branch_type']}"
    def docker_repo = dockerDeploymentDict[branch_type]['docker_repo']
    sh """
        cd ~
        echo "Check folder exist"
        if [ ! -d ${env.WORKSPACE}/dockerDeployment ]; then
            echo "Folder doesn't exist, create dir"
            mkdir -p ${env.WORKSPACE}/dockerDeployment
            cd ${env.WORKSPACE}/dockerDeployment
            mkdir dumps log_files
        fi
        docker run -d --network=host -v ${env.WORKSPACE}/${dockerDeploymentDict['dockerLogs']}:/${dockerDeploymentDict['dockerLogs']}/logs ${dockerDeploymentDict['artifactory_url']}/${docker_repo}/${dockerDeploymentDict['project']}/${dockerDeploymentDict['dockerName']}:${dockerDeploymentDict['version']}
    """
    println "dockerDeployment[end]"
}


/**
 * Function Name : helmDeployment
 * Description   :
 * Arguments     :
 * Returns       :
 */
def helmDeployment(helmDeploymentDict){
    println "helmDeployment[start]"
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def generalStageUtils = new org.utils.generalStageUtils()
    def helmDir = 'helmDeployment'
    def dslDir = 'devopsDsl'
    def devDir = 'devSourceCode'
    def chart_location = "${helmDeploymentDict['helmTargetNodeRootDir']}/${helmDeploymentDict['project']}/${helmDir}/${devDir}/${helmDeploymentDict['source_deployment_folder']}/"
    def additional_values_file_loc = chart_location + 'additional_values.yaml'
    def values_file_loc = chart_location + 'values.yaml'
    def arguments_file_location = "${env.WORKSPACE}/${helmDir}/${devDir}/${helmDeploymentDict['source_deployment_folder']}/" + "arguments.yaml"
    def helm_command
    //**STEP-0 Prep for the deployment*/
    generalDisplayUtils.sectionDisplay("STEP-0[helmDeployment]: Prep for the deployment", 'h3')
    def sshPassCommandArray = ["rm -rf ${helmDeploymentDict['helmTargetNodeRootDir']}/${helmDeploymentDict['project']}","mkdir -p ${helmDeploymentDict['helmTargetNodeRootDir']}/${helmDeploymentDict['project']}"]
    generalGroovyUtils.sshPassCommand(helmDeploymentDict['helmTargetNodeCredId'],helmDeploymentDict['helmTargetNodeIp'],sshPassCommandArray,helmDeploymentDict['helmTargetNodeSshPort'])

    //**STEP-1 Checkout Dev repo*/
    generalDisplayUtils.sectionDisplay("STEP-1[helmDeployment]: Checkout Dev repo", 'h3')
    checkoutDeploymentEnablers("${helmDir}/${devDir}" , helmDeploymentDict['dev_repo'], helmDeploymentDict['dev_branch'], helmDeploymentDict['gitlab_cred_id'])

    //**STEP-2 Checkout DSL repo*/
    generalDisplayUtils.sectionDisplay("STEP-2[helmDeployment]: Checkout DSL repo", 'h3')
    checkoutDeploymentEnablers("${helmDir}/${dslDir}", helmDeploymentDict['dsl_repo'], helmDeploymentDict['dsl_branch'], helmDeploymentDict['gitlab_cred_id'])

    //**STEP-3 Get values from deployment manifest for helm*/
    generalDisplayUtils.sectionDisplay("STEP-3[helmDeployment]: Get values from deployment manifest for helm", 'h3')
    def parseManifestFile = parseManifestFile(helmDeploymentDict['manifest_folder'],arguments_file_location)

    //**STEP-4 Copy HELM repo to target node*/
    generalDisplayUtils.sectionDisplay("STEP-4[helmDeployment]: Copy Helm repo to target node", 'h3')
    def sshPassScpMap = ["${env.WORKSPACE}/${helmDir}/" : "${helmDeploymentDict['helmTargetNodeRootDir']}/${helmDeploymentDict['project']}/"]
    generalGroovyUtils.sshPassScp(helmDeploymentDict['helmTargetNodeCredId'],helmDeploymentDict['helmTargetNodeIp'],sshPassScpMap,helmDeploymentDict['helmTargetNodeSshPort'])

    //**STEP-5 Fetch helm version for command variations*/
    generalDisplayUtils.sectionDisplay('STEP-5[helmDeployment]: Fetch helm version for command variations', 'h3')
    sshPassCommandArray = ["helm version"]
    def helmVersion = (generalGroovyUtils.sshPassCommand(helmDeploymentDict['helmTargetNodeCredId'],helmDeploymentDict['helmTargetNodeIp'],sshPassCommandArray,helmDeploymentDict['helmTargetNodeSshPort'])).trim()
    //def helmVersion = sh(returnStdout: true, script: 'helm version || exit 0').trim()
    println "helmVersion: ${helmVersion}"
    if (helmVersion.contains("v3")) {
        println("Helm version: ${parseManifestFile['helmVersion']}")
        helmListCommand = "list --namespace ${parseManifestFile['namespaceValue']}"
        helmDeleteCommand = "uninstall ${parseManifestFile['releasename']} --namespace ${parseManifestFile['namespaceValue']}"
        helmChartName = "${parseManifestFile['releasename']}"
    } else if(helmVersion.contains("v2")) {
        println("Helm version: ${['helmVersion']}")
        helmListCommand = "list"
        helmChartName = "--name ${parseManifestFile['releasename']}"
        helmDeleteCommand = "delete --purge ${parseManifestFile['releasename']}"
    }
    else{
        sh """
        echo "helm version is neither v2 nor v3"
        echo "Unrecognized Helm version: ${parseManifestFile['helmVersion']}"
        exit 1
        """
    }
    //**STEP-6 ssh and fetch helm list within namespace*/
    generalDisplayUtils.sectionDisplay("STEP-6[helmDeployment]: ssh and fetch helm list within namespace", 'h3')
    sshPassCommandArray = ["helm ${helmListCommand}"]
    def helmList = generalGroovyUtils.sshPassCommand(helmDeploymentDict['helmTargetNodeCredId'],helmDeploymentDict['helmTargetNodeIp'],sshPassCommandArray,helmDeploymentDict['helmTargetNodeSshPort'])

    if (helmList.contains("${parseManifestFile['releasename']}")){
        //**STEP-7 cleanup if this service is already deployed*/
        generalDisplayUtils.sectionDisplay("STEP-7[helmDeployment]: Cleanup if this service is already deployed", 'h3')
        sshPassCommandArray = ["helm ${helmDeleteCommand}"]
        generalGroovyUtils.sshPassCommand(helmDeploymentDict['helmTargetNodeCredId'],helmDeploymentDict['helmTargetNodeIp'],sshPassCommandArray,helmDeploymentDict['helmTargetNodeSshPort'])
    }
    //**STEP-8 Deploy the chart with the given releasename*/
    generalDisplayUtils.sectionDisplay("STEP-8[helmDeployment]: Deploy the chart with the given releasename", 'h3')
    /*sshPassCommandArray = ["helm install -f ${values_file_loc} -f ${additional_values_file_loc} -f ${chart_location}/arguments.yaml --set infra_platform_type=openstack ${parseManifestFile['direct_arguments']} ${helmChartName} --namespace ${parseManifestFile['namespaceValue']} ${chart_location}"] */
    sshPassCommandArray = ["helm install ${helmChartName} ${chart_location} -n ${parseManifestFile['namespaceValue']}"]
    generalGroovyUtils.sshPassCommand(helmDeploymentDict['helmTargetNodeCredId'],helmDeploymentDict['helmTargetNodeIp'],sshPassCommandArray,helmDeploymentDict['helmTargetNodeSshPort'])

    //**STEP-9 ssh and fetch helm list within namespace*/
    generalDisplayUtils.sectionDisplay("STEP-9[helmDeployment]: ssh and fetch helm list within namespace", 'h3')
    sshPassCommandArray = ["helm ${helmListCommand}"]
    def helmInstallVerify = generalGroovyUtils.sshPassCommand(helmDeploymentDict['helmTargetNodeCredId'],helmDeploymentDict['helmTargetNodeIp'],sshPassCommandArray,helmDeploymentDict['helmTargetNodeSshPort'])
    if (helmInstallVerify.contains("${helmChartName}")){
        println("Helm installation Successfull")
    }
    else{
        throw new Exception("Helm installation Failed")
    }
}

def parseManifestFile(manifest_folder_location,arguments_file_location){
    def list_of_manifest_files = sh returnStdout: true, script: """ls ${manifest_folder_location}/*-manifest.yaml"""
    println list_of_manifest_files
    //def releasename;def namespaceValue;def arguments_for_release
    def direct_arguments = ''
    def helm_direct_arguments = []
    //def arguments_for_release = ''
    def parseManifestFile = [:]
    list_of_manifest_files.tokenize("\n").each {
        def manifest_file_data = readYaml file: it
        def parsingValues = ['name', 'namespace', 'helm_override_values', 'helm_arguments']
        parsingValues.each{ manifestKeys ->
            if (manifest_file_data['app']["${manifestKeys}"] || manifest_file_data['app']["${manifestKeys}"] == null) {
                parseManifestFile['releasename'] = manifest_file_data['app']['name']
                parseManifestFile['namespaceValue'] = manifest_file_data['app']['namespace']
                helm_direct_arguments = manifest_file_data['app']['helm_arguments']
                if(helm_direct_arguments instanceof List) {
                    helm_direct_arguments.each{
                        direct_arguments = direct_arguments.concat("${it} ")
                    }
                } else {
                    direct_arguments = helm_direct_arguments
                }
                parseManifestFile['direct_arguments'] = direct_arguments
                parseManifestFile['arguments_for_release'] = manifest_file_data['app']['helm_override_values']
                if(parseManifestFile['arguments_for_release']){
                    sh "rm -rf ${arguments_file_location}"
                    writeYaml file: "${arguments_file_location}", data: parseManifestFile['arguments_for_release']
                    sh "cat ${arguments_file_location}"
                }
            } else {
                def code_checkout_url = sh(returnStdout: true, script: 'git config --local remote.origin.url').trim()
                parseManifestFile['releasename'] = code_checkout_url.tokenize("/")[-1].replace('.git', '')
                parseManifestFile['namespaceValue'] = 'edison-core'
            }
        }
    }
    return parseManifestFile
}

/**
 * Function Name : rpmDeployment
 * Description   :
 * Arguments     :
 * Returns       :
 */
def rpmDeployment(rpmDeploymentDict){
    println "rpmDeployment[start]"
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def generalStageUtils = new org.utils.generalStageUtils()
    def rpmDir = 'rpmDeployment'
    def dslDir = 'devopsDsl'
    def devDir = 'devSourceCode'
    def rpmArtifactPath
    def branch_type = rpmDeploymentDict['branch_type']
    def repo_key
    if(rpmDeploymentDict['rpmArtifactPath']){
        println "in if rpmDeploymentDict['rpmArtifactPath']"
        rpmArtifactPath = rpmDeploymentDict['rpmArtifactPath']
    }
    else{
        error("unable to find RPM file")
    }
    println "rpmDeploymentDict['rpmTargetNodeCredId'] " + rpmDeploymentDict['rpmTargetNodeCredId']
    def artifactoryCredId = generalStageUtils.getArtifactoryCredentialAPIKey(rpmDeploymentDict['artifactory_url'], rpmDeploymentDict,'username_passwd')
    rpmFileName = rpmArtifactPath.split("/")[-1]
    def service_name = rpmFileName.split('-')[0].trim()
    println("service_name:: " + service_name)

    //**STEP-2 Download rpm from artifactory */
    generalDisplayUtils.sectionDisplay("STEP-1[rpmDeployment]: Download rpm from artifactory", 'h3')
    dir(rpmDir){
        withCredentials([usernamePassword(credentialsId: artifactoryCredId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            sh """
                unset https_proxy
                curl -H "X-JFrog-Art-Api:${PASSWORD}" -X GET ${rpmArtifactPath} --output ${rpmFileName}
                pwd
                mkdir -p ${dslDir}; mkdir -p ${devDir}
            """
        }
    }
     //**STEP-3 Checkout Dev repo*/
    generalDisplayUtils.sectionDisplay("STEP-3[rpmDeployment]: Checkout Dev repo", 'h3')
    checkoutDeploymentEnablers("${rpmDir}/${devDir}" , rpmDeploymentDict['dev_repo'], rpmDeploymentDict['dev_branch'], rpmDeploymentDict['gitlab_cred_id'])

    //**STEP-4 Checkout Dsl repo*/
    generalDisplayUtils.sectionDisplay("STEP-4[rpmDeployment]: Checkout DSL repo", 'h3')
    checkoutDeploymentEnablers("${rpmDir}/${dslDir}" , rpmDeploymentDict['dsl_repo'], rpmDeploymentDict['dsl_branch'], rpmDeploymentDict['gitlab_cred_id'])

    //**STEP-5 create directory in target node*/
    generalDisplayUtils.sectionDisplay("STEP-5[rpmDeployment]: Cleanup previous dir and create directory in target node", 'h3')
    def sshPassCommandArray = ["rm -rf ${rpmDeploymentDict['rpmInstallPath']}/${rpmDeploymentDict['project']}","mkdir -p ${rpmDeploymentDict['rpmInstallPath']}/${rpmDeploymentDict['project']}"]
    ret = generalGroovyUtils.sshPassCommand(rpmDeploymentDict['rpmTargetNodeCredId'],rpmDeploymentDict['rpmTargetIp'],sshPassCommandArray,rpmDeploymentDict['rpmTargetPort'])

    //**STEP-6 Copy RPM to target node*/
    generalDisplayUtils.sectionDisplay("STEP-6[rpmDeployment]: Copy RPM to target node", 'h3')
    def sshPassScpMap = ["${env.WORKSPACE}/${rpmDir}/" : "${rpmDeploymentDict['rpmInstallPath']}/${rpmDeploymentDict['project']}/"]
    ret = generalGroovyUtils.sshPassScp(rpmDeploymentDict['rpmTargetNodeCredId'],rpmDeploymentDict['rpmTargetIp'],sshPassScpMap,rpmDeploymentDict['rpmTargetPort'])
    
    //**STEP-7 Check and Uninstall existing RPM from target node*/
    generalDisplayUtils.sectionDisplay("STEP-7[rpmDeployment]: Check and Uninstall existing RPM from target node", 'h3')
    sshPassCommandArray = ["rpm -qa ${service_name}"]
    def installed_rpm = generalGroovyUtils.sshPassCommand(rpmDeploymentDict['rpmTargetNodeCredId'],rpmDeploymentDict['rpmTargetIp'],sshPassCommandArray,rpmDeploymentDict['rpmTargetPort'])
    println("installed rpm = ${installed_rpm}")
    if(installed_rpm != ""){
        println "rpm exist's, So Uninstalling the old rpm: ${installed_rpm}"
        sshPassCommandArray = ["rpm -e ${installed_rpm}"]
        generalGroovyUtils.sshPassCommand(rpmDeploymentDict['rpmTargetNodeCredId'],rpmDeploymentDict['rpmTargetIp'],sshPassCommandArray,rpmDeploymentDict['rpmTargetPort'])
        println "Successfully Uninstalled old rpm: ${installed_rpm}"
    }
    else{
        println "No rpm is installed, Hence proceeding with new rpm installation"
    }

    //**STEP-8 Install RPM to target node*/
    generalDisplayUtils.sectionDisplay("STEP-8[rpmDeployment]: Install RPM to target node", 'h3')
    install_cmd = "rpm -ivh --force ${rpmDeploymentDict['rpmInstallPath']}/${rpmDeploymentDict['project']}/${rpmDir}/${rpmFileName}"
    println("Install_cmd : "+install_cmd)
    sshPassCommandArray = ["export JAVA_HOME=${rpmDeploymentDict['cdSystemJavaHome']}", install_cmd ]
    generalGroovyUtils.sshPassCommand(rpmDeploymentDict['rpmTargetNodeCredId'],rpmDeploymentDict['rpmTargetIp'],sshPassCommandArray,rpmDeploymentDict['rpmTargetPort'])
    println "rpmDeployment[end]"
}


def isoDeployPrep(isoDeployPrepDict){
    //**Constant params*/
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def generalStageUtils = new org.utils.generalStageUtils()
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    def dslDir = 'devopsDsl'
    def devDir = 'devSourceCode'
    if(isoDeployPrepDict['xBoxType'] == 'iso-ehl'){
        println "EDGE Deployment prep shell script execution [isoDeployPrep]"
        isoDeployPrepDict.put('targetNodeCredId',isoDeployPrepDict['edgeTargetNodeCredId'])
        isoDeployPrepDict.put('targetNodeIp',isoDeployPrepDict['edgeTargetNodeIp'])
        isoDeployPrepDict.put('targetNodeSshPort',isoDeployPrepDict['edgeTargetNodeSshPort'])
        isoDeployPrepDict.put('targetNodeRootDir',isoDeployPrepDict['edgeTargetNodeRootDir'])
        isoDeployPrepDict.put('xBoxType', 'iso-ehl')
    }
    else if (isoDeployPrepDict['xBoxType'] == 'iso-eml'){
        println "EML Deployment prep shell script execution [isoDeployPrep]"
        println isoDeployPrepDict['emlTargetNodeCredId']
        println isoDeployPrepDict['emlTargetNodeIp']
        println isoDeployPrepDict['emlTargetNodeSshPort']
        println isoDeployPrepDict['emlTargetNodeRootDir']
        isoDeployPrepDict.put('targetNodeCredId',isoDeployPrepDict['emlTargetNodeCredId'])
        isoDeployPrepDict.put('targetNodeIp',isoDeployPrepDict['emlTargetNodeIp'])
        isoDeployPrepDict.put('targetNodeSshPort',isoDeployPrepDict['emlTargetNodeSshPort'])
        isoDeployPrepDict.put('targetNodeRootDir',isoDeployPrepDict['emlTargetNodeRootDir'])
        isoDeployPrepDict.put('xBoxType', 'iso-eml')
    }

    else{
        throw new Exception("Supported iso deployType is either iso-eml or iso-ehl")
    }
    //** STEP-1 */
    generalDisplayUtils.sectionDisplay("STEP-1[isoDeployPrep]: Workspace Cleanup", 'h3')
    step([$class: 'WsCleanup'])
    //** STEP-2 */
    generalDisplayUtils.sectionDisplay("STEP-2[isoDeployPrep]: Checkout DSL repo", 'h3')
    checkoutDeploymentEnablers(dslDir , isoDeployPrepDict['dsl_repo'] , isoDeployPrepDict['dsl_branch'],isoDeployPrepDict['gitlab_cred_id'])
    //** STEP-3 */
    generalDisplayUtils.sectionDisplay("STEP-3[isoDeployPrep]: Checkout Dev repo", 'h3')
    checkoutDeploymentEnablers(devDir , isoDeployPrepDict['dev_repo'], isoDeployPrepDict['dev_branch'], isoDeployPrepDict['gitlab_cred_id'])
    //** STEP-4 */
    generalDisplayUtils.sectionDisplay("STEP-4[isoDeployPrep]: Download ISO", 'h3')
    dir('isoPackageDir'){
        withCredentials([usernamePassword(credentialsId: isoDeployPrepDict['credID_passwd'], usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            sh """
                echo "################  DOWNLOADING ISO... ################"
                wget ${isoDeployPrepDict['iso_url_form']}.iso --no-check-certificate
                echo "################  DOWNLOADING Package metadata json... ################"
                wget ${isoDeployPrepDict['iso_url_form']}_packagemetadata.json --no-check-certificate
                rm -rf .git
            """
        }
    }
    //** STEP-5 */
    generalDisplayUtils.sectionDisplay("STEP-5[isoDeployPrep]: mkdir in deployment box", 'h3')
    sshPassCommandArray = ["mkdir -p ${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/devopsDsl","mkdir -p ${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/devSourceCode","mkdir -p ${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/devopsDeploy", "mkdir -p ${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/isoPackageDir", "ls -l /${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/"]
    generalGroovyUtils.sshPassCommand(isoDeployPrepDict['targetNodeCredId'],isoDeployPrepDict['targetNodeIp'],sshPassCommandArray,isoDeployPrepDict['targetNodeSshPort'])
    //** STEP-6 */
    generalDisplayUtils.sectionDisplay("STEP-6[isoDeployPrep]: copy devopsDsl and devSourceCode to deployment box", 'h3')
    sshPassScpMap = ['devopsDsl': "${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/", 'devSourceCode': "${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/", 'isoPackageDir': "${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/"]
    generalGroovyUtils.sshPassScp(isoDeployPrepDict['targetNodeCredId'],isoDeployPrepDict['targetNodeIp'],sshPassScpMap,isoDeployPrepDict['targetNodeSshPort'])
    sshPassCommandArray = ["ls ${isoDeployPrepDict['targetNodeRootDir']}/${isoDeployPrepDict['isoNames']}/"]
    generalGroovyUtils.sshPassCommand(isoDeployPrepDict['targetNodeCredId'],isoDeployPrepDict['targetNodeIp'],sshPassCommandArray,isoDeployPrepDict['targetNodeSshPort'])
    //** STEP-7 */
    def deployExecuteDictAdditional = ['isoDeploymentShellDir': isoDeployPrepDict['deploymentShellDir'], 'deploymentShellScript': isoDeployPrepDict['deploymentShellScript'], 'deploymentShellArgs': isoDeployPrepDict['deploymentShellArgs']]
    def deployExecuteDict = isoDeployPrepDict + deployExecuteDictAdditional
    deployExecute(deployExecuteDict)
}

/**
 * Function Name : ehlIsoDeployment
 * Description   :
 * Arguments     :
 * Returns       :
 */
def isoEhlDeployment(isoEhlDeploymentDict){
    println "isoEhlDeploymentDict"
    println isoEhlDeploymentDict
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def generalStageUtils = new org.utils.generalStageUtils()
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    isoEhlDeploymentDict.put('xBoxType', 'iso-ehl')
    //** STEP-1 */
    generalDisplayUtils.sectionDisplay("STEP-1[isoEhlDeployment]: Remove previously created ISO directory", 'h3')
    withCredentials([usernamePassword(credentialsId: isoEhlDeploymentDict['edgeTargetNodeCredId'], usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        sshPassCommandArray = ["echo $PASSWORD | sudo -S rm -rf ${isoEhlDeploymentDict['edgeTargetNodeRootDir']}/${isoEhlDeploymentDict['isoNames']}"]
        generalGroovyUtils.sshPassCommand(isoEhlDeploymentDict['edgeTargetNodeCredId'],isoEhlDeploymentDict['edgeTargetNodeIp'],sshPassCommandArray,isoEhlDeploymentDict['edgeTargetNodeSshPort'])
    }
    //** STEP-2 */
    generalDisplayUtils.sectionDisplay("STEP-2[isoEhlDeployment]: Prepare Target node for repo related details ... ", 'h3')
    generalDisplayUtils.sectionDisplay("... like removing previously deployed ISO, download latest ISO & cloning repos", 'h3')
    def deploymentShellScript = "isoDeploymentCleanup.sh"
    def deploymentShellArgs = "${isoEhlDeploymentDict['edgeTargetNodeRootDir']}/ ${isoEhlDeploymentDict['isoNames']} ${isoEhlDeploymentDict['edgeTargetNodeRootDir']}/${isoEhlDeploymentDict['isoNames']}/"
    isoEhlDeploymentDict.put('deploymentShellScript', deploymentShellScript); isoEhlDeploymentDict.put('deploymentShellArgs', deploymentShellArgs )
    isoDeployPrep(isoEhlDeploymentDict)
    //**STEP-3 */
    generalDisplayUtils.sectionDisplay("STEP-3[isoEhlDeployment]: Deploy latest ISO on EHL target box ", 'h3')
    deploymentShellScript = "isoDeployment.sh"
    deploymentShellArgs = "${isoEhlDeploymentDict['edgeTargetNodeRootDir']}/ ${isoEhlDeploymentDict['iso_name_without_extension']}.iso ${isoEhlDeploymentDict['edgeTargetNodeRootDir']}/${isoEhlDeploymentDict['isoNames']}/isoPackageDir/"
    isoEhlDeploymentDict.put('deploymentShellScript', deploymentShellScript); isoEhlDeploymentDict.put('deploymentShellArgs', deploymentShellArgs )
    deployExecute(isoEhlDeploymentDict)
    return true
}

/**
 * Function Name : emlIsoDeployment
 * Description   :
 * Arguments     :
 * Returns       :
 */
def isoEmlDeployment(isoEmlDeploymentDict){
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def generalStageUtils = new org.utils.generalStageUtils()
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    isoEmlDeploymentDict.put('xBoxType', 'iso-eml')
    println "isoEmlDeploymentDict [method in xBoxDeployment: isoEmlDeployment]"
    println isoEmlDeploymentDict
    //** STEP-2 */
    generalDisplayUtils.sectionDisplay("STEP-1[isoEmlDeployment]: Remove previously created ISO directory", 'h3')
    sshPassCommandArray = ["rm -rf ${isoEmlDeploymentDict['isoNames']}*"]
    generalGroovyUtils.sshPassCommand(isoEmlDeploymentDict['emlTargetNodeCredId'],isoEmlDeploymentDict['emlTargetNodeIp'],sshPassCommandArray,isoEmlDeploymentDict['emlTargetNodeSshPort'])
    //** STEP-2 */
    generalDisplayUtils.sectionDisplay("STEP-2[isoEmlDeployment]: Prepare Target node for repo related details ... ", 'h3')
    generalDisplayUtils.sectionDisplay("... like removing previously deployed ISO, download latest ISO & cloning repos", 'h3')
    isoPackageDir = "${isoEmlDeploymentDict['emlTargetNodeRootDir']}/${isoEmlDeploymentDict['isoNames']}/isoPackageDir/"
    def deploymentShellScript = "isoDeploymentEmlCleanup.sh"
    def deploymentShellArgs = "${isoPackageDir}"
    isoEmlDeploymentDict.put('deploymentShellScript', deploymentShellScript); isoEmlDeploymentDict.put('deploymentShellArgs', deploymentShellArgs )
    isoDeployPrep(isoEmlDeploymentDict)
    //** STEP-3 */
    generalDisplayUtils.sectionDisplay("STEP-3[isoEmlDeployment]: EML ISO DEPLOYMENT","h2")
    deploymentShellScript = "isoDeploymentEml.sh"
    deploymentShellArgs = "${isoPackageDir}${isoEmlDeploymentDict['iso_name_without_extension']}.iso"
    isoEmlDeploymentDict.put('deploymentShellScript', deploymentShellScript); isoEmlDeploymentDict.put('deploymentShellArgs', deploymentShellArgs )
    deployExecute(isoEmlDeploymentDict)
    println "After deployExecute"
    return true
}


def deployExecute(deployExecuteDict){
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    def deploymentShellScript = deployExecuteDict['deploymentShellScript']
    def deploymentShellArgs = deployExecuteDict['deploymentShellArgs']
    def sshPassCommandArray; def targetNodeCredId; def targetNode; def targetNodeSshPort; def targetNodeRootDir; def isoDeploymentShellDir
    if(deployExecuteDict['xBoxType'] == 'iso-ehl'){
        println "EDGE Deployment shell script execution [deployExecute]"
        targetNodeCredId = deployExecuteDict['edgeTargetNodeCredId']
        targetNode = deployExecuteDict['edgeTargetNodeIp']
        targetNodeSshPort = deployExecuteDict['edgeTargetNodeSshPort']
        targetNodeRootDir = deployExecuteDict['edgeTargetNodeRootDir']
        isoDeploymentShellDir = "${targetNodeRootDir}/${deployExecuteDict['isoNames']}/devopsDsl/resources/coreload-CD/"
        sshPassCommandArray = ["""chmod 755 ${isoDeploymentShellDir}*""", """bash ${isoDeploymentShellDir}${deploymentShellScript} ${deploymentShellArgs}"""]
    }
    else if (deployExecuteDict['xBoxType'] == 'iso-eml'){
        println "EML Deployment shell script execution [deployExecute]"
        targetNodeCredId = deployExecuteDict['emlTargetNodeCredId']
        targetNode = deployExecuteDict['emlTargetNodeIp']
        targetNodeSshPort = deployExecuteDict['emlTargetNodeSshPort']
        targetNodeRootDir = deployExecuteDict['emlTargetNodeRootDir']
        isoDeploymentShellDir = "${targetNodeRootDir}/${deployExecuteDict['isoNames']}/devopsDsl/resources/coreload-CD/"
        sshPassCommandArray = ["""chmod 755 ${isoDeploymentShellDir}*""", """chown -R emadmin:eml ${isoDeploymentShellDir}*""", """bash ${isoDeploymentShellDir}${deploymentShellScript} ${deploymentShellArgs}"""]
    }
    else{
        throw new Exception("Supported iso deployType is either iso-ehl or iso-eml")
    }
    generalGroovyUtils.sshPassCommand(targetNodeCredId,targetNode,sshPassCommandArray,targetNodeSshPort)
}

def checkoutDeploymentEnablers(dirName,repo,branch,credentialsId) {
    dir ("${dirName}") {
        git branch: "${branch}", changelog: false, poll: false, url: "${repo}", credentialsId: "${credentialsId}"
    }
}
