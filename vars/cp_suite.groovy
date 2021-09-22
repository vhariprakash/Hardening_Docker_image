def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def revRepoPath = "imaging-devops/common-lib-devops"
    def templateFilePath = "resources/Email_Templates/"
    def fileNameInfo = "cp_suite_email_template.txt"
    //def branchName = "${env.sourceBranch}"
    def branchName = "cd-pipeline-coreload"//"${env.sourceBranch}"
    def build_level = []
    def branch_map = [:]
    def level = 0
    def buildVersion_CP = "${env.Release_Version}"?: ""
    def buildLabel_CP = "${env.Build_Label}"?: ""
    def artifactURL = "https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/"
    def artifactoryRepo = "${config.rpmArtifactoryLocation}"
    def buildStatus =  currentBuild.result ? currentBuild.result : 'SUCCESS'
    def buildNumber = "${env.BUILD_NUMBER}"
    def buildDate = new Date(currentBuild.getStartTimeInMillis());
    def buildDuration = (System.currentTimeMillis() - currentBuild.getStartTimeInMillis());
    def buildDetials = "${env.BUILD_URL}"
    def buildConsoleLog = "${env.BUILD_URL}console"
    def buildComponentArtifact = artifactURL + artifactoryRepo + "/Cyberpackage_"+ buildVersion_CP +"/" + buildLabel_CP
    def emailSubject = buildStatus+" -CyberPackage-Build Label: "+buildLabel_CP + " -Build Version: "+ buildVersion_CP +" -Build Number: "+  buildNumber 
    def mailingList = "${config.mailingList}"                      
    
    def cyberPackageArtifact = buildComponentArtifact + "/CyberPackage"
    node('Docker06'){
        try{
        timestamps {
            currentBuild.description = "${env.sourceBranch}"
        	//Adding the Code for the CD FLag
        	timeout(time: 10, unit: 'HOURS') {
        	    //node('Docker06'){
        		    def getBuildObj
        			def build_Status
        			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: '212441372', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]){
            			result = construct_build_level("${config.build_level}", config)
            			build_level = result[0]
            			branch_map = result[1]
            				
            			build_level.each{
                            level++
                            def stepsForParallel = it.collectEntries {
        				        ["${it}" : buildJobParallel(it, level, branch_map[it], buildVersion_CP, buildLabel_CP )]
        				    }
                                parallel stepsForParallel
                        }
                        //Build Information
                        
                        def email_body = read_mail_template(branchName, revRepoPath, templateFilePath, fileNameInfo, "${env.workspace}")
                        println "Class"+ email_body.getClass()
                        email_body = email_body.replaceAll("buildStatus", buildStatus)
                        email_body = email_body.replaceAll("buildNumber", buildNumber)
                        email_body = email_body.replaceAll("buildDateTime", "${buildDate}")
                        email_body = email_body.replaceAll("buildDuration", "${buildDuration}")
                        email_body = email_body.replaceAll("buildDetials", buildDetials)
                        email_body = email_body.replaceAll("buildConsoleLog", buildConsoleLog)
                        email_body = email_body.replaceAll("buildComponentArtifact", buildComponentArtifact)
                        email_body = email_body.replaceAll("cyberPackageArtifact", cyberPackageArtifact)
                        
                        sendMail(emailSubject, email_body, mailingList)
                        
                            				
        			}//WithEnv 
        		//}//endnode
                }//timeout
            }//timestamp
        }catch(hudson.AbortException ae){
            println "InABORT"
            currentBuild.result = 'ABORTED'
        	println ae
        	def abort_fileNameInfo = "cp_suite_fail_email_template.txt"
        	def abort_email_body = read_mail_template(branchName, revRepoPath, templateFilePath, abort_fileNameInfo, "${env.workspace}")
        	//sh "cat '${abort_fileNameInfo}'"
        
        	abort_email_body = abort_email_body.replaceAll("buildStatus", "ABORTED")
            abort_email_body = abort_email_body.replaceAll("buildNumber", buildNumber)
            abort_email_body = abort_email_body.replaceAll("buildDateTime", "${buildDate}")
            abort_email_body = abort_email_body.replaceAll("buildDuration", "${buildDuration}")
            abort_email_body = abort_email_body.replaceAll("buildDetials", buildDetials)
            abort_email_body = abort_email_body.replaceAll("buildConsoleLog", buildConsoleLog)
            abort_email_body = abort_email_body.replaceAll("buildFailedReason", "${ae}")
            def abort_emailSubject = "ABORTED -CyberPackage-Build Label: "+buildLabel_CP + " -Build Version: "+ buildVersion_CP +" -Build Number: "+  buildNumber 
            
            sendMail(abort_emailSubject, abort_email_body, mailingList)
    
        }catch(err){
            println "InFAIL"
            currentBuild.result = 'FAILURE'
        	println err
        	def fail_fileNameInfo = "cp_suite_fail_email_template.txt"
        	def fail_email_body = read_mail_template(branchName, revRepoPath, templateFilePath, fail_fileNameInfo, "${env.workspace}")
        	//sh "cat '${fail_email_body}'"
        	println fail_email_body
        	println "Class"+ fail_email_body.getClass()
        	println "buildStatus:"+buildStatus+":"+buildNumber
        	fail_email_body = fail_email_body.replaceAll("buildStatus", "FAILED")
            fail_email_body = fail_email_body.replaceAll("buildNumber", buildNumber)
            fail_email_body = fail_email_body.replaceAll("buildDateTime", "${buildDate}")
            fail_email_body = fail_email_body.replaceAll("buildDuration", "${buildDuration}")
            fail_email_body = fail_email_body.replaceAll("buildDetials", buildDetials)
            fail_email_body = fail_email_body.replaceAll("buildConsoleLog", buildConsoleLog)
            fail_email_body = fail_email_body.replaceAll("buildFailedReason", "${err}")
            def fail_emailSubject = "FAILED -CyberPackage-Build Label: "+buildLabel_CP + " -Build Version: "+ buildVersion_CP +" -Build Number: "+  buildNumber 
            
            sendMail(fail_emailSubject, fail_email_body, mailingList)
        } finally{
        	println "Completed with "+currentBuild.result
        }
    }//endnode
}

def read_mail_template(branch, repoPath, templateFilePath, filename, workspace){
    try{
        def scmObj = new org.utils.scm()
        sh "rm -rf ${filename}"
        def TemplateFileName = scmObj.get_file_from_repo(branch, repoPath, templateFilePath+filename)
        def template = jaas_data {
            dirName =  workspace
            fileName= TemplateFileName
            action='read'
            onSlave = true
        }
        return template
    }catch(err){
        println "Error while reading the file"+err
    }
}

def sendMail(mailSubject, emailbody, email_list){
    def email = new coreload.generic_email()
     def filepath = "resources/Email_Templates/"
     def templatename = "cp_suite_email_template.txt"
     def email_body = email.getMailTemplate(templatename,filepath)
     println "Email to "+ email_list
     email.sendEamil(mailSubject, email_list, emailbody)
}

def buildJobParallel(def comp, def level, def branch, def buildVersion, def buildLabel ){
    def branch_info = branch? branch:"master"
    println "BranchInfo:"+branch_info
    println "BuildLable:"+buildLabel
    println "BuildVersion:"+buildVersion
    return {
        stage(level+" : "+comp) {
            build job: "$comp", parameters: [gitParameter(name: 'sourceBranch', value: "${branch_info}"), 
                                             gitParameter(name: 'Build_Label', value: "${buildLabel}"), 
                                             gitParameter(name: 'Release_Version', value: "${buildVersion}")], wait: true
        }
    }
}

def construct_build_level(String build_level, def config ){
    println "Hello from Function"
    def build_level_list = []
    branch_map = [:]
    def level=""
    build_level.split("\n").each{
        level = it
        def internal_list = []
        level.split(",").each{
            if(it.trim() != ""){
                internal_list.add(it.trim())
                branch_map[it.trim()] = config."${it.trim()}"
            }
            
        }
        if(internal_list[0] != ""){
            build_level_list.add(internal_list)
        }
        
    }
    return [build_level_list,branch_map]
}
