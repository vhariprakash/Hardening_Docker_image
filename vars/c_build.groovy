import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config

    body()
    def baseDir="";
    def buildnode=config.buildNode?:'Docker06'
    def projectName = "${config.project}"
    def artifactType = config.artifactType?:"*.so"

    //Devops settings.yaml location, branch and repository name
    def credentials_id = "ssh-Jenkins-s02"
    def git_branch_name = ""
    if (env.gitlabBranch == null )  {
		git_branch_name = "${params.sourceBranch}"
	} else {
		git_branch_name = "${env.gitlabBranch}"
	}
	println "git_branch_name :: " + git_branch_name
    node("${buildnode}") {
        try{
            //def enable_stages = ['approve_build', 'code_checkout','alm_integration','build_unit_test','sonar_analysis','docker_scan','yaml_deployment_dev', 'yaml_bdd' ,'release_artifact', 'publish_docker','helm_deployment_dev','update_iso_manifest' ,'helm_bdd','iso_deployment', 'bdd_publisher']
//            def enable_stages = config.enable_stages ?: ['code_checkout','build_unit_test', 'publish']
            def enable_stages =  ['code_checkout','build_unit_test', 'publish']
            def artifactory_url = config.artifactory_location
            def artifactory_repo_name = config.targetRepo
            def code_checkout_url
            def repo_name
            def sleepTime = 20
            boolean isMasterOrRelease = (params.sourceBranch == 'master' || params.sourceBranch == 'integ') ?: false
  /*******
            def hcddSettings= new hcdd.Settings()
            hcddSettings.org= 'GE Healthcare'
            hcddSettings.team= 'Coreload-EIS'
            hcddSettings.program= 'EIS Platform Services'
            hcddSettings.product= 'EIS'
            hcddSettings.branch= "${env.sourceBranch}"
            hcddSettings.release= '1.0.0'
            hcddSettings.component= "${env.JOB_NAME}"
            hcddSettings.pipelinePhase= 'DEV'                    
            **********/
            println "JP: Print 1"

            // scm object to make git calls
            def scmObj = new org.utils.scm()
            // Shell Object to call linux commands / scripts
            def shellObj = new org.utils.shell()
            projectName = config.project
            if (config.delay){
                sleepTime = "${config.delay}".toInteger()
            }
            echo pwd()
            currentBuild.description = "${git_branch_name}"
            //gitlabCommitStatus(builds: ["Docker"]) {
            stage('Code Checkout') {
                if(enable_stages.contains('code_checkout')){
                    timestamps {
                        step([$class: 'WsCleanup'])
                        echo "check out======GIT =========== on ${params.sourceBranch}"
                        checkout scm
                    }
                    code_checkout_url = shellObj.shell_ret_output("git config --local remote.origin.url")
                    println(code_checkout_url)
                    repo_name = code_checkout_url.split('/')[-1].split('\\.')[0]
                    gitSwarmUrl_ssh = shellObj.shell_ret_output("git config remote.origin.url")
                }else{
                    Utils.markStageSkippedForConditional('Code Checkout')
                }
            }
            println "JP: Print 2"
            stage('Build & Unit Test') {
		        if(enable_stages.contains('build_unit_test')){
		            def build_cmd = config.customBuildCommand ?: ' '
//                        set HTTP_PROXY=http://proxy-privzen.jfwtc.ge.com:80
//                        set HTTPS_PROXY=http://proxy-privzen.jfwtc.ge.com:80
//                        source /root/.bashrc
                    sh """
                        pwd
                        cd ${WORKSPACE}
                        make $build_cmd
				    """
                }else{
                    Utils.markStageSkippedForConditional('Build & Unit Test')
                }
            }
            println "JP: Print 3"
            def commitHash = shellObj.shell_ret_output("git rev-parse --short HEAD")

            stage('Sonar Analysis') {
                if (config.sonarProjectKey && config.sonarProjectName && enable_stages.contains('sonar_analysis')) {
                    jaas_sensor_step{
                        name= "Code Analysis"
                        settings= hcddSettings
                        jaas_step={
                            timestamps {
                                goSonar {
                                    sonarProjectKey = config.sonarProjectKey
                                    sonarProjectName = config.sonarProjectName
                                    projectName = config.project
                                    sonarProjectVersion = config.sonarProjectVersion
                                    sonarGoCoverageReportPaths = config.sonarGoCoverageReportPaths ?:''
                                    sonarExclusions = config.sonarExclusions ?: ''
                                }
                            }
                        }
                    }
                     jaas_sonar {
                            reportFile = '.scannerwork/report-task.txt'
                            sonarUserCredential = 'sonar_prod_token'
                            failBuild = isMasterOrRelease
                            settings = hcddSettings
                            sonarTimeout = 5
                        }
                }else {
                    Utils.markStageSkippedForConditional('Sonar Analysis')
                }
            }

/***  JP: ADDed publish Stage [Begin]  ***/            
            stage("Publish") {
                if(enable_stages.contains('publish')){
                    echo "Artifact Publish"
                    def fileList = get_file_list(workspace, config.filePattern)
                    println("Files:")
                    println(fileList)
                    if (fileList.trim() == '') {
                        println ("PIPELINE_ERROR: No files to publish ...")
                        throw new Exception("No files to publish")
                    }
                    //def source_file
                    def fileCount = 0
                    dir(workspace) {
                        fileList.split("\n").each {
                            if(it) {
                                try {
                                    fileCount = fileCount + 1
                                    source_file = it
                                    println("Built Artifact Source Location: " + source_file)
                                    def dest_location = "${config.artifactory_url}/artifactory/${config.targetRepo}/${config.artifactoryLocation}/${source_file}"
                                    println("Built Artifact Destination Location::-" + dest_location)
                                    sh """
                                        echo "curl -H \"X-JFrog-Art-Api:AKCp5cd5NFLTxbsWmn89JNCRSfupAGqF2Wpbva2wY94knxeny6KrAyoCuoVvpir5psuCgV5Pa\" -X PUT ${dest_location}  -T ${source_file}"
                                        curl --insecure -H "X-JFrog-Art-Api:AKCp5cd5NFLTxbsWmn89JNCRSfupAGqF2Wpbva2wY94knxeny6KrAyoCuoVvpir5psuCgV5Pa" -X PUT ${dest_location}  -T ${source_file}
                                    """
                                } catch(err) {
                                    println ("WARNING : File " + it + " skipped" + err.getMessage())
                                }
                            }
                        }
                    }
                    if (fileCount == 0) {
                        println ("PIPELINE_ERROR: No files to publish ...")
                        throw  new Exception("No files to publish")
                    }
                }
                else{
                    Utils.markStageSkippedForConditional('Publish')
                }
            }
/***  JP: ADDed publish Stage [End]  ***/
        }catch(err){
            currentBuild.result = 'FAILURE'
            emailExtraMsg = "Build Failure:"+ err.getMessage()
            throw err
        }

        finally{
            println ("finally block")
        }
    }
}
 
def get_file_list (filePath, files) {
    def shellObj = new org.utils.shell()
    def parent_folder
    file_list = ''
    dir(filePath) {
        files.split(';').each {
            println(it)
            File file = new File(it)
            if(file.parent) {
                parent_folder = file.parent
            } else {
                parent_folder = "./"
            }
            filenames = shellObj.shell_ret_output("find ${parent_folder} -type f -name \"${file.name}\"")
            file_list = file_list + filenames +"\n"
        }
    }
    return file_list
    println("done")
}


def code_checkout() {
    timestamps {
//JP:        step([$class: 'WsCleanup'])
        echo "check out======GIT =========== on ${params.sourceBranch}"
        checkout scm
    }
}
