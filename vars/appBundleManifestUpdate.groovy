import groovy.json.JsonSlurper
def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    println("appBundleManifestUpdate [Start]")
    //** ############################# (II) Step-0 Variables fetch [START] ############################################
    //App Bundle Manifest variables
    def manifestYamlDir = config.manifest_yaml_dir ?: ''
    //Artifactory URL
    def artifactory_url = config.artifactoryUrl?:"${env.active_artifactory}".split('//')[1]
    def appbundle_manifest_dir
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    def app_bundle_to_chart_name = config.chartAndBundleNamesDict
    println "app_bundle_to_chart_name " + app_bundle_to_chart_name + " " + app_bundle_to_chart_name.getClass()
    if(!artifactory_url){
        throw new Exception("[PIPELINE Error]: artifactory_url is either not available or value is $artifactory_url")
    }
    //Helm Chart location from Chart.yaml helm hub repo and docker images
    def chart_yaml_content = [:]; def helm_chart = [:]; def helm_chart_overwrite
    def docker_image_list = []
    def url; def repo_name; def get_helm_property; def helm_chart_name; def isHelmChartNamePresent
    def chart_location = "${config.helmHubChartYamlPath}"
    //App bundle manifest variables
    def image_list = [:]; def new_helm_docker_map = [:]
    def appBundleManifestDir = config.appbundle_manifest_dir ?:''
    if (appBundleManifestDir){
        appbundle_manifest_dir =  "${env.WORKSPACE}/${config.helmHubDir}/${appBundleManifestDir}"
    }else{
        appbundle_manifest_dir =  "${env.WORKSPACE}/${config.helmHubDir}"
    }
    sh " ls ${appbundle_manifest_dir}"
    def appbundle_manifest_file = "AppBundle-Manifest.yaml"
    def appbundle_manifest_file_location = "${appbundle_manifest_dir}/${appbundle_manifest_file}"
    println "appbundle_manifest_file_location ${appbundle_manifest_file_location}"
    //** (II) Step: Read app bundle manifest yaml
    def appBundleManifestContent = readYaml file: (appbundle_manifest_file_location)
    def appBundleMap = [:]  // appBundleManifestContent['AppBundles']
    //Read Chart yaml
    chart_yaml_content = readYaml file: "${chart_location}"
    //Get all charts complete url as a list

    def chart_checksums = [:]
    def chart_docker_images = [:]
    for(chart in chart_yaml_content['dependencies']){
        //URL and Repo Parsing
        url = chart['repository'].split('/artifactory')[0];
        repo_name = chart['repository'].split('/artifactory')[1]
        helm_chart_name = chart['name']
        helm_chart_overwrite = "${url}/artifactory/${repo_name}/${chart['name']}/${helm_chart_name}-${chart['version']}.tgz"
//        helm_chart_overwrite = "${url}/${chart['name']}/${helm_chart_name}-${chart['version']}.tgz"
        helm_url = "${url}/artifactory/api/storage/${repo_name}/${chart['name']}/${chart['name']}-${chart['version']}.tgz"
        get_helm_property = sh(returnStdout: true,script: """curl ${helm_url}?properties""")
        if(get_helm_property) {
            docker_images = getJsonOutput(get_helm_property,"${helm_chart_name}")
        } else {
            error("ERROR: unable to get helm property for ${helm_url}" )
        }
        def get_helm_chart_property = sh(returnStdout: true,script: """curl ${helm_url}""")
        sha256_value = getCheckSumFromJsonOutput(get_helm_chart_property, 'sha256')
        md5sum_value = getCheckSumFromJsonOutput(get_helm_chart_property, 'md5')
        new_helm_docker_map = ['artifact_url': "${helm_chart_overwrite}", 'sha256':"${sha256_value}", 'md5':"${md5sum_value}"]
        chart_checksums[helm_chart_name] = new_helm_docker_map
        chart_docker_images[helm_chart_name] = docker_images
    }
    println("CheckSum details : ")
    println(chart_checksums)
    println("Docker Images: ")
    println(chart_docker_images)
    app_bundle_to_chart_name.each { newAppBundle, newHelmCharts ->
        appBundleMap[newAppBundle] = ["helm_charts" : []]
        println("Adding new appBundle "+newAppBundle.toString())
        newHelmCharts.each {
            helm_chart_name = it
            println("AppBundle:" + newAppBundle)
            println("helm_chart_name: "+helm_chart_name)
            println(chart_checksums[helm_chart_name])
            appBundleMap[newAppBundle]["helm_charts"].add(chart_checksums[helm_chart_name])
            println("Docker Images for this chart:")
            println(chart_docker_images[helm_chart_name])
            chart_docker_images[helm_chart_name].each {
                image = it
                if(!appBundleMap[newAppBundle]["docker_images"]) {
                    appBundleMap[newAppBundle]["docker_images"] = []
                }
                if(appBundleMap[newAppBundle]["docker_images"].contains(image) == false) {
                    appBundleMap[newAppBundle]["docker_images"].add(image)
                }

            }
        }
    }
    println("-------------------------------------------------------------")
    println(appBundleMap)

    //** Step: Update in app bundle manifest file
    sh "rm -rf ${appbundle_manifest_file_location}"
    appBundleManifestContent['AppBundles'] = appBundleMap
    writeYaml file: appbundle_manifest_file_location, data: appBundleManifestContent
    println("Publishing manifest file to : "+config.publish_url)
    withCredentials([string(credentialsId: config.artifactory_cred, variable: 'ApiToken')]) {
        def cmd = """curl -sw \"%{http_code}\"  --insecure --silent -H \'X-JFrog-Art-Api: ${ApiToken}\' -X PUT ${config.publish_url}  -T ${appbundle_manifest_file_location}  """
        def ret = sh(script:cmd, returnStdout: true).trim()
        println("DEGUNG PRINT: ret = "+ret)
        def ret_code = ret.find(/[0-9]+$/).toInteger()
        if(ret_code >= 400) {
            println("PIPELINE_ERROR Manifest publish failed")
            throw new Exception("Publish failed")
        }
    }

    //** Step: git add, commit and push

    sh """
        cd ${appbundle_manifest_dir}
        git add ${appbundle_manifest_file_location}
    """
    def ret = false
    dir("${appbundle_manifest_dir}"){
        retry(5) {
            if (ret) {
                sleep time: 30, unit: 'SECONDS'
            } else {
                ret = true
            }
            println "final manifest file:"
            sh "cat ${appbundle_manifest_file_location}"
            gitPush {
                commit_hash_map_file = 'EHL_HELM_HUB_commit_hash'
                commitMessage = "[ci-skip] App Bundle Manifest file update for App Bundle"
                gitPushBranch =  "${config.appBundleBranch}"
            }
        }
    }
    println "App Bundle Manifest successfully pushed to Git"
    println("appBundleManifestUpdate [End]")
}

def codeCheckoutInDir(dirName,repo,branch,credentialsId) {
    dir ("${dirName}") {
        git branch: "${branch}", changelog: false, poll: false, url: "${repo}", credentialsId: "${credentialsId}"
    }
}

@NonCPS
def getJsonOutput(get_helm_property,helm_chart_name){
    println("getJsonOutput [Start]")
    def docker_images_output = []
    try {
        def images_from_property = new groovy.json.JsonSlurper().parseText(get_helm_property)
        def docker_images_temp; 
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
    } catch (err) {
        println("PIPELINE_ERROR : getJsonOutput : "+ err.getMessage())
        println("unable to get docker images from : "+ get_helm_property)
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
