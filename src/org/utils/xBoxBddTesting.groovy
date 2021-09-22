package org.utils;
// Utility for logs display related things
import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.*

/**
 * Function Name : dockerBddTesting
 * Description   :
 * Arguments     :
 * Returns       :
 */

def dockerBddTesting(dockerBddTestingDict){
    println "dockerBddTesting"
    echo "Executing the BDD test cases"
    dockerBddTestingDict['bddFileIncludePattern'] = 'tests/BDD/with_four_points/camera_server_infra'
    dockerBddTestingDict['bddJsonReportDirectory'] ='/cucumber_json.json'
    sh """
    cd ${env.WORKSPACE}/${dockerBddTestingDict['bddShellExecutableLocation']}/
    ./task.sh
    python -m behave2cucumber -i result.json -o cucumber_json.json
    """
    cucumber failedFeaturesNumber: 50, failedScenariosNumber: 50, failedStepsNumber: 50,
            fileIncludePattern: dockerBddTestingDict['bddFileIncludePattern'],
            jsonReportDirectory: dockerBddTestingDict['bddJsonReportDirectory'],
            pendingStepsNumber: 50, skippedStepsNumber: 50, sortingMethod: 'ALPHABETICAL', undefinedStepsNumber: 50
    return true
}


/**
 * Function Name : helmBddTesting
 * Description   :
 * Arguments     :
 * Returns       :
 */
def helmBddTesting(){
    println "helmBDDTesting"
    return true
}

/**
 * Function Name : rpmBddTesting
 * Description   :
 * Arguments     :
 * Returns       :
 */
def rpmBddTesting(rpmBddTestingDict){
    println "rpmBddTesting[start]"
    println "rpmBddTestingDict['buildType'] ${rpmBddTestingDict['buildType']}"
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def branch_type = rpmBddTestingDict['branch_type']
    def rpmDir = 'rpmDeployment'
    def dslDir = 'devopsDsl'
    def devDir = 'devSourceCode'
    println "rpmBddTestingDict : "
    println rpmBddTestingDict
    rpmArtifactPath = rpmBddTestingDict['rpmArtifactPath']
    rpmFileName = rpmArtifactPath.split("/")[-1]
    def service_name = rpmFileName.split('-')[0].trim()

    def component_test_dir = "${rpmBddTestingDict['rpmInstallPath']}/${rpmBddTestingDict['project']}/${rpmDir}/${devDir}/component-test/"
//    def service_name = rpmBddTestingDict['rpmFileName'].split('-')[0].trim()
    rpmBddTestingDict['rpmBddReportDir'] = "/root/${service_name}-${env.BUILD_NUMBER}.tar.gz"
    def rpm_bdd_report = "https://${rpmBddTestingDict['artifactory_url']}/artifactory/${rpmBddTestingDict[branch_type]['generic_repo']}/reports/${rpmBddTestingDict['project']}/bddReports-${env.BUILD_NUMBER}.tar.gz"
    //def rpm_bdd_report = "https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-eml-snapshot/reports/edison-licensing/${service_name}-bddReports-${env.BUILD_NUMBER}.tar.gz"
    //**STEP-1 RPM Component testing*/
    generalDisplayUtils.sectionDisplay("STEP-1[rpmBddTesting]: RPM pre hook, Component testing, tar packaging and post hook", 'h3')
//    def sshPassCommandArray = ["bash ${rpmBddTestingDict['rpmInstallPath']}/${rpmBddTestingDict['project']}/${rpmDir}/${rpmBddTestingDict['rpmBddShellLocation']} ${component_test_dir} ${service_name}-${env.BUILD_NUMBER}.tar.gz '''${rpmBddTestingDict['testCommand']}''' ${component_test_dir}/${rpmBddTestingDict['rpmPreStepFile']} ${component_test_dir}/${rpmBddTestingDict['rpmPostStepFile']}"]

    def sshPassCommandArray = ["${rpmBddTestingDict['testCommand']}" ]
    generalGroovyUtils.sshPassCommand(rpmBddTestingDict['rpmTargetNodeCredId'],rpmBddTestingDict['rpmTargetIp'],sshPassCommandArray,rpmBddTestingDict['rpmTargetPort'])
    //**STEP-2 RPM bdd report publish*/
    def exists = fileExists "rpmBddTestingDict['rpmInstallPath']}/results"
    if(exists) {
        withCredentials([usernamePassword(credentialsId: rpmBddTestingDict['credID_passwd'], usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            sshPassCommandArray = ["tar -cvzf ${rpmBddTestingDict['rpmInstallPath']}/${rpmBddTestingDict['project']}/${rpmDir}/${devDir}/component-test/ ${service_name}-${env.BUILD_NUMBER}.tar.gz rpmBddTestingDict['rpmInstallPath']}/results"]
            generalGroovyUtils.sshPassCommand(rpmBddTestingDict['rpmTargetNodeCredId'],rpmBddTestingDict['rpmTargetIp'],sshPassCommandArray,rpmBddTestingDict['rpmTargetPort'])
        }
    } else {
        println("BDD Reports not found !!")
    }
    println "rpmBddTesting[end]"
}


/**
 * Function Name : emlIsoBddTesting
 * Description   :
 * Arguments     :
 * Returns       :
 */
def isoEmlBddTesting(isoEmlBddTestingDict){
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def generalStageUtils = new org.utils.generalStageUtils()
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    println "isoEmlBddTestingDict [method isoEmlBddTesting in xBoxBddTesting]"
    println isoEmlBddTestingDict
    def isoBddPreCheckDictReturn = isoBddPreCheck(isoEmlBddTestingDict)
    def bddGitRepoGroupName = isoBddPreCheckDictReturn['bddGitRepoGroupName']
    def bddGitRepoName = isoBddPreCheckDictReturn['bddGitRepoName']
    def testBuildCommand = isoBddPreCheckDictReturn['testBuildCommand']
    def helmDir = 'helmDeployment'
    def base_dir = pwd(); echo base_dir
    def tarFileName = "${env.BUILD_NUMBER}".toString()+".tar.gz"
    def component_test_dir = "${base_dir}"+"/"+ "${isoEmlBddTestingDict['componentTestingBaseDirStructure']}"
    def tar_file_location = "${base_dir}/${isoEmlBddTestingDict['componentTestingBaseDirStructure']}/${tarFileName}"
    def build_directory = "${base_dir}/${isoEmlBddTestingDict['componentTestingBuildDirStructure']}/*"
    //def testBuildCommand = isoEmlBddTestingDict['testBuildCommand']
    if (isoEmlBddTestingDict['bddOnServer'] == 'false'){
        println "BDD testing on pod"
        withCredentials([usernamePassword(credentialsId: isoEmlBddTestingDict['bdd_publisher_credentials'], usernameVariable: 'bddPublisherNodeUsername', passwordVariable: 'bddPublisherNodePassword')]) {
            sshPassBDDNodeCommandArray = ["mkdir -p ${isoEmlBddTestingDict['publishDir']}/${isoEmlBddTestingDict['isoNames']}/"]
            generalGroovyUtils.sshPassCommand(isoEmlBddTestingDict['bdd_publisher_credentials'],isoEmlBddTestingDict['bdd_publisher_node'],sshPassBDDNodeCommandArray,"22")
            withCredentials([usernamePassword(credentialsId: isoEmlBddTestingDict['bddGitHttpsCredID'], usernameVariable: 'bddGitRepoUsername', passwordVariable: 'bddGitRepoPassword')]) {
                //def completeBddCommand = " bash /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${isoEmlBddTestingDict['bddPodExecuteLocation']} /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/devSourceCode/deploy.yml ${isoEmlBddTestingDict['bddPodNamespace']} ${bddGitRepoGroupName} ${bddGitRepoName} ${bddGitRepoUsername} ${bddGitRepoPassword} ${isoEmlBddTestingDict['dev_branch']} ${isoEmlBddTestingDict['componentTestingBaseDirStructure']} '\'''\'''\''${testBuildCommand}'\'''\'''\'' "
                //println "completeBddCommand ${completeBddCommand}"
                sshPassCommandArray = ["chmod 600 /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/*","bash /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/devopsDsl/resources/coreload-CD/bddPodDelete.sh /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/devSourceCode/deploy.yml",
                                    "bash /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/${isoEmlBddTestingDict['bddPodDeployLocation']} /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/devSourceCode/deploy.yml",
                                    "bash /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/${isoEmlBddTestingDict['bddPodStatusCheckLocation']} /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/devSourceCode/deploy.yml",
                                    "bash /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/${isoEmlBddTestingDict['bddPodExecuteLocation']} /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/devSourceCode/deploy.yml ${isoEmlBddTestingDict['bddPodNamespace']} ${bddGitRepoGroupName} ${bddGitRepoName} ${bddGitRepoUsername} ${bddGitRepoPassword} ${isoEmlBddTestingDict['dev_branch']} ${isoEmlBddTestingDict['componentTestingBaseDirStructure']} '\''${testBuildCommand}'\'' ",
                                    "mkdir -p /${isoEmlBddTestingDict['reportMountLocation']}/${isoEmlBddTestingDict['isoNames']}/",
                                    "bash /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/${isoEmlBddTestingDict['bddPodReportPackageLocation']} /${isoEmlBddTestingDict['emlTargetNodeRootDir']}/${isoEmlBddTestingDict['isoNames']}/${helmDir}/devSourceCode/deploy.yml ${isoEmlBddTestingDict['bddPodNamespace']} ${bddGitRepoName} ${isoEmlBddTestingDict['componentTestingBaseDirStructure']} /${isoEmlBddTestingDict['reportMountLocation']}/${tarFileName} ",
                                    "sshpass -p ${bddPublisherNodePassword} scp -r -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null /${isoEmlBddTestingDict['reportMountLocation']}/${tarFileName} ${bddPublisherNodeUsername}@${isoEmlBddTestingDict['bdd_publisher_node']}:${isoEmlBddTestingDict['publishDir']} "]
                sshPassCommandBddSpecific(isoEmlBddTestingDict['emlTargetNodeCredId'],isoEmlBddTestingDict['emlTargetNodeIp'],sshPassCommandArray,isoEmlBddTestingDict['emlTargetNodeSshPort'])
            }
        }
    }
    else if (isoEmlBddTestingDict['bddOnServer'] == 'true'){
        println "BDD testing On Server"
        node(isoEmlBddTestingDict['bddTargetName']) {
        git branch: isoEmlBddTestingDict['git_branch'], credentialsId: isoEmlBddTestingDict['pod_template_cred_id'], poll: false, url: isoEmlBddTestingDict['gitUrl']
            echo "buildType ${isoEmlBddTestingDict['buildType']}"
            componentTestCommandExecution(component_test_dir,testBuildCommand)
            componentTestTarPackaging(isoEmlBddTestingDict['buildType'],component_test_dir,tar_file_location)
            componentTestReportPublishToPublisherNode(component_test_dir,isoEmlBddTestingDict['bdd_publisher_node'],isoEmlBddTestingDict['bdd_publisher_credentials'],isoEmlBddTestingDict['publishDir'])
        }
    }
    else{
        println "bddOnServer value is ${isoEmlBddTestingDict['bddOnServer']}"
        error ("Failing due to: Kindly mention true/false for key bddOnServer")
    }
    def buildLog = currentBuild.rawBuild.log
    if (buildLog.contains('Component test execution failed')){
        error ("PIPELINE_ERROR: Component test execution failed")
    }
    else if (buildLog.contains('exit 1')){
        error ("sshPassCommand failed")
    }
    else{
        println "No exit 1 found"
    }
    println isoEmlBddTestingDict
    return true
}

/**
 * Function Name : ehlIsoBddTesting
 * Description   : Pod based BDD testing on edge box
 * Arguments     : dictionary based input
 * Returns       : no returns
 */
def isoEhlBddTesting(isoEhlBddTestingDict){
    println "isoEhlBddTestingDict [method isoEhlBddTesting in xBoxBddTesting]"
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def generalStageUtils = new org.utils.generalStageUtils()
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    def tarFileName = "${env.BUILD_NUMBER}".toString()+".tar.gz"
    def base_dir = pwd(); echo base_dir
    def component_test_dir = "${base_dir}"+"/"+ "${isoEhlBddTestingDict['componentTestingBaseDirStructure']}"
    def tar_file_location = "${base_dir}/${isoEhlBddTestingDict['componentTestingBaseDirStructure']}/${tarFileName}"
    def build_directory = "${base_dir}/${isoEhlBddTestingDict['componentTestingBuildDirStructure']}/*"
    def tarArtifactoryPublishLoc = "https://${isoEhlBddTestingDict['artifactory_url']}/artifactory/${isoEhlBddTestingDict['dev']['iso_artifactory_repo_name']}/tarFolder/"
    //** STEP-1: ISO BDD pre check and deployment node prep*/
    generalDisplayUtils.sectionDisplay("STEP-1[isoEhlBddTesting]: ISO BDD pre check and deployment node prep", 'h3')
    def isoBddPreCheckDictReturn = isoBddPreCheck(isoEhlBddTestingDict)
    isoEhlBddTestingDict['bddGitRepoGroupName'] = isoBddPreCheckDictReturn['bddGitRepoGroupName']
    isoEhlBddTestingDict['bddGitRepoName'] = isoBddPreCheckDictReturn['bddGitRepoName']
    isoEhlBddTestingDict['testBuildCommand'] = isoBddPreCheckDictReturn['testBuildCommand']
    println isoEhlBddTestingDict
    //** STEP-2: Create Publish directory in BDD publisher node*/
    generalDisplayUtils.sectionDisplay("STEP-2[isoEhlBddTesting]: Create Publish directory in BDD publisher node", 'h3')
    withCredentials([usernamePassword(credentialsId: isoEhlBddTestingDict['bddPublisherCredentials'], usernameVariable: 'bddPublisherNodeUsername', passwordVariable: 'bddPublisherNodePassword')]) {
        sshPassBDDNodeCommandArray = ["mkdir -p ${isoEhlBddTestingDict['publishDir']}/${isoEhlBddTestingDict['isoname']}/"]
        generalGroovyUtils.sshPassCommand(isoEhlBddTestingDict['bdd_publisher_credentials'],isoEhlBddTestingDict['bdd_publisher_node'],sshPassBDDNodeCommandArray,"22")
        //** STEP-3: tar devSourceCode and devopsDsl*/
        generalDisplayUtils.sectionDisplay("STEP-3[isoEhlBddTesting]: tar devSourceCode and devopsDsl", 'h3')
        withCredentials([usernamePassword(credentialsId: isoEhlBddTestingDict['bddGitHttpsCredID'], usernameVariable: 'bddGitRepoUsername', passwordVariable: 'bddGitRepoPassword')]) {
            withCredentials([usernamePassword(credentialsId: isoEhlBddTestingDict['credID_passwd'], usernameVariable: 'ApiTokenUsername', passwordVariable: 'ApiTokenPassword')]) {
                withCredentials([usernamePassword(credentialsId: isoEhlBddTestingDict['edgeTargetNodeCredId'], usernameVariable: 'edgeTargetNodeUsername', passwordVariable: 'edgeTargetNodePassword')]) {
                    sh """
                        pwd
                        tar -czf ${isoEhlBddTestingDict['isoname']}.tar.gz ${env.WORKSPACE}/devopsDsl/ ${env.WORKSPACE}/devSourceCode/
                        curl  --silent -H X-JFrog-Art-Api:${ApiTokenPassword} -X PUT ${tarArtifactoryPublishLoc}/${isoEhlBddTestingDict['isoname']}.tar.gz -T ${env.WORKSPACE}/${isoEhlBddTestingDict['isoname']}.tar.gz
                    """
                    //** STEP-4: Execute edge BDD pod wrapper shell script*/
                    generalDisplayUtils.sectionDisplay("STEP-4[isoEhlBddTesting]: Execute edge BDD pod wrapper shell script", 'h3')
                    sshPassCommandArray = ["bash /${isoEhlBddTestingDict['edgeTargetNodeRootDir']}/${isoEhlBddTestingDict['isoname']}/${isoEhlBddTestingDict['bddEdgeWrapperShellLocation']} ${isoEhlBddTestingDict['isoname']} ${isoEhlBddTestingDict['edgeTargetNodeRootDir']} ${isoEhlBddTestingDict['bddPodNamespace']} ${isoEhlBddTestingDict['bddGitRepoName']} ${isoEhlBddTestingDict['bddGitRepoGroupName']} ${bddGitRepoUsername} ${bddGitRepoPassword} ${isoEhlBddTestingDict['dev_branch']} '\'''\'''\''${isoEhlBddTestingDict['testBuildCommand']}'\'''\'''\'' ${isoEhlBddTestingDict['componentTestingBaseDirStructure']} ${tarFileName} ${bddPublisherNodeUsername} ${bddPublisherNodePassword} ${isoEhlBddTestingDict['publishDir']} ${ApiTokenPassword} ${tarArtifactoryPublishLoc} ${edgeTargetNodeUsername} ${edgeTargetNodePassword} ${isoEhlBddTestingDict['edgeTargetNodeIp']} ${isoEhlBddTestingDict['edgeTargetNodeSshPort']}"]
                    def bddPodDeploy = generalGroovyUtils.sshPassCommand(isoEhlBddTestingDict['edgeTargetNodeCredId'],isoEhlBddTestingDict['edgeTargetNodeIp'],sshPassCommandArray,isoEhlBddTestingDict['edgeTargetNodeSshPort'])
                    if (bddPodDeploy.contains('exit 1')){
                        sh """
                        echo "PIPELINE_ERROR: BDD Pod deployment issue"
                        exit 1
                        """
                    }
                }
            }
        }
    }
    def buildLog = currentBuild.rawBuild.log
    if (buildLog.contains('Component test execution failed')){
        error ("PIPELINE_ERROR: Component test execution failed")
    }
    else if (buildLog.contains('exit 1')){
        error ("sshPassCommand failed")
    }
    else{
        println "No exit 1 found"
    }
    return true
}

def isoBddPreCheck(isoBddPreCheckDict){
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    def generalStageUtils = new org.utils.generalStageUtils()
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    def tarFileName = "${env.BUILD_NUMBER}".toString()+".tar.gz"
    def isoBddPreCheckDictReturn = [:]
    String gitSwarmUrl_https
    def bddGitRepoGroupName
    def bddGitRepoName
    println "isoBddPreCheckDict [method isoBddPreCheckDict in xBoxBddTesting]"
    println isoBddPreCheckDict
    dir('bdd'){
        jaas_sensor_step{
            name= "Code Checkout"
            settings= hcddSettings
            jaas_step={
                step([$class: 'WsCleanup'])
                retry(3) {
                sleep 5
                generalStageUtils.code_checkout()
                }
            }
        }
        if ( isoBddPreCheckDict['bddOnServer'] && !(fileExists('deploy.yml')) ){
            echo "depoy.yml is mandatory for BDD Pod creation for non bddOnServer execution. Please add in the project root directory."
            System.exit(1)
        }
        if(!(isoBddPreCheckDict['testBuildCommand']) || (isoBddPreCheckDict['testBuildCommand'] == '')) {
            if(isoBddPreCheckDict['buildType'] == 'maven') {
                testBuildCommand = "mvn clean install"
            } else {
                testBuildCommand = '''gradle clean build --stacktrace --info'''
            }
        }
        else{
            testBuildCommand = isoBddPreCheckDict['testBuildCommand']
        }
        if(isoBddPreCheckDict['dev_repo'].startsWith('git')) {
            gitSwarmUrl_https = "https://" + "${isoBddPreCheckDict['dev_repo']}".split('@')[1].replaceFirst(/:/, "/")
            println "inside if gitSwarmUrlSsh.startsWith('git') ${gitSwarmUrl_https}"
            bddGitRepoGroupName = gitSwarmUrl_https.tokenize('/')[2]
            println gitSwarmUrl_https.tokenize('/')
        }//if
        else {
            gitSwarmUrl_https = isoBddPreCheckDict['dev_repo']
            println "inside else ${gitSwarmUrl_https}"
            bddGitRepoGroupName = gitSwarmUrl_https.tokenize('/')[2]
            println gitSwarmUrl_https.tokenize('/')
        }//else
        bddGitRepoName= gitSwarmUrl_https.tokenize('/').last().split("\\.")[0]
        //isoBddPreCheckDictReturn['testBuildCommand'] = "${testBuildCommand}"
        isoBddPreCheckDictReturn.put('testBuildCommand', "${testBuildCommand}")
        isoBddPreCheckDictReturn.put('bddGitRepoGroupName', bddGitRepoGroupName)
        isoBddPreCheckDictReturn.put('bddGitRepoName', bddGitRepoName)
    }
    return isoBddPreCheckDictReturn
}

//** Common variables */
def componentTestCommandExecution(component_test_dir,build_command){
    try{
        sh """
            cd "${component_test_dir}"
            ${build_command} || echo "Component test execution failed"
        """
    }
    catch(err){
        println "Component test execution failed"
    }
}

def componentTestExecutionGradle(){
    if (fileExists("${component_test_dir}/build.gradle")) {
        componentTestCommandExecution(component_test_dir,build_command)
    }
    else {
        ansiColor('xterm') {
            println "Skipping execution of component BDD tests for now as they are not available. Please add the tests at the earliest"
        }
        System.exit(0)
    }
}


def componentTestTarPackaging(project_build_type,component_test_dir,tar_file_location){
    if( "${project_build_type}" == 'gradle'){
        sh """
        if [[ -d ${component_test_dir}/build && -d ${component_test_dir}/target ]]
        then
            cd "${component_test_dir}" && tar -cvf "${tar_file_location}" build/ target/
        else
            echo "PIPELINE_ERROR: ${component_test_dir}/target and/or ${component_test_dir}/build folder does not exist"
            exit 1
        fi
    """
    }
    else{
        sh """
        if [[ -d ${component_test_dir}/target ]]
        then
            cd "${component_test_dir}" && tar -cvf "${tar_file_location}" target/
        else
            echo "PIPELINE_ERROR: ${component_test_dir}/target folder does not exist"
            exit 1
        fi
    """
    }
}

def componentTestReportPublishToPublisherNode(component_test_dir,bdd_publisher_node,bdd_publisher_credentials,publishDir){
    if (fileExists("${component_test_dir}")) {
        dir("${component_test_dir}") {
            withCredentials([usernamePassword(credentialsId: "${bdd_publisher_credentials}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                sh """
                sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "$USERNAME"@"${bdd_publisher_node}" mkdir -p $publishDir
                sshpass -p "$PASSWORD" scp -r -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${build_number}".tar.gz "${USERNAME}"@${bdd_publisher_node}:"${publishDir}"
                """
            }
        }
    }
    else{
        println "${component_test_dir} component_test_dir doesn't exist. Nothing to publish"
    }
}

//Added for EHL and EML deployment as the BDD outputs were not getting printed with generic ssh method
def sshPassCommandBddSpecific(targetNodeCredId,targetNodeIP,sshPassCommandArray,PORT){
     def inputValidation = ['targetNodeCredId':targetNodeCredId, 'targetNodeIP':targetNodeIP,'sshPassCommandArray':sshPassCommandArray,'PORT':PORT]
     inputValidation.each{ key, value ->
         if (value == null || value == '' || value == ' '){
             sh"""
             echo "sshPassCommand validation failed as the value of ${key} is ${it}"
             exit 1
             """
         }
     }
     sshPassCommandArray.each {
        commandLineTemplate = "Executing command: " + it
        println commandLineTemplate
        def targetNodeCommand = it
        withCredentials([usernamePassword(credentialsId: targetNodeCredId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        sh"""
         sshpass -p "$PASSWORD" ssh -p ${PORT} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${USERNAME}@${targetNodeIP} "${targetNodeCommand}"
        """
        commandLineCompletionTemplate = "Completed execution of command: " + it
        println commandLineCompletionTemplate
        }
    }
    println "sshPassCommand completed"

 }
