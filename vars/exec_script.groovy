def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def buildNode = config.buildNode?:'Docker06'
    def stage_name = 'Unknown'
    println("BuildNode: " + buildNode)
    println(config.stageNames)
    def level = 0
    def targetIP = ''
    def targetToken = ''
    def eisReleaseVersion  = config.eisReleaseVersion ?: 'master'
    def successCount = 0
    def failedCount = 0
    def emailExtraMsg = ''
    try{
        timestamps {
            currentBuild.description = "<span style=\"background-color:green;color:#fff;padding:5px;\">${params.sourceBranch}</span>"
            node(buildNode){
                if(!(config.targetIP?.trim()) || !(config.targetToken?.trim())) {
                    checkout_nightly_box_details(eisReleaseVersion)
                    curr_dir = pwd()
                    println("Box details file : " +  curr_dir + '/nightly_release_kubernetes_cloud/box_details.yaml')
                    def system_details_file_location = curr_dir + '/nightly_release_kubernetes_cloud/box_details.yaml'
                    def system_details = readYaml file: "${system_details_file_location}"
                    println (system_details)
                    targetIP            = system_details["kubernetes_ip"]
                    controllerIP        = system_details["controller_ip"]
                    controller_username = system_details["controller_username"]
                    controller_password = system_details["controller_password"]
                    controller_port     = system_details["controller_port"]
                    targetToken         = readFile(curr_dir + '/nightly_release_kubernetes_cloud/kubernetes_token')
                } else {
                    println("reading target IP and token from Jenkinsfile")
                    targetIP            = config.targetIP
                    controllerIP        = config.controller_ip
                    controller_username = config.controller_username
                    controller_password = config.controller_password
                    controller_port     = config.controller_port
                    targetToken         = config.targetToken
                }
                println("Start exec")    

                stage("Code Checkout") {
                    git branch: "${params.sourceBranch}", credentialsId: 'ssh-Jenkins-s02', changelog: false, poll: false, url: "git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/ct/ehl-contract-test-cases.git"
                }
                println("git clone done !")
                def cnt = 1 
                def cmd = ''
                def ret = 0
                config.scripts.each {
                    cmd = it            
//                    println("CMD: "+cmd)    
                    stage_name = config.stageNames[cnt-1]?: 'Unknown'
                    stage (stage_name) {
                        cmd = cmd.replaceAll('targetIP', targetIP)
                        cmd = cmd.replaceAll('targetToken', targetToken)
                        cmd = cmd.replaceAll('controllerIP', controllerIP)
                        cmd = cmd.replaceAll('controller_username', controller_username)
                        controller_password = controller_password.replaceAll('\\$', '\\\\\\$');
                        cmd = cmd.replaceAll('controller_password', controller_password)
                        cmd = cmd.replaceAll('controller_port', controller_port)
                        println("==> Command : "+cmd)
                      //  ret = 0
                        ret = sh(returnStatus:true, script:cmd) 

                        println("command return code: " + ret.toString())
                        if(ret == 0) {
                            println("***** script execution successful *****")
                            successCount = successCount + 1
                        } else {
                            throw new Exception ("script failure") 
                            failedCount = failedCount + 1
                        }
                    }
                    cnt = cnt +  1
                }
            }
        }
    } catch(err){
        println("Catch Block !")
    	println err
        currentBuild.result = 'FAILURE'
    } finally{
        stage("Notify") {
            if(config.mailingList) {
                jaas_email {
                    mailingList="${config.mailingList}"
                    projectName="${config.project}"
                    emailExtraMsg = "Testing completed with ${successCount} successful and ${failedCount} failures"
                    message="$emailExtraMsg"
                    includeChangeSet=false
                }
            }
        }
    	println "Finally Completed"
    }
}


def checkout_nightly_box_details(eisReleaseVersion) {
    git branch: "${eisReleaseVersion}", changelog: false, credentialsId: 'ssh-Jenkins-s02', poll: false, url: 'git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/ees-machine-details/devops_deployment_infra.git'
}
