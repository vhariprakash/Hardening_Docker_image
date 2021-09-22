package org.utils

def maven_build(arch, contArgs, pre_step, post_step, mvnbuildDetails, buildVersion, build_command, branchType, git_branch_name, currentTimestamp, defaultPomVersion) {
    println ('maven_build execution [start]')
    try {
        if (arch?.trim()) {
            withDockerContainer(args: "$contArgs", image: "$arch") {
                maven_build_execution(pre_step, post_step, mvnbuildDetails, buildVersion, build_command, branchType, git_branch_name, currentTimestamp, defaultPomVersion)
            }//EO withContainer
        }else {
            maven_build_execution(pre_step, post_step, mvnbuildDetails, buildVersion, build_command, branchType, git_branch_name, currentTimestamp, defaultPomVersion)
        }//EO if-else (arch)
    }catch (err) {
        println 'PIPELINE_ERROR maven_build :  ' + err.getMessage()
        throw err
    }
    println ('maven_build execution [end]')
}

def gradle_build(arch, contArgs, buildCommand, enableDebug, pre_step, post_step) {
    println ("gradle_build [start]")
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    println("buildCommand : " + buildCommand)
    println("enableDebug  : " + enableDebug.toString())
    try {
        if(arch?.trim()) {
            withDockerContainer(args: "$contArgs", image: "$arch") {
                jaas_sensor_step{
                    name= "Build"
                    settings= hcddSettings
                    jaas_step={
                        ret_code = generalStageUtils.exec_hook(pre_step)
                        if(ret_code != 0) {
                            error("gradle_build: prestep failed")
                        }
                        if(buildCommand.trim() == '') {
                            println("buildCommand is null, hence taking default command")
                            buildCommand = 'build '
                        }
                        if (enableDebug) {
                            println("enable debug")
                            buildCommand = buildCommand + " --debug"
                        }
                        sh """
                            export https_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                            export http_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                            export no_proxy=".ge.com"
                            env
                            if [ -f /root/.bashrc ];
                            then
                                source  /root/.bashrc
                            fi
                            echo "Build Command : ${buildCommand}"
                            gradle --no-daemon ${buildCommand}
                        """
                        ret_code = generalStageUtils.exec_hook(post_step)
                        if(ret_code != 0) {
                            error("gradle_build: post_step failed")
                        }
                    }
                }
            }
        } else {
            jaas_sensor_step{
                name= "Build"
                settings= hcddSettings
                jaas_step={
                    ret_code = generalStageUtils.exec_hook(pre_step)
                    if(ret_code != 0) {
                        error("gradle_build: prestep failed")
                    }
                    if(buildCommand.trim() == '') {
                        println("buildCommand is null, hence taking default command")
                        buildCommand = 'build '
                    }
                    if (enableDebug) {
                        println("enable debug")
                        buildCommand = buildCommand + " --debug"
                    }
                    sh """
                        export https_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                        export http_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                        export no_proxy=".ge.com"
                        env
                        if [ -f /root/.bashrc ];
                        then
                            source  /root/.bashrc
                        fi
                        echo "Build Command : ${buildCommand}"
                        gradle --no-daemon ${buildCommand}
                    """
                    ret_code = generalStageUtils.exec_hook(post_step)
                    if(ret_code != 0) {
                        error("gradle_build: post_step failed")
                    }
                }
            }
        }
        println ("gradle_build [end]")
    } catch(err) {
        if(err.getMessage()) {
            def err_msg = err.getMessage()
            if(err_msg.contains("Failed to rm container")) {
                println "WARNING : *********** " + err.getMessage()
                println ("gradle_build [end]")
            } else {
                throw err
            }
        } else {
            println "PIPELINE_ERROR gradle_build :  " + err.getMessage()
            throw err
        }
    }
}
_
def go_build(arch, contArgs, buildCommand, workspace, pre_step, post_step) {
    println ("go_build [start]")
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    try {
        if(arch?.trim()) {
            withDockerContainer(args: "$contArgs", image: "$arch") {
                jaas_sensor_step{
                    settings= hcddSettings
                    jaas_step={
                        ret_code = generalStageUtils.exec_hook(pre_step)
                        if(ret_code != 0) {
                            error("go_build: prestep failed")
                        }
                        sh """
                            cd ${workspace}
                            export https_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                            export http_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                            export no_proxy=".ge.com"
                            chmod +x gradlew
                            env
                            java -version
                            go version
                            gradle -version
                            ./gradlew ${buildCommand}
                        """
    //                        #python -m unittest -v ${config.customUnittestFilename}
                        ret_code = generalStageUtils.exec_hook(post_step)
                        if(ret_code != 0) {
                            error("go_build: poststep failed")
                        }
                    }
                }
            }
        } else {
            jaas_sensor_step{
                settings= hcddSettings
                jaas_step={
                    ret_code = generalStageUtils.exec_hook(pre_step)
                    if(ret_code != 0) {
                        error("go_build: prestep failed")
                    }
                    sh """
                        cd ${workspace}
                        export https_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                        export http_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                        export no_proxy=".ge.com"
                        chmod +x gradlew
                        env
                        java -version
                        go version
                        gradle -version
                        ./gradlew ${buildCommand}
                    """
//                        #python -m unittest -v ${config.customUnittestFilename}
                    ret_code = generalStageUtils.exec_hook(post_step)
                    if(ret_code != 0) {
                        error("go_build: poststep failed")
                    }
                }
            }
        }
        println ("go_build [end]")
    } catch(err) {
        if(err.getMessage()) {
            def err_msg = err.getMessage()
            if(err_msg.contains("Failed to rm container")) {
                println "WARNING : *********** " + err.getMessage()
                println ("go_build [end]")
            } else {
                throw err
            }
        } else {
            println "PIPELINE_ERROR go_build : " + err.getMessage()
            throw err
        }
    }
}

def python_build(arch, contArgs, workspace, pre_step, post_step) {
    println ("python_build [start]")
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    try {
        if(arch?.trim()) {
            withDockerContainer(args: "$contArgs", image: "$arch") {
                jaas_sensor_step{
                    settings= hcddSettings
                    jaas_step={
                        ret_code = generalStageUtils.exec_hook(pre_step)
                        if(ret_code != 0) {
                            error("python_build: prestep failed")
                        }
                        sh """
                            cd ${workspace}
                            export https_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                            export http_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                            export no_proxy=".ge.com"
                            pip install -r requirements.txt
                            pip install -r test-requirements.txt
                            coverage run -m unittest discover
                            coverage xml -o coverage.xml
                        """
    //                        #python -m unittest -v ${config.customUnittestFilename}
                        ret_code = generalStageUtils.exec_hook(post_step)
                        if(ret_code != 0) {
                            error("python_build: poststep failed")
                        }
                    }
                }
            }
        } else {
            jaas_sensor_step{
                settings= hcddSettings
                jaas_step={
                    ret_code = generalStageUtils.exec_hook(pre_step)
                    if(ret_code != 0) {
                        error("python_build: prestep failed")
                    }
                    sh """
                        cd ${workspace}
                        export https_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                        export http_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80
                        export no_proxy=".ge.com"
                        pip install -r requirements.txt
                        pip install -r test-requirements.txt
                        coverage run -m unittest discover
                        coverage xml -o coverage.xml
                    """
//                        #python -m unittest -v ${config.customUnittestFilename}
                    ret_code = generalStageUtils.exec_hook(post_step)
                    if(ret_code != 0) {
                        error("python_build: poststep failed")
                    }
                }
            }
        }
        println ("python_build [end]")
    } catch(err) {
        if(err.getMessage()) {
            def err_msg = err.getMessage()
            if(err_msg.contains("Failed to rm container")) {
                println "WARNING : *********** " + err.getMessage()
                println ("python_build [end]")
            } else {
                throw err
            }
        } else {
            println "PIPELINE_ERROR python_build : " + err.getMessage()
            throw err
        }
    }
}

def cpp_build(arch, contArgs, buildCommand, pre_step, post_step) {
    println ("cpp_build [start]")
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    if(buildCommand.trim() == '' || buildCommand == null || !buildCommand) {
        buildCommand = 'make '
    }
    try {
        if(arch?.trim()) {
            withDockerContainer(args: "$contArgs", image: "$arch") {
                ret_code = generalStageUtils.exec_hook(pre_step)
                if(ret_code != 0) {
                    error("cpp_build: prestep failed")
                }
                sh """
                    ${buildCommand}
                """
                ret_code = generalStageUtils.exec_hook(post_step)
                if(ret_code != 0) {
                    error("cpp_build: poststep failed")
                }
            }
        } else {
            ret_code = generalStageUtils.exec_hook(pre_step)
            if(ret_code != 0) {
                error("cpp_build: prestep failed")
            }
            sh """
                ${buildCommand}
            """
            ret_code = generalStageUtils.exec_hook(post_step)
            if(ret_code != 0) {
                error("cpp_build: poststep failed")
            }
        }
    } catch(err) {
        if(err.getMessage()) {
            def err_msg = err.getMessage()
            if(err_msg.contains("Failed to rm container")) {
                println "WARNING : *********** " + err.getMessage()
                println ("cpp_build [end]")
            } else {
                throw err
            }
        } else {
            println "PIPELINE_ERROR python_build : " + err.getMessage()
            throw err
        }
    }
    println ("cpp_build [end]")
}

def shell_build(arch, contArgs, buildCommand, pre_step, post_step) {
    println ("shell_build [start]")
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    if(buildCommand?.trim() == '') {
        buildCommand = 'echo "Nothing to build"'
    }
    echo "arch = "+arch.toString()
    echo "buildCommand : "+buildCommand
    try {
        if(arch?.trim()) {
            withDockerContainer(args: "$contArgs", image: "$arch") {
                ret_code = generalStageUtils.exec_hook(pre_step)
                if(ret_code != 0) {
                    error("shell_build: prestep failed")
                }
                sh """
                    echo "*********************** start build ***********************"
                    env
                    ${buildCommand}
                    echo "***********************  end build  ***********************"
                """
                ret_code = generalStageUtils.exec_hook(post_step)
                if(ret_code != 0) {
                    error("shell_build: poststep failed")
                }
            }
        } else {
            ret_code = generalStageUtils.exec_hook(pre_step)
            if(ret_code != 0) {
                error("shell_build: prestep failed")
            }
            sh """
                ${buildCommand}
            """
            ret_code = generalStageUtils.exec_hook(post_step)
            if(ret_code != 0) {
                error("shell_build: poststep failed")
            }
        }
    } catch(err) {
        if(err.getMessage()) {
            def err_msg = err.getMessage()
            if(err_msg.contains("Failed to rm container")) {
                println "WARNING : *********** " + err.getMessage()
                println ("shell_build [end]")
            } else {
                throw err
            }
        } else {
            println "PIPELINE_ERROR shell_build : " + err.getMessage()
            throw err
        }
    }
    println ("shell_build [end]")
}

def docker_build(docker_build_publish_arch,contArgs,dockerFiles,artifactory_url,docker_repo,version,project,list_of_images, commitHash, build_version,pre_step,post_step){
    println ("docker_build [start]")
    withDockerContainer(args:"$contArgs", image: "$docker_build_publish_arch"){
        try {
            def generalStageUtils = new org.utils.generalStageUtils()
            ret_code = generalStageUtils.exec_hook(pre_step)
            if(ret_code != 0) {
                error("docker_build_prestep failed")
            }
            println ("dockerFiles Value from inside docker_build > " + dockerFiles)
            if (dockerFiles == null){
                image_name = "${artifactory_url}"+"/"+"${docker_repo}"+"/"+"${project}"+"/"+"${project}"
                image_tag = build_version
                list_of_images.add("${image_name}:${image_tag}")
                sh """
                    echo "docker  build -t ${image_name}:${image_tag} --label \"git_commit=${commitHash}\" ."
                    docker build -t ${image_name}:${image_tag} --label "git_commit=${commitHash}" .
                """
            }else if (dockerFiles != '' || dockerFiles != null){
                for (item in dockerFiles){
                    println("Keys > " + item.keySet())
                    println("values > " + item.values())
                    println("Dockerfile > " + item.get("image_file"))  //getting the dockerfile from the key docker_file_loc
                    println("Build Env file > " + item.get("image_env")) //build env file if any                     

                    if(item.("image_name") && item.("image_name").trim()) {
                        image_name = item.("image_name").trim()
                        image_name = "${artifactory_url}"+"/"+"${docker_repo}"+"/"+"${project}"+"/"+"${item.image_name.trim()}"
                    } else {
                        error("image_name missing")  // image_name is mandatory
                    }
                    if(item.get("image_tag") && item.get("image_tag").trim()) {
                        image_tag = build_version.replaceAll("${version}", item.get("image_tag").trim())   // replacing the default version with the tag provided in dockerFiles
                    } else {
                        image_tag = build_version
                    }
                    if(item.get("image_file") && item.get("image_file").trim()) {
                        image_file = item.("image_file").trim()
                        println(" Relative path of dockerfile > " + image_file.take(image_file.lastIndexOf('/')))
                        abs_file_path = "${workspace}/${image_file.take(image_file.lastIndexOf('/'))}"
                    } else {
                        image_file = "Dockerfile"
                        abs_file_path = "${workspace}"
                    }
                    if(item.get("image_arg") && item.get("image_arg").trim()) {
                        image_arg = " --build-arg " + item.get("image_arg").trim().replaceAll(",", " --build-arg ")
                    } else {
                        image_arg = ""
                    }
                    if(item.get("image_env") && item.get("image_env").trim()){
                        image_env = item.get("image_env")
                    } else {
                        image_env = null
                    }
                    list_of_images.add("${image_name}:${image_tag}")     
                    sh """
                        cd ${abs_file_path}
                        if [ -f "${workspace}/${image_env}" ]
                        then
                            set -o allexport
                            source ${workspace}/${image_env}
                            set +o allexport
                        else
                            echo "no build env to execute"
                        fi
                        docker build -t ${image_name}:${image_tag} ${image_arg} --label "git_commit=${commitHash}" .
                    """
                }
            }
            ret_code = generalStageUtils.exec_hook(post_step)
            if(ret_code != 0) {
                error("docker_build_poststep failed")
            }
        } catch(err) {
            println("PIPELINE_ERROR docker_build : "+err.getMessage())
            throw err
        }
        println ("docker_build [end]")
    }
}

def npm_build(arch, contArgs, buildCommand, pre_step, post_step) {
    println ("npm_build [start]")
    try {
        if(arch?.trim()) {
            withDockerContainer(args: "$contArgs", image: "$arch") {
                npm_build_execution(buildCommand, pre_step, post_step)
            }
        }else{
            npm_build_execution(buildCommand, pre_step, post_step)
        }
        println ("npm_build [end]")
    } catch(err) {
        if(err.getMessage()) {
            def err_msg = err.getMessage()
            if(err_msg.contains("Failed to rm container")) {
                println "WARNING : *********** " + err.getMessage()
                println ("npm_build [end]")
            } else {
                throw err
            }
        } else {
            println "PIPELINE_ERROR npm_build :  " + err.getMessage()
            throw err
        }
    }
}

def npm_build_execution(buildCommand, pre_step, post_step){
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    ret_code = generalStageUtils.exec_hook(pre_step)
    if(ret_code != 0) {
        error("npm_build: prestep failed")
    }
    println("buildCommand : " + buildCommand)
    if(buildCommand && buildCommand.trim() == '') {
        println("buildCommand is null, hence taking default command")
        buildCommand = 'build'
    }
    buildCommand = buildCommand.replaceAll(",", " && npm ")
             
    sh """
        echo "Build command : ${buildCommand}"
        npm ${buildCommand}
      """
    ret_code = generalStageUtils.exec_hook(post_step)
    if(ret_code != 0) {
        error("npm_build: post_step failed")
    }
}


//--------------------------------------Publish methods--------------------------------------------------------
def maven_publish(arch, contArgs, publish_repo, commitHash, artifactoryUrl, credentialsId, project, publishFilePattern, publishExcludePattern, mvnBuildDetails,prestep,workspace) {
    println ('maven_publish [start]')
    println 'publish_repo : ' + publish_repo

    try {
        if (arch != null && arch != '' ) {
            withDockerContainer(args: "$contArgs", image: "$arch") {
                maven_publish_execution(publish_repo, commitHash, artifactoryUrl, credentialsId, project, publishFilePattern, publishExcludePattern, mvnBuildDetails,prestep, workspace)
            }//Eo withDockerContainer
        }else {
            maven_publish_execution(publish_repo, commitHash, artifactoryUrl, credentialsId, project, publishFilePattern, publishExcludePattern, mvnBuildDetails,prestep, workspace)
        }
        println ('maven_publish [end]')
    } catch (err) {
        if (err.getMessage()) {
            def err_msg = err.getMessage()
            if(err_msg.contains("Failed to rm container")) {
                println "WARNING : *********** " + err.getMessage()
                println ("maven_publish [end]")
            } else {
                throw err
            }
        } else {
            println("PIPELINE_ERROR maven_publish : "+err.getMessage())
            throw err
        }
    }
}

def gradle_publish(arch, contArgs, artifactoryUrl, deployer_location, resolver_location, rtGradleVersion, credentialsId, gradleCustomCommand, commitHash) {
    def server;
    println ("gradle_publish [start]")
    println "deployer_location : " + deployer_location
    println "resolver_location : " + resolver_location
    println("artifactoryUrl: "+artifactoryUrl)
    println("credentialsId: "+credentialsId)
    println("rtGradleVersion: "+rtGradleVersion)
    println("gradleCustomCommand: "+gradleCustomCommand)
    try {
        if(arch != '') {
            println("gradle publish inside docker container")
            withDockerContainer(args: "$contArgs", image: "$arch") {
                server = Artifactory.newServer url:"https://${artifactoryUrl}/artifactory" ,  credentialsId:"${credentialsId}"
                server.setBypassProxy(true)
                def rtGradle = Artifactory.newGradleBuild()
                rtGradle.deployer server: server, repo: deployer_location
                rtGradle.resolver server: server, repo: resolver_location
                rtGradle.tool = rtGradleVersion
                rtGradle.usesPlugin = true
                rtGradle.deployer.deployIvyDescriptors = true
                rtGradle.deployer.deployMavenDescriptors = true
                rtGradle.deployer.addProperty("sourceCodeCommitId", "${commitHash}")
                buildInfo = rtGradle.run rootDir: './', buildFile: 'build.gradle', tasks: "artifactoryPublish", switches: '--no-daemon'
//                buildInfo = rtGradle.run rootDir: './', buildFile: 'build.gradle', tasks: "build --stacktrace artifactoryPublish".toString(), switches: '--no-daemon'
                server.publishBuildInfo buildInfo
            }
        } else {
            println("docker publish outside docker container")
 //       withDockerContainer(args: "$contArgs", image: "$arch") {
            server = Artifactory.newServer url:"https://${artifactoryUrl}/artifactory" ,  credentialsId:"${credentialsId}"
            server.setBypassProxy(true)
            def rtGradle = Artifactory.newGradleBuild()
            rtGradle.deployer server: server, repo: deployer_location
            rtGradle.resolver server: server, repo: resolver_location
            rtGradle.tool = rtGradleVersion
            rtGradle.usesPlugin = true
            rtGradle.deployer.deployIvyDescriptors = true
            rtGradle.deployer.deployMavenDescriptors = true
            rtGradle.deployer.addProperty("sourceCodeCommitId", "${commitHash}")
            buildInfo = rtGradle.run rootDir: './', buildFile: 'build.gradle', tasks: "artifactoryPublish", switches: '--no-daemon'
            server.publishBuildInfo buildInfo
//        }
        }
    } catch (err) {
        if(err.getMessage()) {
            def err_msg = err.getMessage()
            if(err_msg.contains("Failed to rm container")) {
                println "WARNING : *********** " + err.getMessage()
                println ("gradle_publish [end]")
            } else {
                throw err
            }
        } else {
            println("PIPELINE_ERROR gradle_publish : "+err.getMessage())
            throw err
        }
    }
    println ("gradle_publish [end]")
}


def generic_publish (artifactory_url, artifactory_repos, publish_type, artifactory_path, dynamic_path, follow_source_path, file_path, file_pattern, credentialsId, commitHash)
{
    println ("generic_publish [start]")
    def generalStageUtils = new org.utils.generalStageUtils()
    def fileList = get_file_list(file_path, file_pattern)
    def dest_location, source_file, src_filename, src_filename_key
    def artifactory_repo
    println("Files:")
    println(fileList)
    println(artifactory_path)
    println(dynamic_path)
    println(follow_source_path)
    def publish_list = []
    def generalGroovyUtils = new org.utils.generalGroovyUtils()
    def cmd
    def ret
    def ret_code
    def new_artifactory_path = artifactory_path
    def add_properties = "\\;sourceCodeCommitId=${commitHash}"
    String uri, md5, sha256
    dir(file_path) {
        fileList.split("\n").each {
            if(it) {
                try {
                    source_file = it
                    println("source_file: "+source_file)
                    if(follow_source_path?.trim()) {
                        File file = new File(source_file)
                        remove_string = follow_source_path?.endsWith('/') ? follow_source_path : follow_source_path+'/'
                        new_path = file.parent.replace(remove_string, '')
                        new_artifactory_path = artifactory_path+"/"+ new_path
                    } else if(dynamic_path?.trim()) {
                        dynamic_path_output = generalStageUtils.exec_command("${dynamic_path}")
                        new_artifactory_path = artifactory_path+"/"+dynamic_path_output
                    } else {
                        println("using static path for publish...")
                    }
                    src_filename = source_file.split('/')[-1]
                    src_filename_key = src_filename.replaceAll('-[0-9].*', '')
                    println("Built Artifact Source Location: " + source_file)
                    if(publish_type == 'yum') {
                        artifactory_repo = artifactory_repos["yum_repo"]
                    } else if(publish_type == 'pypi')  {
                        artifactory_repo = artifactory_repos["pypi_repo"]
                    } else if(publish_type == 'generic')  {
                        artifactory_repo = artifactory_repos["generic_repo"]
                    }
                    dest_location = "\'https://${artifactory_url}/artifactory/${artifactory_repo}/${new_artifactory_path}/${src_filename}\'"
                    dest_property_update="\'https://${artifactory_url}/artifactory/api/storage/${artifactory_repo}/${new_artifactory_path}/${src_filename}\'"
                    withCredentials([string(credentialsId: credentialsId, variable: 'ApiToken')]) {
                        cmd = "curl -sw \"%{http_code}\" --noproxy  --insecure --silent -H \'X-JFrog-Art-Api: ${ApiToken}\' -X PUT ${dest_location}${add_properties} -T \'${source_file}\'"
                        println("CMD:"+cmd)
                        ret = sh(script:"${cmd}", returnStdout: true).trim()
                        ret_code = ret.find(/[0-9]+$/)
                        if(ret_code.toInteger() < 400) {
                        /*    json = generalGroovyUtils.getDictFromJson(ret)
                            uri =json["uri"]
                            md5 = json["checksums"]["md5"]
                            sha256 = json["checksums"]["sha256"]
                        */
                            uri = generalGroovyUtils.getUriFromJson(ret)
                            md5 = generalGroovyUtils.getMd5SumFromJson(ret)
                            sha256 = generalGroovyUtils.getSha256FromJson(ret)
                            publish_list.add(['name': src_filename_key, 'artifact_url' : uri, 'md5sum' : md5, 'sha256': sha256 ])

                            /* cmd = "curl -sw \"%{http_code}\" --insecure -H \"X-JFrog-Art-Api:${ApiToken}\" -X PUT ${dest_property_update}?properties=sourceCodeCommitId=${commitHash}"
                            ret = sh(script:cmd, returnStdout: true).trim()
                            ret_code = ret.find(/[0-9]+$/).toInteger()
                            if(ret_code >= 400) {
                                println("PIPELINE_ERROR generic_publish : Publish failed, with ret code: "+ret_code)
                                throw new Exception("Publish failed")
                            } */
                        } else {
                            println("PIPELINE_ERROR generic_publish : Publish failed with error code "+ret_code)
                            throw new Exception("Publish failed")
                        }
                    }
                } catch(err) {
                    println("PIPELINE_ERROR generic_publish : "+ err.getMessage())
                    throw err
                }
            }
        }
    }
    println(publish_list.toString())
    println ("generic_publish [end]")
    return (publish_list)
}

def setJavaHome(){
    string containerJavaHome = sh(script:'echo $JAVA_HOME', returnStdout: true).trim()
    echo " Container Java Home: ${containerJavaHome}"
    env.JAVA_HOME = "${containerJavaHome}"
    def baseDir = sh(script: 'pwd', returnStdout: true)
    echo "************${baseDir}*****************"
}

def get_file_list (filePath, files) {
    def shellObj = new org.utils.shell()
    def parent_folder
    file_list = ''
    println('get_file_list [start]')
    println("find files in " + filePath)
    println("files to be searched : "+files)
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
    if(file_list.trim() == '') {
        println("PIPELINE_ERROR get_file_list : file not found")
        throw new Exception("file not found")
    }
    println('get_file_list [end]')
    return file_list
}

def npm_publish(output_dir, artifactory_url, artifactory_repos,jenkinsArtifactoryInstance){
    def files = findFiles(glob: "${output_dir}/**/*.tgz")
    def publish_dir
    if ( files.size() > 0) {
        for (counter = 0; counter < files.size(); counter++) {
            def filePath = files[counter].path.toString()
            def fileName = files[counter].name.toString()
            def fileDir = filePath.minus(fileName).replaceAll('/','').trim()
            publish_dir = "${fileDir}"
            }
        }
    else{
        publish_dir = "${output_dir}"
    }
    /*
    sh """
        echo "Publish starting"
        cd ${publish_dir}
        npm publish --registry "https://${artifactory_url}/artifactory/api/npm/${artifactory_repos['npm_repo']}/"
    """
    */
    //Publish using plugin
    def server = Artifactory.server "${jenkinsArtifactoryInstance}".toString()
    def rtNpm = Artifactory.newNpmBuild()
    rtNpm.tool = 'nodejs'
    server.bypassProxy = true
    rtNpm.deployer server: server, repo: "${npm_snapshot_repository}".toString()
    def buildInfo = rtNpm.publish path: "${fileDir}".toString()
    server.publishBuildInfo buildInfo

}

def docker_publish(docker_build_publish_arch,contArgs,artifactory_url,list_of_images,credID,pre_step,post_step){
    println ("docker_publish [start]")
    withDockerContainer(args:"$contArgs", image: "$docker_build_publish_arch"){
        try {
            def generalStageUtils = new org.utils.generalStageUtils()
            ret_code = generalStageUtils.exec_hook(pre_step)
            if(ret_code != 0) {
                error("docker_publish_prestep failed")
            }
            def docker_info = []
            def sha256 = "", ret_text = ""
            try{
                println("list_of_images > " + list_of_images)
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credID , usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    sh """
                        docker login -u "$USERNAME" -p "$PASSWORD" ${artifactory_url} 
                    """
                    for (images in list_of_images){
                        println("Image variable from list_of_images > ${images} ")
                        docker_image = images.split(":")[0]
                        docker_tag = images.split(":")[1]
                        sh """
                                docker push "${images}"
                            """
                        ret_text = sh(returnStdout: true, script: "docker inspect  --format='{{index .RepoDigests 0}}' ${images}")
                        if(ret_text.contains(":")) {
                            sha256 = ret_text.split(":")[-1].trim()
                        } else {
                            sha256 = "NA"
                        }
                        docker_info = docker_info + ['name': "${docker_image}", 'tag':"${docker_tag}", 'sha256': sha256 ]
                    }
                }
                ret_code = generalStageUtils.exec_hook(post_step)
                if(ret_code != 0) {
                    error("docker_publish_poststep failed")
                }
            } catch(err){
                println("PIPELINE_ERROR docker_publish : "+err.getMessage())
                throw err
            }
            println("docker_info:" + docker_info)
            println ("docker_publish [end]")
            return(docker_info)

        } catch(err) {
            println("PIPELINE_ERROR docker_publish : "+err.getMessage())
            throw err
        }
        println ("docker_publish [end]")
    }
}//EO docker_publish


def validateMavenBuildParams(mvnBuildDetails) {
    println ('maven_build validation [start]')
    def hasErrors = false
    def errormessage = ''
    if (mvnBuildDetails instanceof String) {
        errormessage = 'maven_build: failed to meet the prerequisite mvnBuildDetails. Parameter invalid'
        hasErrors = true
        return [hasErrors, errormessage]
    }
    if (mvnBuildDetails.size() < 0) {
        errormessage = 'maven_build: failed to meet the prerequisite mvnBuildDetails. No prameter specified'
        hasErrors = true
        return [hasErrors, errormessage]
    }
    println ('maven_build validation [end]')
    return [false, errormessage]
}

def maven_build_execution(pre_step, post_step, mvnBuildDetails, buildVersion, build_command, branchType, git_branch_name, currentTimestamp, defaultPomVersion) {
    if (mvnBuildDetails) {
        (hasErrors,errorMessage) = validateMavenBuildParams(mvnBuildDetails)
        if (hasErrors) {
            error(errorMessage)
        }
    }

    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    setJavaHome()
    ret_code = generalStageUtils.exec_hook(pre_step)
    if (ret_code != 0) {
        error('maven_build: prestep failed')
    }

    boolean isDefaultPomVersion = defaultPomVersion // if null, sets false
    println ("use defaultPomVersion : $isDefaultPomVersion")

    if (mvnBuildDetails == null || mvnBuildDetails.size() < 1 ) {
        //default execution for maven build
        def buildCmd = build_command
        if (buildCmd == null || buildCmd.trim() == '') {
            println('buildCommand is null, hence using default command')
            buildCmd = 'clean deploy'// default command
        }
        println("updated build_version > $buildVersion")
        sh """
            cd ${workspace}
            if ${!isDefaultPomVersion}
            then
                mvn versions:set -DnewVersion=${buildVersion} 
            fi
            mvn ${buildCmd}
        """
    } else {
        for (def mvnBuildDetail in mvnBuildDetails) {
            def buildCmd = 'clean deploy'// default command
            def pomAbsolutePath = 'pom.xml' // default pom.xml at root directory
            if (mvnBuildDetail['pomFile'] &&  mvnBuildDetail['pomFile'].trim()) {
                pomAbsolutePath = mvnBuildDetail['pomFile'].trim()
            }
            println('pom file Path : ' + pomAbsolutePath)
            def pomFileName = pomAbsolutePath.split('/')[-1]
            def pomFileDir = pomAbsolutePath.replaceAll(pomFileName, '')
            if (mvnBuildDetail['buildCommand'] && mvnBuildDetail['buildCommand'].trim()) {
                buildCmd  = mvnBuildDetail['buildCommand']
                if (!buildCmd.contains("-f ${pomFileName}")) {
                    buildCmd = buildCmd + " -f ${pomFileName}"
                }
            }
            boolean isSetVersion = mvnBuildDetail['setVersion'] //if null, sets false
            def updatedVersion = (isSetVersion) ? prepareBuildVersion(mvnBuildDetail['version'],buildVersion, branchType.toLowerCase(), currentTimestamp, git_branch_name): buildVersion
        
            println("specified build command : $mvnBuildDetail.buildCommand executing build command : $buildCmd")
            println("build_version to be used : $updatedVersion")
            println ("use defaultPomVersion : $isDefaultPomVersion")
            sh """
                cd ${workspace}/${pomFileDir}
                if ${!isDefaultPomVersion}
                then
                    mvn -f ${pomFileName} versions:set -DnewVersion=${updatedVersion}
                fi
                mvn ${buildCmd}
            """
        }//EO for in
    }
    ret_code = generalStageUtils.exec_hook(post_step)
    if (ret_code != 0) {
        error('maven_build: post-step failed')
    }
}//EO maven_build_execution

def prepareBuildVersion(mvnBuildDetailVersion, buildVersion, branchType, currentTimestamp, git_branch_name) {
    def updatedVersion = buildVersion// initially, set default build_version
    if (mvnBuildDetailVersion) {
        updatedVersion = mvnBuildDetailVersion // set user specified version in mvnBuildDetails
        if (branchType == 'integ') {
            updatedVersion = "${updatedVersion}-${currentTimestamp}-master"
        }else if (branchType == 'dev') {
            updatedVersion = "${updatedVersion}-${currentTimestamp}-${git_branch_name}"
        }
    }
    return updatedVersion
}//EO prepareBuildVersion

def maven_publish_execution(publish_repo, commitHash, artifactoryUrl, credentialsId, project, publishFilePattern,publishExcludePattern, mvnBuildDetails, prestep,workspace) {
    
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    ret_code = generalStageUtils.exec_hook(prestep)
    if(ret_code != 0) {
        error("publish_prestep failed")
    }

    server = Artifactory.newServer url:"https://${artifactoryUrl}/artifactory" ,  credentialsId:"${credentialsId}"
    server.setBypassProxy(true)
    def properties = "git_commit=${commitHash}";
    def uploadFiles = []    
    def defaultPubSpecs = """{
                "pattern": "./packages/jarstaging/(*)",
                "target": "${publish_repo}/{1}",
                "flat": "false",
                "props" : "${properties}",
                "excludePatterns": [ "*.sha1", "*.md5"]
            }"""
    if (publishFilePattern != null && publishFilePattern.trim()) {
        def pubPatterns = publishFilePattern.split(',')
        def excludes = "[ \"*.sha1\", \"*.md5\"]"
        if(publishExcludePattern){
            excludes = []//reset the list
            for (int k = 0; k < publishExcludePattern.size(); k++) {
                String temp = publishExcludePattern.get(k)
                String temp2 = """ "${temp}" """
                excludes.add(temp2)
            }
        }
        for (int i = 0; i < pubPatterns.size(); i++) {
            //publish all files from publish directory. check if exclusions added as a param
            def pubPattern = pubPatterns[i].trim()
            println('Publish pattern specified as ' + pubPattern)
            uploadFiles.add("""{
                            "pattern": "${pubPattern}",
                            "target": "${publish_repo}/{1}",
                            "flat": "false",
                            "props" : "${properties}",
                            "excludePatterns": ${excludes}
                        }""")
                }//EO for loop
    }else if(mvnBuildDetails == null ){
        //execute, in case, user doesn't specified the path at all. 
        uploadFiles.add(defaultPubSpecs)
    }//EO if else pubDir

    if (mvnBuildDetails) { 
        for (int i = 0; i < mvnBuildDetails.size(); i++) {
            //def isPackageCmd = false
            if (mvnBuildDetails[i].get('publishDirectory') != null && mvnBuildDetails[i].get('publishDirectory').trim()) {
                //publish all files from publish directory. check if exclusions added as a param
                def pubDir = mvnBuildDetails[i].get('publishDirectory').trim()
                println('Publish directory specified as ' + pubDir)
                def exclusions = []
                if (mvnBuildDetails[i].get('exclude') != null && mvnBuildDetails[i].get('exclude').size() > 0) {
                    //def exclusions = mvnBuildDetails[i].get('exclude')// value should be  ["*.sha1","*.md5"]
                    def excludeList = mvnBuildDetails[i].get('exclude')
                    for (int k = 0; k < excludeList.size(); k++) {
                        String temp = excludeList.get(k)
                        String temp2 = """ "${temp}" """
                        exclusions.add(temp2)
                    }
                }
                println('Exclusions in file ' + exclusions + '. Uploading all files excluding these mentioned.')
                uploadFiles.add("""{
                        "pattern":"*${pubDir}",
                        "target":"${publish_repo}/{1}",
                        "flat": "false",
                        "props" : "${properties}",
                        "excludePatterns": ${exclusions}
                    }""")
            }else {
                //execute, in case, build command is mvn package
                uploadFiles.add(defaultPubSpecs)
            }//EO if else pubDir
        }//EO for loop
    }
    println('uploadFiles >> ' + uploadFiles)
    def uploadSpec = """{
            "files":${uploadFiles}
        }"""

    println('Artifactory upload specs ' + uploadSpec)
    if (uploadFiles.size() > 0) {
        def buildInfo = server.upload(uploadSpec)
        println('buildInfo starts here-----------')
        println('buildInfo ' + buildInfo.dump())
        println('buildInfo ends here-----------')
        buildInfo.env.collect()
        server.publishBuildInfo(buildInfo)
    }else {
        println('NOTHING TO PUBLISH')
    }
}//maven_publish_execution

def updatePackageJson(version) {
    def currentPackageJsonVersion = sh(returnStdout: true, script: 'grep -m 1 \'version\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
    if(version == "${currentPackageJsonVersion}") {
        println("This version is already available in package.json!")
        return false
    } else {
        println("Updating package.json with version "+version)
        sh "npm --no-git-tag-version version -f ${version}"
        return true
    }
}

def checkPackageJsonUpdate(newVersion, branchName) {
    sh """
        git checkout -f origin/${branchName} package.json
        npm --no-git-tag-version version -f ${newVersion}
        git add package.json
    """
    gitPush {
        commit_hash_map_file = 'packageJson_commit_hash'
        commitMessage = "Updating the package.json file with ${newVersion} version but with [ci-skip]"
        gitPushBranch = "${branchName}"
    }
}

//End of file
