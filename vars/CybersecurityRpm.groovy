def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def emailExtraMsg="";
    def buildnode=config.buildNode?:'docker66'
	
	if (!(env.gitlabBranch == null ))  { 
        properties([
            parameters([
            gitParameter(branch: '',
                branchFilter: 'origin/(.*)',
                defaultValue: "${env.gitlabBranch}",
                description: '',
                name: 'sourceBranch',
                quickFilterEnabled: false,
                selectedValue: 'NONE',
                sortMode: 'NONE',
                tagFilter: '*',
                type: 'PT_BRANCH')
            ])
        ])
    }
    else {
        properties([
            parameters([
            gitParameter(branch: '',
                branchFilter: 'origin/(.*)',
                description: '',
                name: 'sourceBranch',
                quickFilterEnabled: false,
                selectedValue: 'NONE',
                sortMode: 'NONE',
                tagFilter: '*',
                type: 'PT_BRANCH')
            ])
        ])
    }
    node("${buildnode}") {
	    env.JAAS_LOGGER_LEVEL= "FINE"
        env.http_proxy= ''
        env.https_proxy= ''
        echo " Branch is ${params.sourceBranch}"
        def hcddSettings= new hcdd.Settings() 
        hcddSettings.org= 'GE Healthcare' 
        hcddSettings.team= 'Coreload-EIS' 
        hcddSettings.program= 'EIS Platform Services' 
        hcddSettings.product= 'EIS' 
        hcddSettings.branch= "${params.sourceBranch}"
        hcddSettings.release= '1.0.0' 
        hcddSettings.component= "${env.JOB_NAME}" 
        hcddSettings.pipelinePhase= 'DEV'
        try{
              
            def baseDir = pwd()
            echo baseDir
            def git_branch_name = "${params.sourceBranch}"
            def publish_filePattern = "${config.filePattern}"
            def publish_targetRepo= "${config.targetRepo}"
            def publish_customBuildCommand= "${config.customBuildCommand}"
            def build_number = "${env.BUILD_NUMBER}".toString()
            def projectName = "${config.project}"
            echo "${projectName}"
            //Set Jenkins Job description
            currentBuild.description = "<span style=\"background-color:green;color:#fff;padding:5px;\">${params.sourceBranch}</span>"
            def blockBuilds = "${env.block_builds}"
            def blockedBranches = "${env.blocked_branches}"
            if (("${params.sourceBranch}" =~ /${blockedBranches}/) && ("${blockBuilds}" == 'yes')) {
                stage ('Approve The Build') {
                    timeout(time: 1, unit: 'HOURS') {
                        mail to: 'Manish.Mehra@ge.com, Santosh.Dj@ge.com, Harsh.Kumar@ge.com, justin.holder@ge.com',
                        subject: "Job: ${JOB_NAME} is waiting up for your approval",
                        body: "Please go to ${BUILD_URL}input and approve or abort the build."
                        metadata = input id: 'Approve', message: "Builds have been blocked by administrator. Only authorised people can approve.", submitter: '212689636,305025624,212012960,212669121'
                    }
                }
            }
            
            stage('Code Checkout') {
                jaas_sensor_step{ 
                    name= "Code Checkout" 
                    settings= hcddSettings 
                    jaas_step={
                        code_checkout()
                    }
                }
            }
            def devops_deploy_branch = 'staging'
            //get gradle project version
            def issaVersion = sh(returnStdout: true, script: "echo \"${config.projectVersion}\" | sed -e \"s/[^0-9\\.]//g\"").trim()
            echo issaVersion
            def commitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
            echo commitHash
            def rpm_name
            def rpm_path
            def rpm_publish_repo
            def rpm_manifest_entry = "hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-eis-snapshot-all/" +"${rpm_name}"
            //START: Code to check if there is a change in feature file
            if(("${config.enableEtymo}"=='true')&&(env.gitlabBranch == 'master')){
                def git_last_commit = sh returnStdout: true, script: """ git log -m -1 --name-only --pretty="format:" ${commitHash} """
                
                String feature_update_flag
                if (git_last_commit.contains('.feature')){
                    feature_update_flag = 'true'
                }
                else{
                    feature_update_flag = 'false'
                }
                echo "${feature_update_flag}"
                if(feature_update_flag=='true'){
                    echo 'Etymo steps should get executed now'
                }
                else { 
                    echo "Etymo steps won't be executed since there is no change in feature file"
                }
            
                stage('ALM Integration') {    
                    if(feature_update_flag=='true'){
                        def testHeadEtymo = "${config.testHead}".toString()
                        def requestHeadEtymo = "${config.requestHead}".toString()
                        def extra_parameters = "${config.etymoParameters}" 
                        def commit_message = "${git_last_commit}"
                        etymo {
                            feature_file_loc = 'component-test/src/test/resources/feature/'
                            test_head = "${testHeadEtymo}".toString()
                            request_head = "${requestHeadEtymo}".toString()
                            etymo_extra_parameters = "${extra_parameters}"
                            commitMessage = "${commit_message}"
                        }
                    }
                }
                
            }
            //END: Code to check if there is a change in feature file
        def buildName = "${params.sourceBranch}" + (env.gitlabMergeRequestIid ? "Coreload=${env.gitlabMergeRequestIid}" : "")
        def arch = config.arch ?: 'hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-eis-all/nextgen_build:4.1'
        def contArgs = '''-v /dockerspace:/dockerspace:rw -v /dockerspace/sonarCache:/root/.sonar/cache -v /tmp:/tmp 
        -v /var/spool/jenkins:/var/spool/jenkins:ro --net=host'''
        //withDockerContainer(args: "$contArgs", image: "$arch") {
            baseDir = sh(script: 'pwd', returnStdout: true)
            echo "************${baseDir}*****************"    
            stage('Create Rpm'){
              sh """
                rm -rf /root/rpmbuild
                mkdir -p /root/rpmbuild
                cp -a ${env.workspace}/rpmbuild/.  /root/rpmbuild
              """     
             // dir('/root/rpmbuild') {  
               if(config.project == 'ees-antivirus-ondemand') {   
                sh '''
                  yum install -y rpmdevtools
                  cd ~/rpmbuild
                  QA_RPATHS=$[0x0001|0x0002] rpmbuild -bb ~/rpmbuild/SPECS/clamav.spec
                  #Query rpm
                  #rpm -qa --last | grep clamav
                  #Remove rpm
                 # rpm -ev --nodeps clamav-0.100.1-1.el7.centos.x86_64
                  '''
                  rpm_name = 'OnDemandScan-clamav-0.100.1-1.el7.centos.linux.x86_64.rpm'
                  rpm_path = "/root/rpmbuild/RPMS/" + "${rpm_name}"
                  rpm_publish_repo = "https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-eis-snapshot-all/" + "clamav-rpms/" + "${rpm_name}"
              }
              if(config.project == 'ees-antivirus-onaccess') {
                sh '''
                  yum install -y rpmdevtools
                  cd ~/rpmbuild
                  QA_RPATHS=$[0x0001|0x0002] rpmbuild -bb ~/rpmbuild/SPECS/clamav.spec
                 '''
                  rpm_name = 'OnAccessScan-clamav-0.100.1-1.el7.centos.linux.x86_64.rpm'
                  rpm_path = "/root/rpmbuild/RPMS/" + "${rpm_name}"
                  rpm_publish_repo = "https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-eis-snapshot-all/" + "clamav-rpms/" + "${rpm_name}"
              }    
             
             //}
            } 
       // }
          stage('Publish Rpm'){
              withCredentials([usernamePassword(credentialsId: 'gip_sv01_artifactory_eu', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                  sh """
                    unset http_proxy
                    unset https_proxy
                    curl -H "X-JFrog-Art-Api:$PASSWORD" -T ${rpm_path} ${rpm_publish_repo}
                """
              }
          }  

           /* stage ('Publish Tar'){
               withCredentials([string(credentialsId: 'ArtifactoryLoginToken', variable: 'ApiToken')]) {
                    sh """
                        unset http_proxy
                        unset https_proxy
                        curl -H "X-JFrog-Art-Api: ${ApiToken}" -T ${tar_name} ${tar_publish_repo}
                    """
                }
            }
            stage('Release Artifact'){
                dir ('devops-deploy') {
                    git branch: "${devops_deploy_branch}", changelog: false, credentialsId: 'root-user-docker06', poll: false, url: 'git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/devops-deploy.git'
                    echo 'devops-deploy repo'
                    sh 'ls -lart'
                    def titanium_manifest_file_location = "${baseDir}" + '/devops-deploy' + '/EIS_TitaniumInstaller_Manifest.yaml'
					echo " ${titanium_manifest_file_location}"
				    def manifest_file = readYaml file: "${titanium_manifest_file_location}"
                    // writeYAML function doesn't overWrite the existing YAML 
                    // It fails if the YAML file is already existing
                    sh "rm -rf ${titanium_manifest_file_location}"
                    manifest_file['IAAS']["${projectName}"] = "${tar_manifest_entry}"
                    writeYaml file: titanium_manifest_file_location, data: manifest_file
                    // START: git push block
                    def add_message = sh returnStdout: true, script: """git add ${titanium_manifest_file_location}"""
                    echo add_message
                    def commit_message_manifest = sh returnStdout: true, script: """git commit --allow-empty -m  \'Updating EIS_TitaniumInstaller_Manifest file \' """
                    def commit_hash_manifest = commit_message_manifest.split(' ')[1].split(']')[0]
                    def reset_manifest = sh returnStdout: true, script: """ git reset --hard """
                    def pull_message_manifest = sh returnStdout: true, script: """git pull --rebase -s recursive -X ours origin ${devops_deploy_branch}"""
                    def push_message_manifest = sh returnStdout: true, script: """git push origin ${devops_deploy_branch}"""
                    echo push_message_manifest
                    def commit_hash_return_code_manifest = sh returnStdout: true, script: """git log origin/${devops_deploy_branch} | grep -c \"commit ${commit_hash_manifest}\""""
                    if (!commit_hash_return_code_manifest.toString().contains('1')){
                        error("Commit id is not available in Git. Git Push Failed.")
                    }
                    else {
                        echo 'Successfully Pushed to devops-deploy repo'
                        echo commit_hash_return_code_manifest
                    }
                    //END: git push block
                }
            }*/

        
        }catch(err){
            currentBuild.result = 'FAILURE'
            emailExtraMsg = "Build Failure:"+ err.getMessage()
            throw err
        }

        finally{
		  try {
		    jaas_sensor_postjob{ 
                 settings= hcddSettings 
            }			
            if(config.mailingList) {
                jaas_email {
                    mailingList="${config.mailingList}"
                    projectName="${config.project}"
                    message="$emailExtraMsg"
                    includeChangeSet=true
                }
            }
            if(config.flowdockApiToken) {
                jaas_flowdock {
                    apiToken="$config.flowdockApiToken"
                    message=emailExtraMsg
                }
            }
        } catch(err){
            currentBuild.result = 'FAILURE'
            emailExtraMsg = "Build Failure:"+ err.getMessage()
            throw err
        }
		finally {
		    properties([
            parameters([
            gitParameter(branch: '',
                branchFilter: 'origin/(.*)',
                description: '',
                name: 'sourceBranch',
                quickFilterEnabled: false,
                selectedValue: 'NONE',
                sortMode: 'NONE',
                tagFilter: '*',
                type: 'PT_BRANCH')
            ])
          ])
		}
    }
		
  }
 } 

def code_checkout() {
    timestamps {
        step([$class: 'WsCleanup'])
        echo "check out======GIT =========== on ${params.sourceBranch}"
        checkout scm
        
        
    }
}
