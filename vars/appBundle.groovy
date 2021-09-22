import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    //** (step-0a) Define variables globally */
    def artifactory_path; def bundle_chart_version; def bundle_chart_name; def git_branch_name//;def app_bundle_file_loc

    //** (step-0b) Get git details for Helm Hub from repo name */
    def generalDisplayUtils = new org.utils.generalDisplayUtils() //** General display utility Object for log organization **
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    def current_date = new Date().format( 'ddMMyyyy' )
    def build_number = env.BUILD_NUMBER
    boolean changeInApp, registrationChartFoundInChartYaml = false
    def artif_obj = new org.utils.artifact()
    def catalogue_publish_url = ""
    def bundle_chart_yaml_loc
    def new_helm_versions = [:]
    Boolean version_incr_reqd = false
    def scm_obj = new org.utils.scm() //** Scm object to make git calls  **
     def shellObj = new org.utils.shell()
    //** (step-0c) Add values for git branch name and artifactory path with conditions */
    if (env.gitlabBranch == null )  {
        git_branch_name = "${params.sourceBranch}"
    } else {
        git_branch_name = "${env.gitlabBranch}"
    }
    println "git_branch_name " + git_branch_name

    if(!config.appBundleArtifactoryPath || config.appBundleArtifactoryPath == '' || config.appBundleArtifactoryPath == null) {
        artifactory_path = "${configproject}"
    } else {
        artifactory_path = "${config.appBundleArtifactoryPath}"
    }
    def folder_structure = "umbrella-helm-charts/${git_branch_name}/${artifactory_path}"
    def modality = config.modality.toLowerCase()
    def enableWorkspaceCleanup = config.enableWorkspaceCleanup?:'true'
    
    //** (step-0c) Get git details for App Bundle from repo name */
    def app_bundle_dir            = 'app_bundle'
    def app_bundle_templates_path = "${app_bundle_dir}/" + "${config.appBundleTemplatesPath}"
    def app_bundle_profiles_path  = "${app_bundle_dir}/" + "${config.appBundleProfilesPath}"
    def baseDir
    def bundle_chart_yaml_content = [:]

    //** (step-0d) Get git details for Helm Hub from repo name */
    def helm_hub_repo = config.helmHubGroupAndRepo
    def helm_hub_branch = git_branch_name
    def git_url_helm_hub = format_git_url(helm_hub_repo)
    def helm_hub_dir = 'helm_hub'
    def helm_hub_chart_yaml_path = "${helm_hub_dir}/" + config.helmHubChartYamlPath + '/Chart.yaml'
    def bundle_tag = "${env.BUILD_ID}_${current_date}"
    def app_bundle_name

    //** (step-0d) Variables to be fetche from settingsYaml dictionary */
    def settingsYamlDict = generalStageUtils.settingsYamlDict(git_branch_name,modality)
    def credID = generalStageUtils.getArtifactoryCredentialAPIKey(settingsYamlDict['artifactory_url'], settingsYamlDict, 'api_key')
    def credentialsId = settingsYamlDict['gitlab_cred_id']
    def changedAppRegistrationNames = [:]
    //def credID = 'BlrArtifactoryApiKey' //settings_yaml
    println settingsYamlDict['artifactory_url']
    def artifactory_url = settingsYamlDict['artifactory_url']
    def artifactory_repo = config.helm_repo ?: "helm-ees-all"
    def build_node = config.buildNode?:settingsYamlDict['buildNode']?:'CL_BuildNode_01_old'
    def arch = config.arch ?: "${artifactory_url}/docker-snapshot-eis-all/devops/helm-kubectl:1.0.0"
    def contArgs = """-u 0:0 -v /dockerspace:/dockerspace:rw -v /tmp:/tmp -v /var/spool/jenkins:/var/spool/jenkins:ro --net=host --entrypoint='' """
    def app
    def commitHash; def gitSwarmUrl_ssh
    def app_bundle_to_chart_name = [:]
    //def contArgs = """-u 0:0 -v ${baseDir}:/root:rw -v ${config_file_location}:/root/.kube/config --net=host --entrypoint=''"""
    //def arch = "${artifactory_location}/${env.docker_snapshot_repo}/helm/helm:3.1.0"

    try{
        node(build_node) {
            timestamps {
                //--------------------------------------------------------------------//
                //******************** [STEP-1] Repo Clone *************************** //
                //--------------------------------------------------------------------//
                stage ('Code Checkout') {
                    //** (step-1a) Clone App bundle git repo * /
                    step([$class: 'WsCleanup'])
                    generalDisplayUtils.sectionDisplay("(step-1a) ${app_bundle_dir} repo clone Starts", "h3")
                    stageIntegCodeCheckout{
                        scmObj = scm_obj
                        checkout_folder = app_bundle_dir
                        enableWorkspaceCleanup
                    }
                    dir(app_bundle_dir){
                        commitHash = shellObj.shell_ret_output("git rev-parse --short HEAD")
                        gitSwarmUrl_ssh = shellObj.shell_ret_output("git config remote.origin.url")
                    }
                    generalDisplayUtils.sectionDisplay("(step-1a) ${app_bundle_dir} repo clone ends", "h3")

                    //** (step-1b) Clone helm hub git repo * /
                    generalDisplayUtils.sectionDisplay("(step-1b) ${helm_hub_repo} repo clone Starts", "h3")
                    codeCheckoutInDir(helm_hub_dir,git_url_helm_hub,helm_hub_branch,credentialsId)
                    generalDisplayUtils.sectionDisplay("(step-1b) ${helm_hub_repo} repo clone ends", "h3")
                }

                //--------------------------------------------------------------------//
                //******************** [STEP-2] App Bundle Yaml Update *************** //
                //--------------------------------------------------------------------//
                stage('AppBundle Registration Yaml Update'){
                    generalDisplayUtils.sectionDisplay("(step-3) App Bundle Registration Yaml Update [starts]", "h2")
                     //** (step-2a) Chart yaml read * /
                    generalDisplayUtils.sectionDisplay("(step-2a) Read Chart Yaml starts", "h3")
                    def chart_yaml_content = [:]
                    chart_yaml_content = readYaml file: "${helm_hub_chart_yaml_path}"
                    generalDisplayUtils.sectionDisplay("(step-2a) Read Chart Yaml ends", "h3")

                    // ** (step-2b) App bundle template read * /
                    generalDisplayUtils.sectionDisplay("(step-2b) App bundle template read and overwrite starts", "h3")
                    def list_of_template_files = sh returnStdout: true,script: """ls ${app_bundle_templates_path}/*.yaml"""
                    println("list_of_template_files:")
                    println(list_of_template_files)
                    Boolean version_to_be_updated = false
                    list_of_template_files.split('\n').each {
                        app_bundle_file_loc = it
//                        println "app_bundle_file_loc ${app_bundle_file_loc}"
                        def array_of_helm_chart_names = []
                        println("app_bundle_content filename: " + app_bundle_file_loc)
                        app_bundle_content = readYaml file: "${app_bundle_file_loc}"
                        version_to_be_updated = false
                        for(i=0;i<app_bundle_content["spec"]["apps"].size();i++) {
                            app = app_bundle_content["spec"]["apps"][i]
                            array_of_helm_chart_names.add("${app['chart']}")
                            chart_yaml_content['dependencies'].each{ chart ->
                                if(app.containsValue(chart['name'])){
                                    changeInApp = true
                                    if (app['version'] == chart['version']){
                                        changeInApp = false
                                    }else{
                                        app['version'] = chart['version']
                                        sh "rm -rf ${app_bundle_file_loc}"
                                        writeYaml file: "${app_bundle_file_loc}", data: app_bundle_content
                                        //As per ehl request update of profiles and tempaltes versions is disabled - Aug 18 21
                                        //version_to_be_updated = true
                                        //version_incr_reqd = true
                                    }
                                    registrationChartFoundInChartYaml = true
                                }
                            }
                            if (!registrationChartFoundInChartYaml){
                                error("Chart ${app['chart']} not found in chart.yaml, aborting")
                            }
                            app_bundle_name = app_bundle_file_loc.split("/")[-1].split("\\.")[0]
                            app_bundle_to_chart_name << ["${app_bundle_name}":array_of_helm_chart_names]
                        }
                        if(version_to_be_updated) {
                            app_bundle_content = readYaml file: "${app_bundle_file_loc}"
                            println("original content : " + app_bundle_content)
                            chart_version = app_bundle_content['metadata']['name']
                            println("chart_version : " + chart_version)
                            String new_chart_name
                            new_chart_name = increment_version(chart_version)
                            new_helm_versions[chart_version] = new_chart_name
                            println ("new_chart_name "  + new_chart_name)
                            app_bundle_content['metadata']['name'] = new_chart_name
                            println("new content : " + app_bundle_content)
                            sh "rm -rf ${app_bundle_file_loc}"
                            writeYaml file: "${app_bundle_file_loc}", data: app_bundle_content
                            println("write successful")
                        }
                    }       
                    println("new_helm_versions")
                    println(new_helm_versions)
                    /*** Update Versions in "requires" sections ***/                    
                    
                    /***** update of requires section no more required for now  [Begin]
                    list_of_template_files = sh returnStdout: true,script: """ls ${app_bundle_templates_path}/*.yaml"""
                    println("list_of_template_files:")
                    println(list_of_template_files)
                    list_of_template_files.split('\n').each {
                        app_bundle_file_loc = it
                        println("app_bundle_content filename: " + app_bundle_file_loc)
                        app_bundle_content = readYaml file: "${app_bundle_file_loc}"
                        if(app_bundle_content["spec"]["requires"]) {
                            for(i=0;i<app_bundle_content["spec"]["requires"].size();i++) {
                                req = app_bundle_content["spec"]["requires"][i]
                                if(new_helm_versions[req]) {
                                    println (" found !!")
                                    app_bundle_content["spec"]["requires"][i] = new_helm_versions[req]
                                }
                            }
                            sh "rm -rf ${app_bundle_file_loc}"
                            writeYaml file: "${app_bundle_file_loc}", data: app_bundle_content
                        }
                    }
                    ***** update of requires section no more required for now  [End] */

                    /*** Update Versions in profiles ***/
                    list_of_template_files = sh returnStdout: true,script: """ls ${app_bundle_profiles_path}/*.yaml"""
                    println("list_of_profile_files:")
                    println(list_of_template_files)
                    list_of_template_files.split('\n').each {
                        app_bundle_file_loc = it
                        println("profile filename: " + app_bundle_file_loc)
                        app_bundle_content = readYaml file: "${app_bundle_file_loc}"
                        version_to_be_updated = false
                        if(app_bundle_content["spec"]["bundledefs"]) {
                            for(i=0;i<app_bundle_content["spec"]["bundledefs"].size();i++) {
                                req = app_bundle_content["spec"]["bundledefs"][i]
                                println req
                                println req.getClass()
                                String key_req
                                if (req.getClass() == java.util.LinkedHashMap)
                                {
                                    req.each {key, val -> 
                                        key_req = key.toString()
                                    }
                                // key_req = req.replaceAll(":","")
                                }
                                else{
                                    key_req = req.toString()
                                }
                                if(new_helm_versions[key_req]) {
                                    println (" found1 !!")
                                    //println("old value : "+req)
                                    //println("old value trimmed : " + key_req)
                    //              app_bundle_content["spec"]["bundledefs"][i] = new_helm_versions[req]
                                    if(req.getClass() == java.util.LinkedHashMap) {
                                        println("hashmap updated")
                                        req[new_helm_versions[key_req]] = req[key_req]
                                        req.remove(key_req)                                        
                                    } else {
                                        println("string updated")
                                        app_bundle_content["spec"]["bundledefs"][i] = app_bundle_content["spec"]["bundledefs"][i].replaceAll(key_req, new_helm_versions[key_req])
                                    }
                                    //println(" new value " + app_bundle_content["spec"]["bundledefs"][i] )
                                    //As per ehl request update of profiles and tempaltes versions is disabled - Aug 18 21
                                    //version_to_be_updated = true
                                }
                            }

/********************
                            for(i=0;i<app_bundle_content["spec"]["bundledefs"].size();i++) {
                                req = app_bundle_content["spec"]["bundledefs"][i]
                                println req
                                println req.getClass()
                                String key_req
                                if (req.getClass() == java.util.LinkedHashMap)
                                {
                                    req.each {key, val -> 
                                        key_req = key.toString()
                                    }
                                    println ("keyset : ")
                                    println "key check"
                                    println key_req   
                                // key_req = req.replaceAll(":","")
                                }
                                else{
                                    key_req = req.toString()
                                }
                                if(new_helm_versions[key_req]) {
                                    println (" found1 !!")
                                    //println("old value : "+req)
                                    println("old value trimmed : " + key_req)
//                                    app_bundle_content["spec"]["bundledefs"][i] = new_helm_versions[req]
                                    app_bundle_content["spec"]["bundledefs"][i] = app_bundle_content["spec"]["bundledefs"][i].replaceAll(key_req, new_helm_versions[key_req])
                                    println(" new value " + app_bundle_content["spec"]["bundledefs"][i] )
                                    version_to_be_updated = true
                                }
                            }
********/                            
                            if(version_to_be_updated) {
                                println("original content : " + app_bundle_content)
                                chart_version = app_bundle_content['metadata']['name']
                                println("profile_version : " + chart_version)
                                app_bundle_content['metadata']['name'] = increment_version(chart_version)
                                println("profile_version2 : " + app_bundle_content['metadata']['name'])
                                sh "rm -rf ${app_bundle_file_loc}"
                                writeYaml file: "${app_bundle_file_loc}", data: app_bundle_content
                            }
                        }
                    }

                    generalDisplayUtils.sectionDisplay("(step-2b) App bundle template read and overwrite ends", "h3")
                    generalDisplayUtils.sectionDisplay("(step-3) App Bundle Registration Yaml Update [ends]", "h2")
                    dir(app_bundle_dir ){
                        sh "git add templates"
                        gitPush {
                            commit_hash_map_file = 'commithash.yaml'
                            commitMessage = "[Pipeline]: Updated templates file"
                            gitPushBranch =  "${git_branch_name}"
                        }
                    }
                }
                println "%%%%%%%%%%%%%%%%%Finding the version to be incremented for Manifest and Chart.yaml%%%%%%%%%%%%%%%%%%%%%%%"
                bundle_chart_yaml_loc = "${env.WORKSPACE}/${app_bundle_dir}/Chart.yaml"
                bundle_chart_yaml_content = readYaml file: bundle_chart_yaml_loc
                bundle_chart_version = bundle_chart_yaml_content['version']
                //As per EHL request we are updating chart.yml version fro every builds
                version_incr_reqd = true
                if(version_incr_reqd) {
                    def branch_name = git_branch_name.replaceAll("/","-")
                    //new_ver = increment_version(bundle_chart_yaml_content['version'])
                    new_ver = bundle_chart_yaml_content['version'].split("-")[0]
                    println("newVersion:"+new_ver)
                    version_components = new_ver.split("\\.")
                    println(version_components)
                   // patch_version = version_components[-1].toInteger()
                   // patch_version = patch_version + 1
                   // version_components[-1] = patch_version.toString()
                    version_components[-1] = version_components[-1].toInteger() + 1
                    println(version_components)
                    new_ver = version_components.join(".")
                    if(branch_name.contains("release")){
                        bundle_chart_version = new_ver
                    }else{
                        bundle_chart_version = "${new_ver}-${branch_name}-${build_number}"
                    }
                }
                bundle_chart_name = bundle_chart_yaml_content['name']
                println "%%%%%%%%%%%%%%%%% DONE %%%%%%%%%%%%%%%%%%%%%%%"
                if(app_bundle_to_chart_name.isEmpty()){
                    println '************************************************'
                    println '************************************************'
                    println '************************************************'
                    println 'NO CHANGES FOUND TO BE BUILD'
                    println '************************************************'
                    println '************************************************'
                    println '************************************************'
                }
                if(!app_bundle_to_chart_name.isEmpty()){
                    stage('AppBundle Manifest Yaml Update'){
                        if(config.disableManifest == 'true') {
                            Utils.markStageSkippedForConditional("${env.STAGE_NAME}")
                        } else {
                            generalDisplayUtils.sectionDisplay("(step-4) App Bundle Manifest Yaml Update [Starts]", "h2")
                            bundle_chart_yaml_loc = "${env.WORKSPACE}/${app_bundle_dir}/Chart.yaml"
                            catalogue_publish_url = "https://${artifactory_url}/artifactory/generic-eis-all/app_bundle_catalogues/ehs_app_bundle_catalogue-${bundle_chart_version}.yaml"
                            appBundleManifestUpdate{
                                manifest_yaml_dir = config.manifestYamlDir
                                helmHubDir = "${helm_hub_dir}"
                                helmHubChartYamlPath = helm_hub_chart_yaml_path
                                catalogue_yaml_dir = configCatalogueYamlDir
                                appbundle_manifest_dir = config.appBundleManifestDir
                                app_bundle_name = config.appBundleName
                                appBundleBranch = git_branch_name
                                chartAndBundleNamesDict = app_bundle_to_chart_name
                                publish_url = catalogue_publish_url
                                artifactory_cred = credID
                            }
                            generalDisplayUtils.sectionDisplay("(step-4) App Bundle Yaml Manifest Update [ends]", "h2")
                        }
                    }
                    //--------------------------------------------------------------------//
                    //******************** [STEP-3] Helm Package of App bundle ***********//
                    //--------------------------------------------------------------------//

                    stage('Helm Package of App bundle'){
                        bundle_chart_yaml_content['version'] ="${bundle_chart_version}"
                        sh "rm -rf ${bundle_chart_yaml_loc}"
                        writeYaml file: "${bundle_chart_yaml_loc}", data: bundle_chart_yaml_content
                        if(version_incr_reqd) {
                            dir(app_bundle_dir ){
                                sh "git add ${env.WORKSPACE}/${app_bundle_dir}/Chart.yaml"
                                gitPush {
                                    commit_hash_map_file = 'commithash.yaml'
                                    commitMessage = "[Pipeline]: Updated templates file"
                                    gitPushBranch =  "${git_branch_name}"
                                }
                            }                            
                        }

                        // ** (step-3a) cd to ehs-app-bundle-registration and helm package * /
                        generalDisplayUtils.sectionDisplay("(step-3a) cd to ehs-app-bundle-registration and helm package starts", "h3")
                        baseDir = pwd()
                        withDockerContainer(args: "$contArgs", image: "$arch"){
                            sh"""
                                cd ${baseDir}/${app_bundle_dir}/
                                helm package .
                            """
                        }
                        generalDisplayUtils.sectionDisplay("(step-3a) cd to ehs-app-bundle-registration and helm package ends", "h3")
                    }

                    //--------------------------------------------------------------------//
                    //******************** [STEP-4] Publish App Bundle Helm Package ******//
                    //--------------------------------------------------------------------//
                    stage('Publish App Bundle Helm Package'){
                        // ** (step-4a) Push app bundle helm package (.tgz) to artifactory
                        generalDisplayUtils.sectionDisplay("(step-4a) Push app bundle helm package (.tgz) to artifactory starts", "h3")
                        def chartProperties = "origin_branch=${git_branch_name}\\;git_commit_id=${commitHash}\\;git_url=${gitSwarmUrl_ssh}"
                        def dest_location = "https://${artifactory_url}/artifactory/${artifactory_repo}/${folder_structure}/${bundle_chart_name}-${bundle_chart_version}.tgz"
                        withCredentials([string(credentialsId: credID, variable: 'ApiToken')]) {
                            def cmd = """curl -sw \"%{http_code}\"  --insecure --silent -H \'X-JFrog-Art-Api: ${ApiToken}\' -X PUT ${dest_location}\\;${chartProperties}  -T "${baseDir}/${app_bundle_dir}/${bundle_chart_name}-${bundle_chart_version}.tgz" """
                            def ret = sh(script:cmd, returnStdout: true).trim()
                            println("DEGUNG PRINT: ret = "+ret)
                            def ret_code = ret.find(/[0-9]+$/).toInteger()

                            if(ret_code >= 400) {
                                    println("PIPELINE_ERROR App bundle publish failed")
                                    throw new Exception("Publish failed")
                            }
                        }
                        //update the manifest file with chart's location
                        dir ('devops-deploy') {
                            git branch: git_branch_name, changelog: false, credentialsId: "${credentialsId}", poll: false, url: 'git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/devops-deploy.git'
                            def artifact_yaml_file_loc = "ehs_2.x_artifacts/ehs_apps_pkg_manifest.yaml"
                            def artifact_yaml_content = readYaml file: artifact_yaml_file_loc
                            artifact_yaml_content["AppBundles"][0]["ehs"]["pkg_registration_helm_charts"] = dest_location
                            artifact_yaml_content["AppBundles"][0]["ehs"]["pkg_catalogue_yaml_file"] = catalogue_publish_url
                            sh "rm -rf ${artifact_yaml_file_loc}"
                            writeYaml file: "${artifact_yaml_file_loc}", data: artifact_yaml_content
                            sh "git add ${artifact_yaml_file_loc}"
                            println "final artifact file: " 
                            //sh "cat ${artifact_yaml_file_loc}"
                            retry(5){
                                gitPush {
                                    commit_hash_map_file = 'Devops_Deploy_commit_hash'
                                    commitMessage = "[ci-skip] Version update for Umbrella Helm chart"
                                    gitPushBranch =  "${git_branch_name}"
                                }
                            }
                        }
                        generalDisplayUtils.sectionDisplay("(step-4a) Push app bundle helm package (.tgz) to artifactory ends", "h3")
                    }
                }   
            }
        }
    }catch(err){
        currentBuild.result = 'FAILURE'
        emailExtraMsg = "Build Failure:"+ err.getMessage()
        throw err
    }//catch
    finally{
        println "Finally Block"
    }
}

def format_git_url(repo_name){
    git_url = "git" + "@gitlab-gxp.cloud.health.ge.com:" + repo_name + ".git"
    //Example: git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/catalyst/integration_test_automation.git
    return git_url
}

def codeCheckoutInDir(dirName,repo,branch,credentialsId) {
    dir ("${dirName}") {
        git branch: "${branch}", changelog: false, poll: false, url: "${repo}", credentialsId: "${credentialsId}"
    }
}

def increment_version(chart_name) {
    sem_ver = ''
    new_ver = ''
    version_components = chart_name.split("-")
    for(i=0;i<version_components.size();i++){
        if(version_components[i] =~ /[0-9]+\.[0-9]+\.[0-9]/) {
            sem_ver = version_components[i]
            versions = sem_ver.split("\\.")
            println(versions)
            versions[-1]  = versions[-1].toInteger() + 1
            println(versions)
            new_ver = versions.join(".")
            version_components[i] = new_ver
        }
    }
    chart_name = version_components.join("-")
    println("CHARTNAME: "+chart_name)
    return(chart_name)
}
