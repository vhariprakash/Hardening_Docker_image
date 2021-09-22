def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    println ("appISOPackageHelm [start]")
    try{
        def checkout_branch = 'master'
        def hostip_dev_env = "${env.hostip_dev_env_helm}"
        def dev_token = "${env.dev_token_helm}"
        def nexus_url_for_deployment = "${env.nexus_location_in_box}"
        def group = "${config.group_name}"
        def artifactory_url_for_deployment = "hc-eu-west-aws-artifactory.cloud.health.ge.com"
        def logs = currentBuild.rawBuild.getLog(10000)
        def crdJenkinsfileCheck = logs.find { it.contains('@script to read Jenkinsfile-crd') }
        def manifest_iso_loc = ''
        def app_iso_location_helm= "${config.appISOLocationHelm}" + '/'
        //def iso_manifest_absolute_location = "iso-manifests/${group}.yaml"
        def iso_manifest_absolute_location = sh(returnStdout: true, script: """ find . -name ${group}.yaml """).trim()
        def iso_manifest_file_parse = (!iso_manifest_absolute_location?.trim()) ? error("ISO group name is null or empty"): readYaml (file:iso_manifest_absolute_location)
        if (iso_manifest_file_parse.keySet()) {
            iso_manifest_file_parse.keySet().each {
                dir(it){
                    echo '*************************************'
                    echo "Service Name: ${it}"
                    echo '*************************************'
                    sh "rm -rf ${it}"
                    def repo_to_clone = iso_manifest_file_parse[it]['repo_url']
                    def repo_commit_hash = iso_manifest_file_parse[it]['commit_id']
                    def helm_relative_location = iso_manifest_file_parse[it]['helm_chart_location']
                    def manifest_file = iso_manifest_file_parse[it]['manifest_file_location']
                    def additional_values_commit_hash = iso_manifest_file_parse[it]['commit_id_additional_values']
                    def additional_folder_location = ''
                    if(!crdJenkinsfileCheck){
                        dir(additional_values_commit_hash){
                            checkout ( [$class: 'GitSCM', branches: [[name: "${additional_values_commit_hash}" ]], userRemoteConfigs: [[
                                credentialsId: 'ssh-Jenkins-s02', url: "${repo_to_clone}"
                            ]]])
                            additional_folder_location = pwd()
                            def additional_values_file_loc = "${helm_relative_location}/additional_values.yaml"
                            def additional_values_file_read = readFile("${additional_values_file_loc}").replaceAll(artifactory_url_for_deployment,nexus_url_for_deployment)
                            writeFile file: "${additional_values_file_loc}", text: additional_values_file_read
                            def additional_values_file_parse = readYaml file: additional_values_file_loc
                        }
                    }
                    else{
                        additional_folder_location = pwd()
                    }

                    dir(repo_commit_hash){
                        sleep 15
                        checkout ( [$class: 'GitSCM', branches: [[name: "${repo_commit_hash}" ]],
                            userRemoteConfigs: [[credentialsId: 'ssh-Jenkins-s02', url: "${repo_to_clone}"]]]
                        )
                        def manifest_file_copy_destination = manifest_file.replaceAll(manifest_file.split('/')[-1],'')
                        echo "manifest_file_copy_destination: ${manifest_file_copy_destination}"
                        if(!crdJenkinsfileCheck){
                            sh """
                            rm -rf ${helm_relative_location}/Chart.yaml
                            rm -rf ${helm_relative_location}/additional_values.yaml
                            rm -rf ${manifest_file}
                            chmod +r ${additional_folder_location}/${helm_relative_location}/Chart.yaml
                            chmod +r ${additional_folder_location}/${helm_relative_location}/additional_values.yaml
                            cp ${additional_folder_location}/${helm_relative_location}/Chart.yaml ${helm_relative_location}
                            cp ${additional_folder_location}/${helm_relative_location}/additional_values.yaml ${helm_relative_location}
                            cp ${additional_folder_location}/${manifest_file} ${manifest_file_copy_destination}
                            echo 'wait for sometime to finish copy operation'
                            sleep 5
                        """
                        }

                        sh """
                            rm -rf ${helm_relative_location}/Chart.yaml
                            rm -rf ${manifest_file}
                            chmod +r ${additional_folder_location}/${helm_relative_location}/Chart.yaml
                            cp ${additional_folder_location}/${helm_relative_location}/Chart.yaml ${helm_relative_location}
                            cp ${additional_folder_location}/${manifest_file} ${manifest_file_copy_destination}
                            echo 'wait for sometime to finish copy operation'
                            sleep 5
                        """
                        echo 'repo_commit_hash'
                        echo pwd()
                    }
                    def mountPoint = pwd()
                    dir ('helm-docker') {
                        git branch: "${checkout_branch}", changelog: false, credentialsId: 'ssh-Jenkins-s02', poll: false, url: 'git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/helm-docker.git'
                    }
                    def config_file_location = "${mountPoint}" + '/helm-docker/config'
                    def configFileContent = readFile(config_file_location).replaceAll('deployment_server_ip', hostip_dev_env).replaceAll('deployment_server_token',dev_token)
                    writeFile file: config_file_location, text: configFileContent
                    def contArgs = """-u 0:0 -v /dockerspace:/dockerspace:rw -v ${config_file_location}:/root/.kube/config --net=host"""
                    def arch = 'hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/helm-centos:2.11.1'
                    withDockerContainer(args: "${contArgs}", image: "${arch}") {
                        def current_date = new Date().format( 'yyyyMMdd' )
                        sh """
                            cd ${repo_commit_hash}/${helm_relative_location}
                            helm package .
                        """
                    }
                    sh """
                        cd ${repo_commit_hash}/${helm_relative_location}
                        ls
                        cp *.tgz ${app_iso_location_helm}
                    """
                }
            }
            echo 'confirm'
            sh "ls ${app_iso_location_helm}"
        }
        else {
            echo "No application was avaliable inside the iso manifest or the ${group}.yaml ISO manifest was not a valid YAML"
        }
    }
    catch(err){
        println("PIPELINE_ERROR appISOPackageHelm : " + err.getMessage())
        throw err
    }
    println ("appISOPackageHelm [end]")
}
