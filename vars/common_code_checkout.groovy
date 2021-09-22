//scmObj = new org.utils.scm()
def gitLabCheckOut(allParamDict){
    println("gitLabCheckOut [start]")
    def generalStageUtils = new org.utils.generalStageUtils() 
    gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {
        updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')
        ret_code = generalStageUtils.exec_hook(allParamDict["code_checkout_prestep"])
        if(ret_code != 0) {
            error("code_checkout_prestep failed")
        }
        if(allParamDict["enableWorkspaceCleanup"] && allParamDict["enableWorkspaceCleanup"] == 'true') {
            step([$class: 'WsCleanup'])
        }
        echo "check out====== GIT ==========="
        checkout scm

        updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
        ret_code = generalStageUtils.exec_hook(allParamDict["code_checkout_poststep"])
        if(ret_code != 0) {
            error("gitLabCheckOut: code_checkout_poststep failed")
        }
        autoCommitCheck()
    }
    println("gitLabCheckOut [end]")
}

//** ------------------------INDEPENDENT CAN BE REUSED FOR ANY CODE {Required to me moved to out} ===[ENDS]==== ----------------------- **
def autoCommitCheck(){
    def autoCommit_check = sh (returnStdout: true, script: "git log -1")

    println autoCommit_check
    if (autoCommit_check.contains('autocommit: commit to trace ALM heads')){
        echo '|***************************************************************|'
        echo 'This is an autoCommit, hence the pipeline will not be triggered'
        echo '|***************************************************************|'
        return
    }//autocommit: commit to trace ALM heads
}
