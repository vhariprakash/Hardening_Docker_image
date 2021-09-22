def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def gitUrl = "${config.gitSwarmUrl}"
    echo gitUrl
    def hostip_bdd = "${config.hostip_dev_env_helm}"
    def build_number = "${env.BUILD_NUMBER}".toString()
    def project_test = "${config.projectTest}"
    def bdd_publisher_credentials = config.bddPublisherCredentials
    def bdd_publisher_node = config.bddPublisherNode

    println "CompTesting:config.component_testing_base_dir_structure: ${config.component_testing_base_dir_structure}"
    println "CompTesting:config.component_testing_build_dir_structure: ${config.component_testing_build_dir_structure}"

    def componentTestingBaseDirStructure = config.component_testing_base_dir_structure
    def componentTestingBuildDirStructure = config.component_testing_build_dir_structure
    println "Debug lines: componentTestingBuildDirStructure " + componentTestingBuildDirStructure
    println "Debug lines: componentTestingBaseDirStructure " + componentTestingBaseDirStructure
    //def podTemplateCredId = config.pod_template_cred_id
    def pod_template_cred_id = config.podTemplateCredId
    println "[bddTesting] before buildCommand ${config.build_command}"
    //def resourceyaml = "./resource.yml"
    def cloud_name = config.cloudName
    echo 'project comp test'
    echo "${project_test}"
    def resourceData = []

    def tar_destination = config.tarDestination

    def build_command = "${config.build_command}" ?: "mvn clean install"
    println "[bddTesting] after buildCommand ${build_command}"
    project_build_t = "${config.build_type}"
    project_build_type = "${config.build_type}"
    if(project_build_type == 'maven') {
        if(config.build_command) {
            build_command = config.build_command
        } else {
            build_command = "mvn clean install"
        }
//        build_command = "${config.build_command}" ?: "mvn clean install"
    }
    else {
        if(config.build_command) {
            build_command = config.build_command
        } else {
            build_command = "gradle clean build --stacktrace --info"
        }
//        build_command = "${config.build_command}" ?: "gradle clean build --stacktrace --info"
    }

println "[bddTesting] after buildCommand ${build_command}"

 echo " Starting Component Testing with yaml file "
    def slave_label = "bdd-runner-${build_number}"
    sh "ls -ltrh"
    //sh "cat deploy.json"
    sh "whoami"
    sh "pwd"
    echo "cloud_name ${cloud_name}"

    def fileContents = readFile 'deploy.yml'
    echo fileContents
   timeout(80) {

           podTemplate(label: slave_label, cloud: "${cloud_name}", yaml : "${fileContents}" ) {
             node(slave_label) {
             echo "Slave is alive"
            //kubectl top nodes
             git branch: "${config.git_branch}", credentialsId: "${pod_template_cred_id}", poll: false, url:"${gitUrl}"


            container('bdd-runner-with-dataset') {
                echo "${hostip_bdd}"
                echo 'project_build_type'
                echo "${project_build_type}"
                base_dir = pwd()
                echo base_dir
                tar_file_name ="${build_number}"+".tar.gz"
                component_test_dir = "${base_dir}"+"/${componentTestingBaseDirStructure}"
                tar_file_location = "${base_dir}"+"/${componentTestingBaseDirStructure}/"+"${tar_file_name}"
                build_directory = "${base_dir}/${componentTestingBuildDirStructure}/*"
                withCredentials([usernamePassword(credentialsId: bdd_publisher_credentials, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

                    if(project_build_type=='gradle') {
					  if (fileExists("${base_dir}/${componentTestingBaseDirStructure}/build.gradle")) {
                         sh """
                            echo " trying cd to ${base_dir}/${componentTestingBaseDirStructure}"
                            cd "${base_dir}/${componentTestingBaseDirStructure}"
                            echo " trying ${build_command} execution"
                            ${build_command} || echo "Component test execution failed"
                         """
                         sh """
                             if [[ -d ${base_dir}/${componentTestingBaseDirStructure}/build && -d ${base_dir}/${componentTestingBaseDirStructure}/target ]]
                            then
                                cd "${base_dir}/${componentTestingBaseDirStructure}" && tar -cvf "${tar_file_location}" build/ target/
                            else
                                echo "PIPELINE_ERROR : ${base_dir}/${componentTestingBaseDirStructure}/target and/or ${base_dir}/${componentTestingBaseDirStructure}/build folder does not exist"
                                exit 1
                            fi
                         """
					  }
                      else {
					     ansiColor('xterm') {
                             println "Skipping execution of component BDD tests for now as they are not available. Please add the tests at the earliest"

                        }
					  }
                    }
                    else if(project_build_type=='maven') {
                        sh """
                            cd "${base_dir}/${componentTestingBaseDirStructure}"
                            ${build_command} || echo "Component test execution failed"
                        """
                        //mvn clean install || echo "Component test execution failed"
                        sh """
                            if [[ -d ${base_dir}/${componentTestingBaseDirStructure}/target ]]
                            then
                                cd "${base_dir}/${componentTestingBaseDirStructure}" && tar -cvf "${tar_file_location}" target/
                            else
                                echo "PIPELINE_ERROR : ${base_dir}/${componentTestingBaseDirStructure}/target folder does not exist"
                                exit 1
                            fi

                        """
                    }
                    else {
                        echo 'Select appropriate project type'
                    }
					if (fileExists("${base_dir}/${componentTestingBaseDirStructure}")) {
                    dir("${component_test_dir}") {
                        sh """
                            sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${USERNAME}"@"${bdd_publisher_node}" mkdir -p ${tar_destination}
                            sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${build_number}".tar.gz "${USERNAME}"@"${bdd_publisher_node}":"${tar_destination}"
                        """
                    }
                    script {
                        def logs = currentBuild.rawBuild.getLog(10000)
                        def result = logs.find { it.contains('Component test execution failed') }
                        if (result) {
                            error ('Failing due to ' + result)
                        }
                    }
                }
			  }
            }
        }
     }
   }
}
