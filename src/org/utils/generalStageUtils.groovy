package org.utils;

//** ------------------------INDEPENDENT CAN BE REUSED FOR ANY CODE {Required to me moved to out} ===[BEGINS]==== ----------------------- **
/**
 Function Name : code_checkout
 Description   : Cleans the workspace and checks out the code with jenkins scm values
 Arguments     :
 Returns       : git url, branch or commit ID checked out in build node
*/
def code_checkout() {
    try{
        timestamps {
            step([$class: 'WsCleanup'])
            echo "check out======GIT =========== on ${params.sourceBranch}"
            checkout scm
        }
    }
    catch(err){
        println "*************************************************************************************"
        println "**********Oops....... An uncaught exception occured**********************************"
        println "**********Opportunity to catch this exception with better display message************"
        println "*************************************************************************************"
        throw err
    }
}
def checkedoutCodeDetails(enableDebugForDSL){

    println("checkedoutCodeDetails [start]")
    def repoDetails
    try {
        //node ('Docker06') {
            def generalDisplayUtils = new org.utils.generalDisplayUtils()
            def shellObj = new org.utils.shell()
            def code_checkout_url = shellObj.shell_ret_output("git config --local remote.origin.url")
            def repo_name = code_checkout_url.split('/')[-1].split('\\.')[0]
            def gitSwarmUrl_ssh = shellObj.shell_ret_output("git config remote.origin.url")
            def debugMap = ['code_checkout_url': code_checkout_url, 'gitSwarmUrl_ssh': gitSwarmUrl_ssh];
            generalDisplayUtils.debugLinesWithEnable(debugMap, enableDebugForDSL)
            repoDetails = ["code_checkout_url": code_checkout_url,"repo_name": repo_name, "gitSwarmUrl_ssh": gitSwarmUrl_ssh]
        //}
    } catch (err) {
        println ("PIPELINE_ERROR checkedoutCodeDetails : " + err.getMessage())
        throw err
    }
    println("checkedoutCodeDetails [end]")
    return repoDetails
}


def autoCommitCheck(){
    def autoCommit_check = sh (returnStdout: true, script: "git log -1")
    println autoCommit_check
    if (autoCommit_check.contains('autocommit: commit to trace ALM heads')){
        echo '|***************************************************************|'
        echo 'This is an autoCommit, hence the pipeline will not be triggered'
        echo '|***************************************************************|'
        currentBuild.description = "<span style=\"background-color:green;color:#fff;padding:5px;\">autoCommit: block pipeline</span>"
        return
    }//autocommit: commit to trace ALM heads
}

// This function is not implemented completely, it gives master by default.
// This need to be implemented to parse Jenkinsfile and get the branch name or any other better method
def getDSLBranchName() {
    println("getDSLBranchName [start]")
    def dsl_branch = "master"
    dir(env.WORKSPACE) {
        def exists = fileExists 'Jenkinsfile'
        if(exists) {
            dsl_branch = "master"
        } else {
            println("WARNING: unable to get DSL branch info. Taking default branch as master")
        }
    }
    println("getDSLBranchName [end]")
    return dsl_branch
}

def settingsYamlDict(git_branch_name, modality) {
    println("settingsYamlDict [start]")
    println("modality:"+modality)
    try {
        def configYamlData
        if (git_branch_name.contains("devops")) {
            settingsYamlDict = libraryResource 'build-config/settings_test.yaml'
            println("settings.yaml = settings_test.yaml")
        } else {
            settingsYamlDict = libraryResource 'build-config/settings_master.yaml'
            println("settings.yaml = settings_master.yaml")
        }
        settingsYamlDict = readYaml text: (settingsYamlDict)
    } catch (err) {
       println ("PIPELINE_ERROR settingsYamlDict : " + err.getMessage())
       if (err.getMessage().contains('Ambiguous method overloading for method java.util.LinkedHashMap#plus')){
           println "FAILURE_CAUSE settingsYamlDict : Modality is missing $modality in settings_yaml file in branch $settings_repo_branch"
       }
        throw err
    }
    println("settingsYamlDict [end]")
    return settingsYamlDict[modality]
}

def getArtifactoryCredentialAPIKey(artifactory_url, settingsYamlDict, type)
{
    println("getArtifactoryCredentialAPIKey [start]")
    def artifactory_credID_api_key
    if(artifactory_url.contains('blr-artifactory')) {
        artifactory_credID_api_key = settingsYamlDict['blr_artifactory_credID_'+type]
    } else if(artifactory_url.contains('hc-eu-west-aws-artifactory')) {
        artifactory_credID_api_key = settingsYamlDict['eu_artifactory_credID_'+type]
    } else if(artifactory_url.contains('hc-us-east-aws-artifactory')) {
        artifactory_credID_api_key = settingsYamlDict['us_artifactory_credID_'+type]
    } else {
        println ("WARING :  ************  Unknown Artifactory ***********")
        println("Artifactory:"+artifactory_url)
    }
    println("getArtifactoryCredentialAPIKey [end]")
    return (artifactory_credID_api_key)
}

def exec_hook(scriptName) {
    def ret = 0
    println("exec_hook [start]")
    if(scriptName) {
        def exists = fileExists scriptName
        println("executing : "+scriptName)
        dir (env.WORKSPACE) {
            ret = sh(returnStatus: true, returnStdout: true, script: "${scriptName}")
        }
    } else {
        println ("exec_hook : Nothing to execute")
    }
    println("exec_hook [end]")
    return ret
}

def exec_command(scriptName) {
    println("exec_command [start]")
    if(scriptName) {
        def exists = fileExists scriptName
        println("executing : "+scriptName)
        dir (env.WORKSPACE) {
            ret = sh(script: "${scriptName}", returnStdout: true).trim()
            println "return value: " + ret
        }
    } else {
        println ("exec_command : Nothing to execute")
    }
    println("exec_command [end]")
    return ret
}

