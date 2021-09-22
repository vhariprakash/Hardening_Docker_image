package org.utils;
/* Utility to run linux commands */

/**
 Function Name : execute_command
 Description   : Execute Shell command and return exit code
 Arguments     : 
    cmd_list   -  a list of strings, with command in first element
                 and parameters from subsequent elements
 Returns       : Exit code of the command
*/
def execute_cmd(cmd_list) {
    def executor = cmd_list.execute()
    // Wait till process returns
    executor.waitFor()
    // Return command exit value
    return executor.exitValue()
}

/**
 Function Name : exec_ret_output
 Description   : Execute Shell command and return its text output
 Arguments     : 
    cmd_list   - a list of strings, with command in first element
                 and parameters from subsequent elements
 Returns       : Output from the command
*/
def exec_ret_output(cmd_list) {
    def executor = cmd_list.execute()
    // Wait till process returns
    executor.waitfor()
    // Return command output
    return executor.text
}

/**
 Function Name : shell_ret_output
 Description   : Execute Shell command using jenkins sh step and return its 
                 text output
 Arguments     : 
   cmd         - a command in the form of string to execute
 Returns       : Output from the command
*/
def shell_ret_output(cmd) {
    def output = sh(returnStdout:true, script:cmd)
    println(output)
    // Return command output
    return output.trim()
}

/**
 Function Name : shell_ret_exitVal
 Description   : Execute Shell command using jenkins sh step and return its
                 exit value
 Arguments     : 
   cmd         - a command in the form of string to execute
 Returns       : Exit Value from the command
*/
def shell_ret_exitVal(cmd) {
    def exitVal = sh(returnStatus:true, script:cmd)
    // Return command output
    return exitVal
}

