package org.utils;
/* Utility to send Status mail to stakeholders */

/**
 Function Name          : mailer
 Description            : Mails stakeholders, the build information
 Arguments:
   build_info           - Build information to be mailed
   mailing_list         - Mail ids of stakeholders
   project_name         - Name of the project
*/
def email_build_status(build_info, mailing_list, project_name) {
    jaas_email {
        mailingList="${mailing_list}"
        projectName="${project_name}"
        message=status_message
        includeChangeSet=true
    }
}

