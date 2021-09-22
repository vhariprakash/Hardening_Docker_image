def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    println("appBundlePackaging [Start]")
    def appBundle_Name; def imageKeys; def firstKeyForImage; def remainingkeysForImage; def tagKeys; def firstKeyForTag; def remainingkeysForTag; def sourceTagValue
    def registrationYamlData = [:]
    def manifestYamlData = [:]
    def new_helm_docker_map = [:]
    def modality = config.modalityName
    def scm_obj = new org.utils.scm() //** Scm object to make git calls  **
    def artifactObj = new org.utils.artifact()
    def credentialsId = config.gitlab_cred_id
    def deployRepo = config.deployRepo
    def chartName = config.chart_name
    def chartVersion = config.chart_version
    println "chart_name " + chartName + chartVersion
    def helm_chart_overwrite
    def arch = config.yq_arch
    println("yq-arch = "+arch)
    def baseDir = pwd()
    def contArgs = "-v ${baseDir}:${baseDir}:rw --entrypoint yq"
    def imageReferenceList = config.image_reference_list
    def values_parsed_data = [:]
    def dockerImagesArray = []
    def bundle_dest_location
    def manifest_dest_location
    def build_number = env.BUILD_NUMBER
    def applicationChartLocation = config.application_chart_location
    def appBundleChartLocation = "${env.WORKSPACE}/app-bundle-git-repo/${config.app_bundle_chart_folder}"
    Boolean changeInApp, tagExistsInSeparateLine
    def artifactory_repo = "helm-ees-all"
    println("HELM REPO::" + artifactory_repo)
    dir("app-bundle-git-repo"){
        def artifactory_url = config.artifact_url
        def url = "https://${artifactory_url}/artifactory/${artifactory_repo}"
        helm_chart_overwrite =  "${url}/${chartName}/${chartName}-${chartVersion}.tgz"
        helm_url = "https://${artifactory_url}/artifactory/api/storage/${artifactory_repo}/${chartName}/${chartName}-${chartVersion}.tgz"

        git branch: config.deployRepoBranch, changelog: false, poll: false, url: deployRepo, credentialsId: credentialsId
        def appBundleRegistrationYamlPath = config.registrationYamlPath
        def appBundleManifestYamlPath = "${env.WORKSPACE}/app-bundle-git-repo/${config.appBundleManifestPath}"
 //       def appBundleManifestYamlName = appBundleManifestYamlPath.split("/")[-1]
        registrationYamlData = readYaml file: appBundleRegistrationYamlPath
        manifestYamlData = readYaml file: appBundleManifestYamlPath
        println("appBundleManifestYamlPath Content before:")
        println(manifestYamlData.toString())
        appBundle_Name = appBundleRegistrationYamlPath.split("/")[-1].split("\\.")[0]
        //appBundle_Name = registrationYamlData["metadata"]["name"]
        //def existing_images = manifestYamlData['AppBundles'][appBundle_Name]["docker_images"]
        //println "existing_images " + existing_images + existing_images.getClass()
        println "appBundle_Name " + appBundle_Name

        //find docker images
        imageReferenceList.each{
            tagExistsInSeparateLine = false
            def exists = fileExists "${env.WORKSPACE}/${applicationChartLocation}/values.yaml"
            if(exists) {
                println("values.yaml present")
                values_parsed_data = readYaml file: ("${env.WORKSPACE}/${applicationChartLocation}/values.yaml")
            } else {
                println("additional_values.yaml does not exist, considering values.yaml")
                values_parsed_data = readYaml file: ("${env.WORKSPACE}/${applicationChartLocation}/values.yaml")
            }
            if(exists) {
                println("non-aca")
                if (it.contains(":")){
                    tagExistsInSeparateLine = true
                    imageKeys = it.split(":")[0]
                    firstKeyForImage = imageKeys.split("\\.")[0]
                    remainingkeysForImage = imageKeys-firstKeyForImage
                    tagKeys = it.split(":")[-1]
                    firstKeyForTag = tagKeys.split("\\.")[0]
                    remainingkeysForTag = tagKeys-firstKeyForTag
                    sourceTagValue = sh(returnStdout: true, script: """docker run ${contArgs} ${arch} eval '."${firstKeyForTag}"${remainingkeysForTag}' "${env.WORKSPACE}/${applicationChartLocation}/values.yaml" """).trim()
                }else{
                    firstKeyForImage = it.split("\\.")[0]
                    remainingkeysForImage = it-firstKeyForImage
                }
                //find the source image value
                sourceImageValue = sh(returnStdout: true, script: """docker run ${contArgs} ${arch} eval '."${firstKeyForImage}"${remainingkeysForImage}' "${env.WORKSPACE}/${applicationChartLocation}/values.yaml" """).trim()
                if(sourceImageValue.contains("docker.io") ||sourceImageValue.contains(".ge.com")){
                    sourceImageValue = sourceImageValue.split("/",2)[-1]
                }
                if (tagExistsInSeparateLine){
                    sourceFullImage = "${sourceImageValue}:${sourceTagValue}"
                }else{
                    sourceFullImage = "${sourceImageValue}"
                }
                dockerImagesArray.add(sourceFullImage)
            }
        }
        /*if(existing_images){
            dockerImagesArray= dockerImagesArray + existing_images
        }*/
        println "Docker images array .........." + dockerImagesArray
        def get_helm_chart_property = sh(returnStdout: true,script: """curl ${helm_url}""")
        sha256_value = getCheckSumFromJsonOutput(get_helm_chart_property, 'sha256')
        md5sum_value = getCheckSumFromJsonOutput(get_helm_chart_property, 'md5')
        helm_chart_info = ['artifact_url': "${helm_chart_overwrite}", 'sha256':"${sha256_value}", 'md5':"${md5sum_value}"]
        if(dockerImagesArray.size() > 0) {
            new_helm_docker_map["docker_images"] = dockerImagesArray
        }
        println "new_helm_docker_map = " + new_helm_docker_map
        //update chart version in templates file
        for(i=0;i<registrationYamlData["spec"]["apps"].size();i++) {
            app = registrationYamlData["spec"]["apps"][i]
            if(app.containsValue(chartName)){
                changeInApp = true
                println "*************************************************************"
                println "Service Chart Name: " + app['chart']
                println "Found Version: " + app['version']
                println "Replacing with: " + chartVersion
                if (app['version'] == chartVersion){
                    changeInApp = false
                }else{
                    app['version'] = chartVersion
                }
                print "*************************************************************"
            }
        }
        sh "rm -rf ${appBundleRegistrationYamlPath}"
        writeYaml file: "${appBundleRegistrationYamlPath}", data: registrationYamlData
        Boolean isHelmChartNamePresent = false
        def appBundleHelmMap
        def appBundleMap = manifestYamlData['AppBundles']

        println("appBundleMap.each loop")
        appBundleMap.each(){ appBundleName, appBundleValue ->
            def count = 0
            //Length of app bundle helm list
            def helmArrayIndex = appBundleMap[appBundleName]["helm_charts"].size()
            isHelmChartNamePresent = false
            println('appBundleMap[appBundleName][helm_charts].each loop')
            println(helmArrayIndex)
            appBundleMap[appBundleName]["helm_charts"].each{ appBundleHelmArray ->
                println(appBundleMap[appBundleName]["helm_charts"][count])
                println(appBundleMap[appBundleName]["helm_charts"][count]["artifact_url"])
                if(appBundleMap[appBundleName]["helm_charts"][count]["artifact_url"]?.contains(chartName)) {
                    appBundleMap[appBundleName]["helm_charts"][count] = helm_chart_info
                    isHelmChartNamePresent = true
                }
                count = count + 1
            }
            if(isHelmChartNamePresent == false) {
                appBundleMap[appBundleName]["helm_charts"].add(helm_chart_info)
            }
        }
        manifestYamlData['AppBundles'][appBundle_Name]["docker_images"] = dockerImagesArray

        //manifestYamlData = readYaml file: "${appBundleManifestYamlPath}_bkp"
        sh "rm -rf ${appBundleManifestYamlPath}"
        manifestYamlData['AppBundles'] = appBundleMap
        writeYaml file: "${appBundleManifestYamlPath}", data: manifestYamlData
        println("appBundleManifestYamlPath content after:")
        sh "cat ${appBundleManifestYamlPath}"

        //package the chart
        def appBundleChartFileLocation = "${appBundleChartLocation}/Chart.yaml"
        def bundle_chart_yaml_content = readYaml file: appBundleChartFileLocation
        def bundle_chart_name = bundle_chart_yaml_content['name']
        def semVer = bundle_chart_yaml_content['version'].split("-")[0]
        bundle_chart_yaml_content['version'] = "${semVer}-${build_number}"
        //bundle_chart_yaml_content['version'] = bundle_chart_yaml_content['version'].replaceAll(/-[0-9]*/,"-${build_number}")
        println("1.bundle_chart_yaml_content['version'] :"+bundle_chart_yaml_content['version'])
        sh "rm -rf ${appBundleChartFileLocation}"
        writeYaml file: "${appBundleChartFileLocation}", data: bundle_chart_yaml_content
        def image = config.arch ?: "${artifactory_url}/docker-snapshot-eis-all/devops/helm-kubectl:1.0.0"
        def imageArgs = """-u 0:0 -v ${env.WORKSPACE}:${env.WORKSPACE}:rw -v /var/spool/jenkins:/var/spool/jenkins:ro --net=host --entrypoint='' """
        withDockerContainer(args: "$imageArgs", image: "$image"){
            sh"""
                cd ${appBundleChartLocation}
                helm package .
            """
        }

        //publish helm chart and manifest file
        def credID = config.artifactory_credID
        println("credID: "+credID)
        bundle_dest_location = "https://${artifactory_url}/artifactory/${artifactory_repo}/${appBundle_Name}/${bundle_chart_name}-${bundle_chart_yaml_content['version']}.tgz"
        println("bundle_dest_location: "+bundle_dest_location)
        publishFileToArtif(credID,bundle_dest_location,"${appBundleChartLocation}/${bundle_chart_name}-${bundle_chart_yaml_content['version']}.tgz")
        println("${chartVersion}")
//        appBundleManifestYamlName_with_version = appBundleManifestYamlName.replace('.yaml',"-${bundle_chart_yaml_content['version']}.yaml")
        println("appBundle_Name: "+appBundle_Name)
        println("version: "+bundle_chart_yaml_content['version'])
        appBundleManifestYamlName_with_version = appBundle_Name+"-${bundle_chart_yaml_content['version']}.yaml"
        println("appBundleManifestYamlName_with_version : "+appBundleManifestYamlName_with_version)
        manifest_dest_location = "https://${artifactory_url}/artifactory/generic-eis-all/2-p-apps-manifests/${appBundle_Name}/${appBundleManifestYamlName_with_version}"
        println("manifest_dest_location: "+ manifest_dest_location)
        publishFileToArtif(credID,manifest_dest_location,appBundleManifestYamlPath)

        //update files in git
        sh """
            git add ${appBundleRegistrationYamlPath} ${appBundleManifestYamlPath} ${appBundleChartFileLocation}
        """
        gitPush{
            commit_hash_map_file = 'isoCatalogueCommit.yaml'
            commitMessage = "[ci-skip] updated app bundle files"
            gitPushBranch = "${config.deployRepoBranch}"
        }
    }

    // update 2p_apps_pkg_manifest.yaml file in devops-deploy
    def fileToBeUpdated
    dir("devops-deploy"){
        git branch: config.deployRepoBranch, changelog: false, poll: false, url: deployRepo, credentialsId: credentialsId
        fileToBeUpdated = "ehs_2.x_artifacts/2p_apps_pkg_manifest.yaml"
        def fileData = readYaml file: "${fileToBeUpdated}"
        Boolean appBundleAvailable = false
        fileData["AppBundles"].each{appBundleHelmArray ->
            appBundleHelmArray.each{ key, value->
                if(key == "${appBundle_Name}"){
                    appBundleAvailable = true
                    value["pkg_registration_helm_charts"] = bundle_dest_location
                    value["pkg_catalogue_yaml_file"] = manifest_dest_location
                }
            }
        }
        if(!appBundleAvailable){
            def newMap = ["${appBundle_Name}":["pkg_registration_helm_charts":"${bundle_dest_location}","pkg_catalogue_yaml_file":"${manifest_dest_location}"]]
            fileData["AppBundles"].add(newMap)
        }
        sh "rm -rf ${fileToBeUpdated}"
        writeYaml file: fileToBeUpdated, data: fileData
        sh """
            cat ${fileToBeUpdated}
            git add ${fileToBeUpdated}
        """
        gitPush{
            commit_hash_map_file = 'isoCatalogueCommit.yaml'
            commitMessage = "[ci-skip] updated parameters for 2-p apps in 2p_apps_pkg_manifest.yaml"
            gitPushBranch = "${config.deployRepoBranch}"
        }
        
    }
    println("appBundlePackaging [End]")
}
def publishFileToArtif(credID,destLocation,sourceLocation){
    withCredentials([string(credentialsId: credID, variable: 'ApiToken')]) {
        def cmd = """curl -sw \"%{http_code}\"  --insecure --silent -H \'X-JFrog-Art-Api: ${ApiToken}\' -X PUT ${destLocation}  -T "${sourceLocation}" ; echo "Exit code: \$?" """
        def ret = sh(script:cmd, returnStdout: true).trim()
        println("DEGUNG PRINT: ret = "+ret)
        def ret_code = ret.find(/[0-9]+$/).toInteger()
        if(ret_code >= 400) {
                println("PIPELINE_ERROR App bundle publish failed")
                throw new Exception("Publish file to artifactory function failed")
        }
    }
}

@NonCPS
def getJsonOutput(get_helm_property,helm_chart_name){
    println("getJsonOutput [Start]")
    def images_from_property = new groovy.json.JsonSlurper().parseText(get_helm_property)
    def docker_images_temp; def docker_images_output = []
    if(!images_from_property['properties']["noDockerImages"]){
        if (images_from_property['properties']['docker_images']){
            docker_images_temp = images_from_property['properties']['docker_images']
            def temp = docker_images_temp[0].trim().split(',')
            temp.each{
                docker_images_output.add(it)
            }
        }
    }
    else if(images_from_property['properties']["noDockerImages"] == "true"){
        docker_images_output = ""
    }
    else{
        error("Sorry, we did not find docker images in the helm properties for ${helm_chart_name}")
    }
    println("getJsonOutput [End]")
    return docker_images_output
}

def getCheckSumFromJsonOutput(get_helm_property,checksum_type){
    def json = new groovy.json.JsonSlurper().parseText(get_helm_property)
    println("json")
    println(json)
    println(checksum_type)
    try {
        return json["checksums"][checksum_type]
    } catch(err) {
        return ""
    }
}
