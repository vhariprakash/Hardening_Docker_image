import hudson.AbortException
node('CL_BuildNode'){
    def cmd = ""
    String cmd_result = ""
    def SRC = ""
    def DEST = ""
    def timestamp = sh (returnStdout: true, script: "date +%Y_%m_%d_%H_%M_%S")
    def return_code = ""
    def scan_script_error = ""
    timestamp=timestamp.replaceAll("\n","")
	
    stage ('Prep') {
        try{
            step([$class: 'WsCleanup'])
            sh "cd ${env.WORKSPACE}; rm -rf *" 
            println "Input Details"
            println "Nessus Scan Id: ${Nessus_Scan_ID}"
            println "Nessus Host Address: ${Nessus_Host_Address}"
            println "Nessus Port Number: ${Nessus_Port_Number}"
            println "Target Host Address: ${Target_Host_Address}"
            cmd_result = exec_ssh_cmd(" mkdir -p CyberLabPatch_Exec; echo \$?")
            println "cmd_result:${cmd_result}"
        }catch(error){
            println "Error in Prep stage"
            println error
            throw new AbortException("Failed")
        }
        
        
    }    
    stage ('Checkout') {
        try{
            retry(2) {
                timeout(time: 3, unit: 'MINUTES') {	
                    dir('patch-testing'){
                        git url: "git@gitlab-gxp.cloud.health.ge.com:Cyber-Security-Lab/patch-testing.git", credentialsId: 'ssh-Jenkins-s02'
                    } 
                }
            }
            //Copy the patch-testing script to target machine
            SRC = " ${env.WORKSPACE}/patch-testing/"
            DEST = "/root/CyberLabPatch_Exec/"
            cmd_result = exec_scp_command(SRC, DEST)            
        }catch(error){
            println "Error in checkout stage"
            println error
            throw new AbortException("Failed")
        }
        
    }
    
    stage ('Build') {
        try{
            def int_cmd = """cd CyberLabPatch_Exec/patch-testing; python CyberLabPatch.py --scanid ${Nessus_Scan_ID} -nh  ${Nessus_Host_Address} -np ${Nessus_Port_Number} -ta ${Access_Token} -ts ${Secret_Token};  echo \$?"""
            println int_cmd
            cmd_result = exec_ssh_cmd(int_cmd)
            scan_script_error = cmd_result
            println "cmd_result:${cmd_result}"
            return_code = cmd_result.split("\n")[-1]
            return_code = return_code.toInteger()
            
            if(return_code == 0){
                // Copy the log files
                def cp_cmd = """cd CyberLabPatch_Exec/patch-testing;  cp -Rf log ../log_${env.BUILD_NUMBER}_${timestamp};"""
                cmd_result = exec_ssh_cmd(cp_cmd)
                println cmd_result
                // Copy to workspace
                SRC = "/root/CyberLabPatch_Exec/log_${env.BUILD_NUMBER}_${timestamp}"
                DEST = "${env.WORKSPACE}/"
                cmd_result = exec_reverse_scp_command(SRC,DEST)
                sh "cd ${env.WORKSPACE}; zip -r log_${env.BUILD_NUMBER}_${timestamp}.zip log_${env.BUILD_NUMBER}_${timestamp}"
            } else {
                println "Scan script failed with above error"
            }
        }catch(error){
            println "Error in build stage"
            println error
            throw new AbortException("Failed")
        }
    }
    stage ('Notify') {
        try{
            wrap([$class: 'BuildUser']) {
                def user = env.BUILD_USER_ID
                def user_email = "${user}@ge.com"
                def job_url = env.JOB_URL
                def job_number = env.BUILD_NUMBER
                def pipeline_log = "${job_url}/${job_number}"
                def email_text = ""
                def mail_Subject = "Cyber Secuirity OS Patch Report for Nessus Scan Id:${Nessus_Scan_ID}"
                def from_address = "Service.Coreload_Jenkins_SSO@ge.com"
                if(return_code == 0){
                    println "In if"
                    sh """echo "The CyberSecuritty OS Patch has been run.\nReport resides in ${Nessus_Host_Address} in \'/root/CyberLabPatch_Exec/\'.\nHere is Jenkins Log ${pipeline_log}" > /tmp/message_body.txt"""
                }else{
                    println "In Else"
                    sh """echo "The CyberSecuritty OS Patch has been run with the below error.\n${scan_script_error}" > /tmp/message_body.txt """
                }
                sh  """ 
                    if [ -f log_${env.BUILD_NUMBER}_${timestamp}.zip ]
                    then
                        mailx -a log_${env.BUILD_NUMBER}_${timestamp}.zip -r ${from_address} -s "${mail_Subject}" "${user_email}" < /tmp/message_body.txt
                    else
                        echo "To: ${user_email}" > /tmp/email.txt
                        echo "Subject: Cyber Secuirity OS Patch Report for Nessus Scan Id:${Nessus_Scan_ID}" >> /tmp/email.txt                        
                        echo "From: ${from_address}" >> /tmp/email.txt
                        cat  /tmp/message_body.txt >> /tmp/email.txt                        
                        sendmail -vt < /tmp/email.txt

                    fi
                """
            }
        }catch(error){
            println "Error in notify stage"
            println error
            throw new AbortException("Failed")
        }
    }
    
    stage ('Cleanup') {
        try{
            cmd_result = exec_ssh_cmd("cd CyberLabPatch_Exec; rm -rf patch-testing; echo \$?")
            if(return_code != 0){
                currentBuild.result = 'FAILURE'
            }
        }catch(error){
            println "Error in cleanup stage"
            println error
            throw new AbortException("Failed")
        }
    }
}

def exec_ssh_cmd(String cmd){

	environment {
		MY_CREDS = credentials('dummy-credentials')
		MY_CREDS_USR = "root"
		MY_CREDS_PSW = "${params.Target_Host_Password}"
	}		 
	MY_CREDS_USR = "root"
	MY_CREDS_PSW = "${params.Target_Host_Password}"
	
    def base_ssh = """sshpass -p '${MY_CREDS_PSW}' ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${MY_CREDS_USR}@${Target_Host_Address} '${cmd}'"""
	cmd_result = sh (returnStdout: true, script: cmd)
	return cmd_result
}


def exec_scp_command(def src, def dest){
	environment {
		MY_CREDS = credentials('dummy-credentials')
		MY_CREDS_USR = "root"
		MY_CREDS_PSW = "${params.Target_Host_Password}"
	}
	MY_CREDS_USR = "root"
	MY_CREDS_PSW = "${params.Target_Host_Password}"
    cmd_scp = """sshpass -p '${MY_CREDS_PSW}' scp -r -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${src} ${MY_CREDS_USR}@${Target_Host_Address}:${dest} """
	cmd_result = sh (returnStdout: true, script: cmd_scp)
    return cmd_result 
}

def get_reverse_scp_command(def src, def dest){
	environment {
		MY_CREDS = credentials('dummy-credentials')
		MY_CREDS_USR = "root"
		MY_CREDS_PSW = "${params.Target_Host_Password}"
	}
	MY_CREDS_USR = "root"
	MY_CREDS_PSW = "${params.Target_Host_Password}"
    cmd_scp = """sshpass -p '${MY_CREDS_PSW}' scp -r -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${MY_CREDS_USR}@${Target_Host_Address}:${src} ${dest}"""
	cmd_result = sh (returnStdout: true, script: cmd_scp)
    return cmd_result 
}
