package org.utils;

/**
 Function Name          : exception_handler
 Description            : Marks build as failure, prints error message
                          and mails stakeholders the result
 Arguments:
   errMsg               : error message to be printed
   err                  - Exception information, if used in a try catch block
   mailingList          - Stakeholder Mail information
   project_name         - Repo Name
*/
def exception_handler(errMsg, err, project_name, mailingList) {
    currentBuild.result = "FAILURE"
    def emailExtraMsg = errMsg + err.getMessage()
    def message = errMsg + err
    println(message)
    def mailObj = new org.utils.mailer()
    mailObj.email_build_status("${emailExtraMsg}", "${mailingList}")
}


/**
 Function Name          : error_handler
 Description            : Marks build as failure, prints error message
                          and mails stakeholders the result
 Arguments:
   errMsg               : error message to be printed
   mailingList          - Stakeholder Mail information
   project_name         - Repo Name
*/
def error_handler(errMsg, project_name, mailingList) {
    currentBuild.result = "FAILURE"
    println(errMsg)
    def mailer = new org.utils.mailer()
    mailObj.email_build_status("${errMsg}", "${mailingList}")
}

/**
 Function Name          : success_handler
 Description            : Marks build as Success, prints message
                          and mails stakeholders the result
 Arguments:
   successMsg           - Message to be printed
   mailingList          - Stakeholder Mail information
   project_name         - Repo Name
*/
def success_handler(successMsg, project_name, mailingList) {
    currentBuild.result = "SUCCESS"
    println(successMsg)
    def mailer = new org.utils.mailer()
    mailObj.email_build_status("${emailExtraMsg}", "${mailingList}") 
}

