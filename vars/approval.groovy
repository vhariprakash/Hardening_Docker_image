def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    dir('approver_repo') {
        String timeout_for_approval = '8'
        String timeout_unit = 'HOURS'
        def baseDir=pwd()
        def approver_repo_branch = "${config.approverRepoBranch}"
        def git_creds_id = "${config.gitCredsId}"
        def approver_repo_ssh_url = "${config.approverRepoSshUrl}"
        def approval_message = "${config.approvalMessage}"
        def approval_project_folder_name = "${config.approvalProjectFolderName}"
        def approval_project_key = "${config.approvalProjectKey}"
        //def timeout_passed = "${config.approvalTimeout}"
        if (config.approvalTimeout){
            echo 'break1'
            timeout_for_approval = "${config.approvalTimeout}"
        }
        if (config.timeoutUnit){
            timeout_unit = "${config.timeoutUnit}"
        }
        def input_id = 'Approve'
        def mail_subject = "Jenkins Job: ${env.JOB_NAME} is waiting up for your approval"
        def mail_body = "Dear User</br></br> Please go to ${env.BUILD_URL} and approve or abort the job. </br></br> Regards </br>Yours Truly </br> Jenkins Server"
        git branch: "${approver_repo_branch}", changelog: false, credentialsId: "${git_creds_id}", poll: false, url: "${approver_repo_ssh_url}"
        def approver_file_location = pwd() + '/' + approval_project_folder_name + '/' + 'approvers.yaml'
        def approver_project_yaml = readYaml file: ("${approver_file_location}")
        def overall_approvers_email_id = approver_project_yaml['Overall_Approvers']['Approvers'].keySet().join(", ")
        def project_approvers_email_id = approver_project_yaml["${approval_project_key}"]['Approvers'].keySet().join(", ")
        def mail_to = overall_approvers_email_id +',' + project_approvers_email_id
        def overall_approvers_sso = approver_project_yaml['Overall_Approvers']['Approvers'].values().join(", ")
        def project_approvers_sso = approver_project_yaml["${approval_project_key}"]['Approvers'].values().join(", ")
        def submitter_list = overall_approvers_sso.replaceAll(' ','') +','+ project_approvers_sso.replaceAll(' ','')
        println "These People Can Approve this Job: ${submitter_list}"
        timeout(time: timeout_for_approval.toInteger(), unit: "${timeout_unit}") {
            emailext body: "${mail_body}", subject: "${mail_subject}", to: "${mail_to}"
            metadata = input id: "${input_id}", message: "${approval_message}", submitter: "${submitter_list}"
        }
    }    
}
