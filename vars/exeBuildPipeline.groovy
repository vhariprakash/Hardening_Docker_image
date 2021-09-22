def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    println "JP: EXE Creation Stage step1"
    def WindowsBuildNode = config.WindowsBuildNode ?:'Windows_CP'
    // Shell Obj for running shell commands
    def shellObj = new org.utils.shell()
    // SCM Obj for git operations
    def scmObj = new org.utils.scm()
    // Manifest Obj for Manifest operations
    def manifestObj = new org.utils.manifest()
    // Version variables
    // For Semantic version controlled by EES through build.gragle
    def build_version = config.buildVersion
    def build_release = config.buildRelease
    def branch_name   = config.branchName 
    def project       = config.project
    def workspace     = config.workspace?:'C:\\GO\\SRC\\workspace'
    println "JP: EXE Creation Stage step2"
    println "Build Node : "+WindowsBuildNode
    node(WindowsBuildNode) {
        try {
        //    step([$class: 'WsCleanup'])
            bat """
                cd $workspace
                dir
                del *.* /q
            """
            def ws = pwd()
            println "JP: EXE Creation Stage step3"+ws
            dir(workspace){
                checkout scm
                bat(returnStdout: true, script: "git rev-parse --short HEAD > GIT_COMMIT")
                commitHash = readFile('GIT_COMMIT').take(6)
                println "Commit ID : "+commitHash
                src = "*.*"
                flg = "/E /Y"
/*                bat """
                    xcopy  $src ${workspace}  $flg 
                """*/
            }                
            println "JP: EXE Creation Stage step4"

            withEnv([
                "HTTP_PROXY=http://PITC-Zscaler-ASPAC-Bangalore3PR.proxy.corporate.ge.com:80",
                "HTTPS_PROXY=http://PITC-Zscaler-ASPAC-Bangalore3PR.proxy.corporate.ge.com:80"
            ]){                     
                dir(workspace) {
                    println "Working directory in CD Node is : "+workspace
                    println ("Executing go build")
                    bat """
                        cd
                        ipconfig
                        dir
                        go build
                    """
                    if(fileExists(project+".exe")) {
                        bat """
                            dir
                            package.bat
                        """
/*                        def exeCopyCmd = project + ".exe output\\bin\\"
                        def batCopyCmd = "*.bat output\\"
                        def configCopyCmd = workspace+"\\config\\*.* output\\configs\\"
                        def zipCmd = " -a -c -f "+project+".zip output\\*.*"
                        bat """
                            dir
                            mkdir output
                            mkdir output\\bin
                            copy $exeCopyCmd
                            copy $batCopyCmd
                            mkdir output\\configs
                            copy $configCopyCmd
                            mkdir output\\logs
                            cd output
                            tar $zipCmd
                        """
*/
                    }
                    else {
                        throw   new Exception ('EXE not generated')
                    }
                }
            }
            stage("EXE Artifact Publish") {
                echo "Artifact Publish"
                echo "URL : "+config.exeArtifactPath
                dir(workspace+"\\output") {
             /*       def filenames = getFileName(".\\", ".zip")
                    if(filenames.length == 0) {
                        throw new Exception ("No ZIP File not found for publish")
                    } else if (filenames.length > 1) {
                        throw new Exception ("Multiple ZIP Files found for publish")
                    } else {
                        filename = filenames[0]
                        println ("ZIP filename : " + filename)
                    }
                    */
                    filename = "decipher-service-1.0-0.1.x86_64.zip"
                    if(filename) {
                        withCredentials([usernamePassword(credentialsId: 'gip_sv01_artifactory_eu', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                            def curlCommand = "-u $USERNAME:$PASSWORD -X PUT "+config.exeArtifactPath+"/"+filename+" -T" + filename
                            println ("Curl Command: "+curlCommand)
                            bat """
                                curl ${curlCommand}
                            """
                        }
                    } else {
                        throw new Exception ("No ZIP File not found for publish")
                    }
                }
            }
        }  
        catch(err){
            currentBuild.result = 'FAILURE'
            emailExtraMsg = err.getMessage()
            throw err
        }   
    }
}		
/*
def getFileName(dir, filter) {
	dlist = []
	flist = []
    fileteredFiles = ""
    new File(dir).eachDir {dlist << it.name }
    dlist.sort()
    new File(dir).eachFile(FileType.FILES, {flist << it.name })
    flist.sort()
    files = (dlist << flist).flatten()
//   println files
    files.each {
    // println it
        if(it.endsWith(filter)) {
            fileteredFiles = fileteredFiles + it + "\n"
        }
    }
    return fileteredFiles.split("\n")
}

*/