def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def target = 'eis-devops'
    def projectVersion = "${config.version}"
    def git_branch_name = "${env.gitlabBranch}"
    def projectName = "${config.project}"
    def deployment_parent_folder = "${config.deployment_folder_location}"
    def source_deployment_folder = "${config.source_deployment_folder}"
    def hostip_dev_env = "${env.hostip_dev_env}"
    def dev_token = "${env.dev_token}"
    def manifest_folder_location = "deploy/deployment_manifest"
    def chartFile = "deploy/charts/eis-flowmanager/Chart.yaml"
    def chart_name = source_deployment_folder.split('/')[-1]
    def list_of_manifest_files
    def isoGroupName = "${config.iso_group_name}"
    def commitHash =''
    def manifest_file_loc
    def sleepTime = "${config.delay}".toInteger()
    def installation_namespace = "${config.namespace}"
    def additional_values_file_location = "deploy/charts/eis-flowmanager/additional_values.yaml"
    def additional_values_parsed_data = [:]
    
    try{
        def blockBuilds = "${env.block_builds}"
        def blockedBranches = "${env.blocked_branches}"
        String commit_hash_ci_skip= ''
        if (("${env.gitlabBranch}" =~ /${blockedBranches}/) && ("${blockBuilds}" == 'yes')) {
            stage ('Approve The Build') {
                timeout(time: 1, unit: 'HOURS') {
                    mail to: 'Manish.Mehra@ge.com, Santosh.Dj@ge.com, Harsh.Kumar@ge.com',
                    subject: "Job: ${JOB_NAME} is waiting up for your approval",
                    body: "Please go to ${BUILD_URL}input and approve or abort the build."
                    metadata = input id: 'Approve', message: "Builds have been blocked by administrator. Only authorised people can approve.", submitter: '212689636,305025624,212669121'
                }
            }
        }

        node (target){
            currentBuild.description = "<span style=\"background-color:green;color:#fff;padding:5px;\">${env.gitlabBranch}</span>"
            
            stage('Code Checkout') {
                checkout()
            }
            def remoteURL = sh(returnStdout: true, script: 'git config --get remote.origin.url').trim()
            def project_name = remoteURL.split('/')[-1].split('\\.')[0]
            def additional_values_existence = fileExists "${additional_values_file_location}"
            def codeCommitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim() 
            println "additional_values_existence: ${additional_values_existence}"
            if (additional_values_existence) {
                print 'exists'
                additional_values_parsed_data = readYaml file: (additional_values_file_location)
            }
            stage('Build Flowmanager Server') {
                def arch = config.arch ?: 'hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/flowmanager:0.0.4'
                def contArgs = '''-v /dockerspace:/dockerspace:rw -v /dockerspace/sonarCache:/root/.sonar/cache -v /tmp:/tmp 
                -v /var/spool/jenkins:/var/spool/jenkins:ro --net=host'''
                    withDockerContainer(args: "$contArgs", image: "$arch") {
                        baseDir = sh(script: 'pwd', returnStdout: true)
                        echo "************${baseDir}*****************"
                    //docker.image('hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/flowmanager:0.0.4').inside('')
                        sh 'cp /certs/cacerts ~/.gradle/ ; cp /certs/gradle.properties ~/.gradle/gradle.properties'
                        sh ' gradle --version'
                        sh ' java -version'
                        sh 'gradle build --exclude-task publish --exclude-task test --stacktrace'
                    }
                }   
                echo "00"
            stage('Publish Server Docker Image') {
                docker.withRegistry('https://hc-eu-west-aws-artifactory.cloud.health.ge.com', 'gip_sv01_artifactory_eu') {
                    commitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    image="hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/eis-flowmanager/flowmanager_server"
                    imageName="$image:$projectVersion-$commitHash"
                    sh "docker build -t $imageName -f docker/server/Dockerfile ."
                    sh "docker push $imageName"
                    sh "docker rmi $imageName"
                }
                if (!(additional_values_parsed_data['eis_flowmanager'])){
                    additional_values_parsed_data['eis_flowmanager']=[:]
                }
                additional_values_parsed_data['eis_flowmanager']['server']=[:]
                additional_values_parsed_data['eis_flowmanager']['server']['deployment']=[:]
                additional_values_parsed_data['eis_flowmanager']['server']['deployment']['image']=image
                additional_values_parsed_data['eis_flowmanager']['server']['deployment']['imageTag']="${projectVersion}-${commitHash}"
            }
            stage('Build and Publish UI Docker Image') {
                docker.withRegistry('https://hc-eu-west-aws-artifactory.cloud.health.ge.com', 'gip_sv01_artifactory_eu') {
                    commitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    image = "hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/eis-flowmanager/flowmanager_ui"
                    imageName="$image:$projectVersion-$commitHash"
                    sh "docker build -t $imageName -f docker/ui/Dockerfile ."
                    sh "docker push $imageName"
                    sh "docker rmi $imageName"
                }
                additional_values_parsed_data['eis_flowmanager']['ui']=[:]
                additional_values_parsed_data['eis_flowmanager']['ui']['deployment']=[:]
                additional_values_parsed_data['eis_flowmanager']['ui']['deployment']['image']=image
                additional_values_parsed_data['eis_flowmanager']['ui']['deployment']['imageTag']="${projectVersion}-${commitHash}"
                
            }
            stage('Build and Publish Curator Docker Image') {
                docker.withRegistry('https://hc-eu-west-aws-artifactory.cloud.health.ge.com', 'gip_sv01_artifactory_eu') {
                    commitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    image = "hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/eis-flowmanager/flowmanager_curator"
                    imageName="$image:$projectVersion-$commitHash"
                    sh "cd docker/curator && docker build -t $imageName ."
                    sh "docker push $imageName"
                    sh "docker rmi $imageName"
                }
                additional_values_parsed_data['eis_flowmanager']['curator']=[:]
                additional_values_parsed_data['eis_flowmanager']['curator']['job']=[:]
                additional_values_parsed_data['eis_flowmanager']['curator']['job']['image']=image
                additional_values_parsed_data['eis_flowmanager']['curator']['job']['imageTag']="${projectVersion}-${commitHash}"
            }

            //update additional values file with the data
            sh "rm -rf ${additional_values_file_location}"
            writeYaml file: additional_values_file_location, data: additional_values_parsed_data
            def manifest_add_message = sh returnStdout: true, script: """git add ${additional_values_file_location}"""

            //update manifest file's version, release and helm_package keys
            list_of_manifest_files = sh returnStdout: true, script: """ls ${manifest_folder_location}/*-manifest.yaml"""
            list_of_manifest_files.split('\n').each {
                println "Manifest File Name: ${it}"
                def manifest_file_parsed_data = readYaml file: it
                manifest_file_parsed_data['app']['version']=projectVersion
                manifest_file_parsed_data['app']['release']=commitHash
                manifest_file_parsed_data['app']['helm_package']= chart_name + '-' + projectVersion + '-' + commitHash + '.tgz'
                sh "rm -rf ${it}"
                writeYaml file: it, data: manifest_file_parsed_data
                def it_write_file=readFile(it).replaceAll(/\'/,/\"/)
                sh "rm -rf ${it}"
                writeFile file: it, text: it_write_file
                sh returnStdout: true, script: """git add ${it}"""
            }
            println manifest_add_message
             
            //update Chart.yaml's version
            def chart_file_parsed_data = readYaml file: chartFile
            println chart_file_parsed_data
            chart_file_parsed_data['version'] = projectVersion + '-' + commitHash
            sh "rm -rf ${chartFile}"
            writeYaml file: chartFile, data: chart_file_parsed_data
	        sh returnStdout: true, script: """git add ${chartFile}"""

             /// START GIT 
            gitPush{
                commit_hash_map_file = 'commitHash.yaml'
                commitMessage = '[ci-skip]'
                gitPushBranch = git_branch_name
            }       
            // END GIT BLOCK*/
            def commitHashData = readYaml file: ('commitHash.yaml')
		    commit_hash_ci_skip = commitHashData['commit_hash_ci_skip']
            echo 'print commit_hash_ci_skip'
            println commit_hash_ci_skip
        
            def baseDir = pwd()
            echo "baseDir is: ${baseDir}"
            checkout()
            commitHash = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
            def image_version = "${projectVersion}" + '-' + "${commitHash}"
            echo image_version
            echo 'break1'
            def values_file_location = "${baseDir}/${source_deployment_folder}/values.yaml"
            def values_file_yaml = readYaml file: "${values_file_location}"
            values_file_yaml['eis_flowmanager']['server']['deployment']['image'] = 'hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/eis-flowmanager/flowmanager_server'
            values_file_yaml['eis_flowmanager']['ui']['deployment']['image'] = 'hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/eis-flowmanager/flowmanager_ui'
            values_file_yaml['eis_flowmanager']['curator']['job']['image'] = 'hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/eis-flowmanager/flowmanager_curator'
            values_file_yaml['eis_flowmanager']['server']['deployment']['imageTag'] = image_version
            values_file_yaml['eis_flowmanager']['ui']['deployment']['imageTag'] = image_version
            values_file_yaml['eis_flowmanager']['curator']['job']['imageTag'] = image_version
            sh "rm -rf ${values_file_location}"
            writeYaml file: values_file_location, data: values_file_yaml
            sh "cat ${values_file_location}"

        println '///////////////helm based deployment starts here//////////////////'

        stage ('Helm deployment'){
			helmInstall{
				manifestFolderLocation = manifest_folder_location
				helmChartLocation = config.source_deployment_folder
				checkoutBranch = git_branch_name
				delay_duration = sleepTime
			}
		}

        stage ('Helm Component Testing'){
	    	helmTest{
	    		checkoutBranch = git_branch_name
	    		testScriptLocation = config.test_script_location
	    		projectTest = project_name
	    	}
	    }

        list_of_manifest_files = sh returnStdout: true, script: """ls ${manifest_folder_location}/*-manifest.yaml"""
			stage ('Update ISO manifest'){
				list_of_manifest_files.split('\n').each {
				    if (config.mupltiple_iso_existence){
                        if (!(config.multiple_iso) == 'yes'){
		                	manifest_file_loc = it
		                }
				    	else{
				    		manifest_file_loc = it
				    		isoGroupName = it.tokenize("/")[2].replaceAll("-manifest.yaml","")
				    	}
	                }
                    else{
		                manifest_file_loc = it
                    }
                    echo "manifest file path is ${manifest_file_loc}"
				    echo "isogroup name is ${isoGroupName}"
                    updateIsoManifestYaml {
                        commitHashCiSkip = commit_hash_ci_skip
                        sourceDeploymentFolder= config.source_deployment_folder
                        eesDeployCheckoutBranch = 'master'
                        group_name_iso = isoGroupName
                        repoName = project_name
                        gitSwarmUrlSsh = remoteURL
                        commit_hash = codeCommitHash
                        manifestFileLoc = manifest_file_loc

                    }
			    }
			} 

        } //parent node ends here
    }
    catch(err){
        echo 'Failed to deploy service EIS :'+ err
        throw err
    } 
}

def checkout() {
    timestamps {
        step([$class: 'WsCleanup'])
        echo "check out======GIT =========== on ${env.gitlabBranch}"
        checkout scm
    }
}