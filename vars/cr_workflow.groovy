import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import java.text.SimpleDateFormat
import groovy.json.JsonSlurper
import groovy.transform.Field

@Field def LOG_CONTENT = ''
@Field def EMAIL_CONTENT = '<html><body><p>'
@Field def ERROR_MSG = ''

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def build_node = config.buildNode ?: 'CCS-Node'
    node(build_node) {

    def userID = ''
    def userName = ''
    def cr_result    = "SUCCESS"
    def enable_stages =  ['Verify_User', 'Validate_Input', 'Validate_Release_Status', "Verify_Promotion", 'Validate_Artifact', 'Publish_Artifact']

    def url = ''
    def artifact_list = ''
    def start_time = get_date_time()
    def modality = params.Modality
    def product  = params.Product_or_Service
    def release  = params.Release
    def part_number = params.MWS_BOM_PART_or_CATALOG
    def vnv_doc_id = params.MWS_VnV_DocID
    def rel_letter_doc_id  = params.MWS_SW_Release_Letter_DocID
    def service_list
    def config_params
/*    if(params.Promotion_Target.contains('Artifactory')) {
        enable_stages.add('Publish_Artifact')
    }
    if(params.Promotion_Target.contains('FlexNet')) {
        enable_stages.add('Publish_FlexNet')
    }
    if(params.Promotion_Target.contains('EMMA')) {
        enable_stages.add('Publish_EMMA')
    }
*/

    try {
// Get user details...
        stage ('Prep & Checkout') {
            cleanup()
            def yaml_data = libraryResource 'continous_release/service_list.yaml'
            service_list = readYaml text: (yaml_data)
            yaml_data = libraryResource 'continous_release/config_params.yaml'
            config_params = readYaml text: (yaml_data)

            println("DSL Branch: "+env.BRANCH_NAME)
            wrap([$class: 'BuildUser']) {
                output = sh returnStdout: true, script: 'echo "${BUILD_USER_ID}"'
                userID = output.trim()
                output = sh returnStdout: true, script: 'echo "${BUILD_USER}"'
                userName = output.trim()
            }
            currentBuild.description = product
            log_message("<b>Continuous Release (CR) Workflow Tool v1.0</b>", true)
            log_message("Date:"+start_time, true)
            log_message("Build Number: "+env.BUILD_NUMBER, true)
            log_message("\n<br><b>Parameters selected:</b>", true)
            log_message("\tModality : " + modality, true)
            log_message("\tProduct  : " + product, true)
            log_message("\tRelease  : " + release, true)
            log_message("\tMWS BOM PART NUMBER : " + part_number, true)
            log_message("\tSW Release Letter DocID : " + rel_letter_doc_id, true)
            log_message("\tV&V Summary Report DocID: " + vnv_doc_id, true)

            if(params.SW_Artifact_List == '') {
                cr_result = "FAILED"
                artifact_list = "\n"+"Nothing to promote !"
                if(ERROR_MSG == '') {
                    ERROR_MSG = "Nothing to Promote !!"
                }
            } else {
                artifact_list = "\n\t\t"+params.SW_Artifact_List //.replace("[process]")
            }
        }

        withEnv(["http_proxy=${config_params['custom_parameters'][modality].http_proxy}",
                 "https_proxy=${config_params['custom_parameters'][modality].https_proxy}",
                 "no_proxy=${config_params['custom_parameters'][modality].no_proxy}" ]) {
            stage ('Authenticate User') {
                if(enable_stages.contains('Verify_User')){
                    log_message( "Workflow Triggered by : " + userName + "("+userID+")", true)
                    if(!(config_params["approvers"][modality].contains(userID)) ) {
                        log_message("\nERROR : User "+userName + "("+userID+") Not authorized to trigger this job", true)
                        cr_result = "FAILED"
                        if(ERROR_MSG == '') {
                            ERROR_MSG = "Unauthorized User"
                        }
                        throw new Exception ("Not authorized")
                    } else {
                        log_message( "Authorization - OK  "+userName + "("+userID+")<br>", true)
                    }
                }else{
                    log_message('Skip Stage : Verify User', false)
                }
            } //stage ('Authenticate User')

            stage ('Verify Input') {
                if(enable_stages.contains('Validate_Input')){
                    cr_result = validate_user_input(part_number, "Part Number not provided")
                    cr_result = validate_user_input(rel_letter_doc_id, "Release letter Doc ID not provided")
                    cr_result = validate_user_input(product, "Product can not be empty")
                    cr_result = validate_user_input(release, "Release can not be empty")
                    cr_result = validate_user_input(modality, "Modality not selected")
                    cr_result = validate_user_input(params.Promotion_Target, "No target selected")
                    cr_result = validate_user_input(params.SW_Artifact_List, "Artifact to promote list empty")

//                    service_list = readYaml file : 'config/service_list.yaml'
                    url = ''
                    try {
                        SW_Artifact_List.split("\n").each {
                            if (it != '') {
                                line = it
                                url = line.split(",")[0]
                                url = url.replace("[process]", "")
                                if(line.contains("[process]")) {
                                    if(line.contains(service_list["apps_list"][product.toUpperCase()])) {
                                        println("artifact file ok")
                                    } else {
                                        println("artifact file not ok")
                                        cr_result = "FAILED"
                                        if(ERROR_MSG == '') {
                                            ERROR_MSG = "Invalid product/chart file"
                                        }
                                        throw new Exception("Invalid product/chart file")
                                    }
                                }
                                sha256 = get_checksum(url, "sha256")
                                md5sum = get_checksum(url, "md5")
                                if (sha256 == '') {
                                    cr_result = "FAILED"
                                    if(ERROR_MSG == '') {
                                        ERROR_MSG = "Unable to find checksum of input files. Please check files specified"
                                    }
                                    throw new Exception("Invalid product/chart file")
                                }
                                sh """
                                    echo "        ${url}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]"
                                    echo "        ${url}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]" >> /tmp/input_checksum.txt
                                    echo "        ${url}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]" >> ${env.WORKSPACE}/input_checksum.txt
                                """
                            }
                        }
                    }catch (err) {
                        println("Input Artifact checksum error : " +err.getMessage())
                        cr_result = "FAILED"
                        if(ERROR_MSG == '') {
                            ERROR_MSG = "Invalid product/chart file"
                        }
                        throw new Exception("Invalid product/chart file")
                    }
                    println("validate input done")
                }else{
                    log_message('Skip Stage : Validate input', false)
                }
            } //stage ('Verify Input')

            stage ('Validate Release Status') {
                if(enable_stages.contains('Validate_Release_Status')){
                    mws_doc = part_number.split(" ")
                    docid = mws_doc[0]
                    revno = mws_doc[-1].toUpperCase().replace("REV", "").trim()
                    String bom_status = get_mws_status(docid, revno)
                    log_message("MyWorkShop Document Status:", true)
                    log_message("    "+part_number+"  :  "+bom_status, true)
                    if(bom_status == 'Release') {
                        if(bom_status == '') {
                            log_message("\nERROR: Unable to get MWS status for "+ part_number, true)
                            log_message("Unable to get MWS status for "+part_number, false)
                        } else {
                            log_message("\nERROR: Document "+ part_number + " not in released state.  (Actual status: "+bom_status+")", true)
                            log_message("Document "+part_number + " not in released state   (Actual status: "+bom_status+")", false)
                        }
                        cr_result = "FAILED"
                        if(ERROR_MSG == '') {
                            ERROR_MSG = "Invalid DOC Status in MWS"
                        }
                        throw new Exception("Invalid doc status")
                    }

                    mws_doc = rel_letter_doc_id.split(" ")
                    docid = mws_doc[0]
                    revno = mws_doc[-1].toUpperCase().replace("REV", "").trim()
                    String rel_note_status = get_mws_status(docid, revno)
                    log_message("    "+rel_letter_doc_id+"  :  "+rel_note_status, true)
                    if(rel_note_status != 'Released') {
                        if(rel_note_status == '') {
                            log_message("\nERROR: Unable to get MWS status for "+ rel_letter_doc_id, true)
                            log_message("Unable to get MWS status for "+rel_letter_doc_id, false)
                        } else {
                            log_message("Document "+rel_letter_doc_id + "not in released state  (Actual status: "+rel_note_status+")", false)
                            log_message("\nERROR: Document "+rel_letter_doc_id + " not in released state   (Actual status: "+rel_note_status+")", true)
                        }
                        cr_result = "FAILED"
                        if(ERROR_MSG == '') {
                            ERROR_MSG = "Invalid DOC Status in MWS"
                        }
                        throw new Exception("Invalid doc status")
                    }

                    mws_doc = vnv_doc_id.split(" ")
                    docid = mws_doc[0]
                    revno = mws_doc[-1].toUpperCase().replace("REV", "").trim()
                    println("Call3 "+docid+"    "+revno)
                    String vnv_doc_status = get_mws_status(docid, revno)
                    log_message("    "+vnv_doc_id+"  :  "+vnv_doc_status, true)
                    if(vnv_doc_status != 'Released') {
                        if(vnv_doc_status == '') {
                            log_message("\nERROR: Unable to get MWS status for "+ vnv_doc_id, true)
                        } else {
                            log_message("\nERROR: Document "+vnv_doc_id + " not in released state   (Actual status: "+vnv_doc_status+")", true)
                        }
                        cr_result = "FAILED"
                        if(ERROR_MSG == '') {
                            ERROR_MSG = "Invalid DOC Status in MWS"
                        }
                        throw new Exception("Invalid doc status")
                    }
                }
                else{
                    log_message('Skip Stage : Validate Release Status', false)
                }
            }//stage ('Validate Release Status')
            stage ('Download Artifacts') {
                if(enable_stages.contains('Validate_Artifact')){
                    url = ''
                    try {
                        SW_Artifact_List.split("\n").each {
                            if (it != '') {
                                to_be_processed = 0
                                line = it
                                url = line.split(",")[0]
                                println("URL:"+url)
                                if(url.contains("[process]")) {
                                    to_be_processed = 1
                                    url = url.replace("[process]","")
                                }
                                if(url.indexOf(':') > 6) { //  : found in case of docker image
                                    docker_tag  = url.split(':')[1]
                                    docker_name = url.split("/")[-1].split(":")[0]
                                    sh """
                                        docker pull ${url} | tee /tmp/result.txt
                                        echo "${url} : " `grep sha256 /tmp/result.txt |  cut -f3 -d:` >> /tmp/release_local_checksum.txt
                                        echo "${url} : " `grep sha256 /tmp/result.txt |  cut -f3 -d:` >> /tmp/preprocess_local_checksum.txt
                                        echo "${docker_name}:${url} SHA256="`grep sha256 /tmp/result.txt | cut -f3 -d:` >> shaDockerValue_release.yaml
                                    """
                                } else {  // Other than docker image and charts -  for individual file promote
                                    file_name = url.split('/')[-1]
                                    file_ext = (file_name.substring(file_name.lastIndexOf(".")+1))
                                    println("URL!: "+url)
                                    sha256 = get_checksum(url, "sha256")
                                    md5sum = get_checksum(url, "md5")
                                    if(to_be_processed == 0) {
                                        sh """
                                            pwd
                                            ls -l
                                            touch a.txt
                                            echo "        ${url}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]"
                                            echo "        ${url}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]" >> /tmp/release_local_checksum.txt
                                            echo "        ${url}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]" >> /tmp/preprocess_local_checksum.txt
                                            echo "${file_name}:${url} SHA256=${md5sum}" >> shaDockerValue_release.yaml
                                            ls -l
                                        """
                                    } else {
                                        sh """
                                            echo "        ${url}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]"
                                            echo "        ${url}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]" >> /tmp/preprocess_local_checksum.txt
                                        """
                                    }
                                    if(to_be_processed == 0) {
                                        ret=sh(returnStdout:false, returnStatus:true, script:"curl --noproxy '*' -s -S -X GET ${url} --output ${file_name} ")
                                        if(ret == 0) {
                                            log_message (it + "Artifact OK", false)
                                        } else {
                                            log_message("ERROR: Error Code : "+ ret.toString()+"\t"+url, false)
                                            currentBuild.result = "FAILURE"
                                            if(ERROR_MSG == '') {
                                                ERROR_MSG = "Unable to read checksum of artifact"
                                            }
                                            cr_result = "FAILED"
                                            throw err
                                        }
                                    }
                                }
                            }
                            log_message("Artifacts : All OK", false)
                        }
                    } catch (err) {
                        println ('EXCEPTION-1 : '+err.getMessage())
                        log_message ('ERROR : '+err.getMessage(), false)
                        currentBuild.result = "FAILURE"
                        if(ERROR_MSG == '') {
                            ERROR_MSG = "Error validating the artifacts"
                        }
                        cr_result = "FAILED"
                        throw err
                    }
                }else{
                    log_message('Skip Stage : Validate Artifact', false)
                }
            } //stage ('Download Artifacts')
            stage ('Process Artifacts') {
                println("Process artifact hook")
            }

            stage ('Update Staging') {
                println("Update staging & UAT systems")
            }

            if(enable_stages.contains("Verify_Promotion")) {
                stage("Final Approval") {
                    url = 'http://inblr-cl-jenkins-prod01.eng.med.ge.com/job/Continuous_Release/job/CR_Workflow_Tool_v1.0/'+"${env.BUILD_NUMBER}"+'/input/'                    
                    println(url)
                    sh """
                        echo "To: ${userID}@ge.com" > /tmp/email.txt
                        echo "Subject: ${modality} CR Workflow - Awaiting verification : ${product} ${release}" >> /tmp/email.txt
                        echo "Content-type: text/html" >> /tmp/email.txt
                        echo "From: Service.Coreload_Jenkins_SSO@ge.com" >> /tmp/email.txt
                        echo "${EMAIL_CONTENT}" >> /tmp/email.txt
                        echo "<br>Release Artifact checksum :" >> /tmp/email.txt
                        cat /tmp/input_checksum.txt >> /tmp/email.txt

                        echo "<br><br>ACTION: Please verify above details and goto below URL to proceed. <br>Type the word Verified for continue.</b> " >> /tmp/email.txt
                        echo "<br>  NOTE: Workflow will timeout in 10 minutes" >> /tmp/email.txt
                        echo "<br> ${url}" >> /tmp/email.txt
                        echo '</p></body></html>' >> /tmp/email.txt
                        sendmail -vt < "/tmp/email.txt"
                    """
                    println("mail sent for verification")
                    def userInput = ''
                    def inputReq = jaas_data {
                        fileName='/tmp/email.txt'
                        action='read'
                        onSlave = true
                    }
                    try {
                        timeout(time: 10, unit: 'MINUTES') {
                            userInput = input(
                                id: 'userInput', message: 'Final Approval. Please type "Verified" and click Proceed', parameters: [
                                [$class: 'TextParameterDefinition', defaultValue: '', description: inputReq, name: 'Verify_Message'],])
                        }
                        println("Verification Message : "+userInput)
                        String msg = userInput.trim()
                        if(msg.toUpperCase() != "VERIFIED") {
                            log_message ('FINAL CONFIRMATION NOT RECEIVED. CANCELLING WORKFLOW', true)
                            if(ERROR_MSG == '') {
                                ERROR_MSG = "Final confirmation: \"Verified\" not received"
                            }
                            cr_result = "FAILED"
                            throw new Exception("Final confirmation not received")
                        }
                    } catch (err) {
                        println("Input Error: "+err.getMessage())
                        if(ERROR_MSG == '') {
                            ERROR_MSG = "workflow aborted"
                        }
                        cr_result = "FAILED"
                        throw new Exception("Final confirmation not received")
                    }
                }
            } else {
                println("SKIP Approval stage")
            } //enable_stages.contains("Verify_Promotion")
            if(enable_stages.contains('Publish_Artifact')){
                if(params.Promotion_Target.contains("Artifactory")) {
                    stage ('Publish Artifactory') {
                        SW_Artifact_List.split("\n").each {
                            if (it != '') {
                                to_be_processed = 0
                                url = it.split(",")[0]
                                println(url)
                                if(url.contains("[process]")) {
                                    to_be_processed = 1
                                    url = url.replace("[process]","")
                                    println("PARAMS  :"+product.toUpperCase())
                                    println("Procedure: "+config_params['process_hook'][modality])
                                    println(url)
                                    println("Modality logic [start] is here...")
                                    println(" groovy to be executed : "+ config_params['process_hook'][modality])
                                    
                                    "${config_params['process_hook'][modality]}" {
                                        chart_product = product
                                        chart_name = url
                                        modality_name = modality
                                        config_param_yaml = config_params
                                    }
                                    
                                    println("Modality logic [end] is here...")
                                }
                                if(to_be_processed == 0) {
                                    if(url.indexOf(':') > 6) { //  : found in case of docker image
                                        if(url.contains(config_params['custom_parameters'][modality]["docker_release_repo"])) {
                                            target_docker_name = url.replace(config_params['custom_parameters'][modality]["docker_release_repo"],config_params['custom_parameters'][modality]["docker_prod_repo"])
                                            docker_tag  = url.split(':')[1]
                                            /**  Required for binary comparision table */
                                            docker_name = url.split("/")[-1].split(":")[0]
                                            sh """
                                                docker tag  ${url} ${target_docker_name}
                                                docker push ${target_docker_name}  | tee /tmp/result.txt
                                                echo "${url} : " `grep sha256 /tmp/result.txt | cut -f4 -d: | cut -f1 -d' '` >> /tmp/release_remote_checksum.txt
                                                echo "${docker_name}:${target_docker_name} SHA256="`grep sha256 /tmp/result.txt | cut -f4 -d: | cut -f1 -d' '` >> shaDockerValue_prod_release.yaml
                                            """
                                            println("docker push completed")
                                        } else {
                                            println("invalid docker release repo")
                                            log_message("ERROR:invalid docker release repo", false)
                                            if(ERROR_MSG == '') {
                                                ERROR_MSG = "Invalid docker release repo"
                                            }
                                            cr_result = "FAILED"
                                        }
                                    } else {
                                        println("file publish")
                                        url_components = url.split('/')
                                        println(url_components)
                                        file_name = url_components[-1]
                                        src_repo = url_components[4]
                                        println("src_repo : " +src_repo)
                                        file_ext = (file_name.substring(file_name.lastIndexOf(".")+1))
                                        target_repo = url.replace(src_repo,config_params['custom_parameters'][modality]["prod_repo"])
                                        target_repo = replace_folder_artifactory_url(target_repo, config_params['custom_parameters'][modality]["prod_repo"], config_params['custom_parameters'][modality]["prod_folder"])

                                        def credID = ''
                                        if(target_repo.contains('blr-artifactory')) {
                                            credID = config_params['custom_parameters'][modality]["blr_artifactory_key"] ?: 'BlrArtifactoryApiKey'
                                        } else if(target_repo.contains('hc-eu-west-aws-artifactory')) {
                                            credID = config_params['custom_parameters'][modality]["eu_artifactory_key"] ?: '502782741_Artifactory_API_Key'
                                        } else if(target_repo.contains('hc-us-east-aws-artifactory')) {
                                            credID = config_params['custom_parameters'][modality]["us_artifactory_key"] ?: '502782741_Artifactory_API_Key'
                                        } else {
                                            println ("WARING :  ************  Unknown Artifactory ***********")
                                            credID = '502782741_Artifactory_API_Key'
                                        }
                                        println("Credential to use: "+credID)
                                        withCredentials([string(credentialsId: credID, variable: 'ApiToken')]) {
                                            ret=sh(returnStatus:true, script:"curl -H \"X-JFrog-Art-Api:${ApiToken}\" -X PUT $target_repo -T $file_name | tee /tmp/result.txt")
                                            curl_ret = sh (returnStatus:true, script:"grep downloadUri /tmp/result.txt" )
                                            println("ret="+ret+"  curl_ret="+curl_ret)
                                            if (ret != 0 || curl_ret != 0) {
                                                println("ERROR: Unable to publish the artifcat")
                                                cr_result = "FAILED"
                                                throw new Exception("Publish Error")
                                            }
                                        } //withCredentials

                                        sha256 = get_checksum(target_repo, "sha256")
                                        md5sum = get_checksum(target_repo, "md5")
                                        sh """
                                            pwd
                                            echo "        ${target_repo}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]"
                                            echo "        ${target_repo}  :  [md5sum: ${md5sum}]  :  [sha256: ${sha256}]" >> /tmp/release_remote_checksum.txt
                                            echo "${file_name}:${target_repo} SHA256=${md5sum}" >> shaDockerValue_prod_release.yaml
                                        """
                                    }
                                } // to_be_processed == 0
                            }
                        }// End of artifactory_list loop
                    } // end of publish artifactory stage
                } else {
                        log_message('Skip Stage : Publish Artifactory', false)
                }
            } // if params.Promote_Target.contains("Artifactory"))
/*          if(enable_stages.contains('Publish_FlexNet')){
                stage ('Publish FlexNet') {
                    try {
                        SW_Artifact_List.split("\n").each {
                            if (it != '') {
                                line = it
                                println(line)
                                url = line.split(",")[0]
                                part_num = line.split(",")[1]
                                file_name = url.split('/')[-1]
                                println("file_name == " + file_name)
                                PROGRAM='TEST'
                                sh """
                                    echo Source Path: $path
                                    echo Upload File: "${file_name}"
                                    echo Remote "Dir: /X-ray/MURAL/${PROGRAM}/"

                                    /usr/bin/sshpass -p Eu07do6Q sftp -o StrictHostKeyChecking=no -o ProxyCommand="connect-proxy -H 3.20.109.242:9400 %h %p" 1214@uploaduat.flexnetoperations.com << END_SFTP
mkdir /DevOps
cd /DevOps
mkdir ${params.Modality}
cd ${params.Modality}
mkdir ${params.Product_or_Service}
cd ${params.Product_or_Service}
mkdir ${params.Release}
cd ${params.Release}
ls -l
put "${file_name}"
ls -l
sha256sum "${file_name}"
md5sum "${file_name}"
ls -l
bye
END_SFTP
                                    """

                                    sh """
echo "Update flexNet now..."
java -jar /root/JP/FileUpload.jar << 'EOF'
${part_num}
${file_name}
/DevOps/${params.Modality}/${params.Product_or_Service}/${params.Release}/${file_name}
EOF
                                    """
                                }
                            }
                        } catch (err) {
                            println ("Error" + err.getMessage())
                        }
                    }
                } else {
                    log_message('Skip Stage : Publish FlexNet', false)
                }
                if(enable_stages.contains('Publish_EMMA')){
                    stage ('Publish EMMA') {
                        try {
                            SW_Artifact_List.split("\n").each {
                                if (it != '') {
                                    line = it
                                    println(line)
                                    url = line.split(",")[0]
                                    if(line.contains(",")) {
                                        part_num = line.split(",")[1]
                                    } else {
                                        part_num = part_number
                                    }
                                    file_name = url.split('/')[-1]
                                    println("file_name = " + file_name)
                                    println("Emma Server: 3.20.244.192")
                                    sh """
                                        echo Source Path: $path
                                        echo Upload File: "${file_name}"
                                        echo Remote "Dir: /Release/${modality}/${product}/${release}/"
                                        /usr/bin/sshpass -p Eu07do6Q sftp -o StrictHostKeyChecking=no -o ProxyCommand=\"connect-proxy -H 3.20.109.242:9400 %h %p\" 1214@uploaduat.flexnetoperations.com << END_SFTP
                                        mkdir /DevOps
                                        cd /DevOps
                                        mkdir ${params.Modality}
                                        cd ${params.Modality}
                                        mkdir ${params.Product_or_Service}
                                        cd ${params.Product_or_Service}
                                        mkdir ${params.Release}
                                        cd ${params.Release}
                                        ls -l
                                        put "${file_name}"
                                        ls -l
                                        sha256sum "${file_name}"
                                        md5sum "${file_name}"
                                        bye
                                        END_SFTP
                                    """

                                    sh """
echo "Update flexNet now..."
java -jar /thesource/dumpall/tools/FileUpload.jar << 'EOF'
${part_num}
${file_name}
/DevOps/${params.Modality}/${params.Product_or_Service}/${params.Release}/${file_name}
EOF
cp /tmp/preprocess_local_checksum.txt /tmp/release_remote_checksum.txt
                                    """

                                }
                            }
                        } catch (err) {
                            println ("Error" + err.getMessage())
                        }
                    }
                }else{
                    log_message('Skip Stage : Publish EMMA', false)
                } // if params.Promote_Target.contains("EMMA"))
****************/
        }// withEnv
    } catch(err) {
        println("cr_result in catch block :"+cr_result)
        if(ERROR_MSG == '') {
            ERROR_MSG = 'EXCEPTION-2 : '+err.getMessage()
        }
        log_message("ERROR:"+err.getMessage(), false)
        cr_result = "FAILED"
    } finally {
        stage ('Publish Report') {
            println("cr_result in finally block :"+cr_result)
            def fileContents = ''
            log_message("-------------------- CHECKSUMS+----------------", false)
            log_message("<br><b>CheckSum Report for :"+product+"</b>", true)
            log_message("    Release Artifact Checksum:", true)
            def preprocess_local_checksum = get_checksum_info("/tmp/preprocess_local_checksum.txt")
            fileContents = sh (returnStdout:true, script : "cat /tmp/preprocess_local_checksum.txt | nl || echo \"file not found\"")
            if(fileContents.contains("file not found")) {
                if(ERROR_MSG == '') {
                    ERROR_MSG = "PrePreocess Checksum information not found"
                }
            }
            log_message(fileContents.replaceAll('\n', '<br>') +"<br>", true)
            // Add checksums of files given in artifactory_list text box
            log_message("<br>    Processed Artifact Checksum:", true)
            def release_local_checksum = get_checksum_info("/tmp/release_local_checksum.txt")
            fileContents = sh (returnStdout:true, script : "cat /tmp/release_local_checksum.txt | nl || echo \"file not found\"")
            if(fileContents.contains("file not found")) {
                if(ERROR_MSG == '') {
                    ERROR_MSG = "Local Checksum information not found"
                }
            }
            log_message(fileContents.replaceAll('\n', '<br>') +"<br>", true)
            log_message("<br>    Production Artifact Checksum:", true)
            def release_remote_checksum = get_checksum_info("/tmp/release_remote_checksum.txt")
            fileContents = sh (returnStdout:true, script : "cat /tmp/release_remote_checksum.txt | nl || echo \"file not found\"")
            if(fileContents.contains("file not found")) {
                if(ERROR_MSG == '') {
                    ERROR_MSG = "Remote Checksum information not found"
                }
            }
            log_message(fileContents.replaceAll('\n', '<br>') +"<br>", true)

            log_message("-------------------- CHECKSUMS+----------------", false)

            if(release_local_checksum != release_remote_checksum || release_local_checksum.isEmpty() || release_remote_checksum.isEmpty()) {
                log_message(" Checksums compared and found ERROR\n", true)
                if(ERROR_MSG == '') {
                    ERROR_MSG = "Checksums compared and found ERROR"
                }
                cr_result = "FAILED"
            } else {
                log_message("<br><b>Software Binary Verification:</b>", true)
                binary_chksum_report = print_binary_checksums()
                println("binary_chksum_report:")
                log_message(binary_chksum_report, true)
                if(binary_chksum_report.contains("ERROR : ")) {
                    log_message("Summary: ERROR : There is an error in software binary comarison\n", true)
                    cr_result = 'FAILED'
                    ERROR_MSG = 'Binary Checksum compared and found error'
                } else {
                    log_message("Summary: There is no change to SW Binaries. Checksums compared and OK\n", true)
                }
            }
            log_message("<br><b> FINAL RESULT : " + cr_result+"</b>", true)
            if (cr_result == 'FAILED') {
                log_message( "<b>ERROR MESSAGE:</b> ${ERROR_MSG}", true)
            }

            target_repo=config_params['custom_parameters'][modality].artifactory_url+"/artifactory/"+config_params['custom_parameters'][modality].artifactory_repo+"/cr_workflow_logs/workflow_log_"+product.replaceAll(" ", "_").trim()+"_"+release.trim()+"_"+start_time.replaceAll(":", "-").replaceAll(' ','_')+".txt"
            if(cr_result == 'SUCCESS') {
                log_message("Log file is available in :  "+target_repo, true)
            }
            log_message("<br>End Time: "+get_date_time()+"<br>", true)
            log_message("<br>Help and support: blrdevops@ge.com", true)
            log_message("RELEASE REPORT START : \n"+EMAIL_CONTENT+ "\nRELEASE REPORT END", false)
            def to_list = "${userID}@ge.com;"
            config_params['approvers'][modality].each {
                to_list = to_list + it + "@ge.com;"
            }
//            to_list = "${userID}@ge.com"
            withCredentials([string(credentialsId: '502782741_Artifactory_API_Key', variable: 'ApiToken')]) {
                sh """
                    echo "To: ${to_list}" > /tmp/email.txt
                    echo "Subject: ${modality} CR Report : ${product} ${release} (${cr_result})" >> /tmp/email.txt
                    echo "Content-type: text/html" >> /tmp/email.txt
                    echo "From: Service.Coreload_Jenkins_SSO@ge.com" >> /tmp/email.txt
                    echo "${EMAIL_CONTENT}" >> /tmp/email.txt
                    echo '</p></body></html>' >> /tmp/email.txt
                    sendmail -vt < "/tmp/email.txt"
                """
                if (cr_result == 'SUCCESS') {
                    def build_log = currentBuild.rawBuild.getLog(10000).join('\n')
                    writeFile file: 'build_log.txt', text: build_log
                    sh """
                        curl -H "X-JFrog-Art-Api:${ApiToken}" -X PUT "${target_repo}" -T build_log.txt
                    """
                } else {
                    currentBuild.result = 'FAILURE'
                }
            }
        }
    }
}
}

def cleanup(){
    println("-------CLEANUP--------------")
    step([$class: 'WsCleanup'])
    sh """
        rm -Rf /tmp/*.txt || exit 0
        ls -l /tmp/*.txt  || exit 0
    """
}

def get_checksum (url, chksum) {
    def api_url = ''
    try {
        if(url.indexOf(':') > 6) { //  : found in case of docker image
            if(chksum == "sha256") {
                def output = sh (returnStdout: true, script: "docker pull ${url} > /tmp/result.txt; grep sha256 /tmp/result.txt |  cut -f3 -d:")
                return output
            } else {
                return ''
            }
        } else {
            api_url = url.replace('/artifactory/', '/artifactory/api/storage/')
            println("getchecksum url:"+ """curl --noproxy "*" -s -S -X GET '${api_url}' > result.json""")
            sh """curl --noproxy "*" -s -S -X GET '${api_url}' | tee result.json"""
            def src_output = readJSON file: "result.json"
            def jsonSlurper = new JsonSlurper()
            def object = jsonSlurper.parseText(src_output.toString())
            try {
                return (object["checksums"][chksum])
            } catch (err) {
                println ('EXCEPTION-3 : '+err.getMessage())
                log_message("md5 not found", false)
                return ''
            }
        }
    } catch(err) {
        println ('EXCEPTION-4 : '+err.getMessage())
        log_message("get_checksum ERROR:" + err.getMessage())
        return ''
    }
}

def get_date_time(String timestamp_format="dd-MMM-yyyy HH:mm:ss"){
    def date = new Date()
    sdf = new SimpleDateFormat(timestamp_format)
    return sdf.format(date)
}

def validate_user_input(value, err_msg) {
    try{
        if(value == '') {
            log_message("ERROR: "+err_msg, true)
            currentBuild.result = "FAILURE"
            cr_result = "FAILED"
            if(ERROR_MSG != '') {
                ERROR_MSG = err_msg
            }
            throw  new Exception("Invalid input")
            return("FAILED")
        }
        return("SUCCESS")
    } catch (err) {
        println("validate_user_input ERROR:" + err.getMessage())
        return("FAILED")
    }
}

def get_mws_status(docid, revision='') {
    def state = ''
    println("get_mws_status Docid : "+docid)
    println("get_mws_status revision : "+revision)
    try {
        if (docid.contains("DOC")) {
            sh """
                json_token=`curl --silent \
                   --request POST \
                   --data "client_id=GEHealth-7Be4Kf91fOu9bdw8VszNpG4G&client_secret=bcb698fc26a221ca97459594a814a472d0cf2bcf&scope=api&grant_type=client_credentials" https://fssfed.ge.com/fss/as/token.oauth2`
                token=`echo \${json_token} | cut -f4 -d\\"`
                curl --silent --location --request POST "https://api.ge.com/healthcare/api/myworkshop/1/mwsGetDetails/MWSDocDetails?Name=${docid}&Revision=${revision}" --header "Authorization: Bearer \${token}" > result.json
                cat result.json
            """
        } else {
            sh """
                json_token=`curl --silent \
                   --request POST \
                   --data "client_id=GEHealth-7Be4Kf91fOu9bdw8VszNpG4G&client_secret=bcb698fc26a221ca97459594a814a472d0cf2bcf&scope=api&grant_type=client_credentials" https://fssfed.ge.com/fss/as/token.oauth2`
                token=`echo \${json_token} | cut -f4 -d\\"`
                curl --silent --location --request POST "https://api.ge.com/healthcare/api/myworkshop/1/mwsGetDetails/MWSPartDetails?Name=${docid}&Revision=${revision}" --header "Authorization: Bearer \${token}" > result.json
                cat result.json
            """
        }
        state = sh(returnStdout: true, script : 'cat result.json | awk -v RS=":" \'/Latest State/{getline;print $0}\' | cut -d, -f1 | sed \'s/\"//g\' | tr -d \'\\n\'')
    } catch(err) {
        println ('EXCEPTION-6 : '+err.getMessage())
        log_message("get_checksum ERROR:" + err.getMessage())
        return ''
    }  
    println ("MWS Latest state : "+state)
    return(state)
}

def get_checksum_info(filename) {
    def data = [:]
    def file_text = ''
    def temp
    file_text = sh returnStdout: true, script: "cat ${filename} || echo ''"
    file_text.split("\n").each {
    /* next 5 lines to be replaced with single regular expression later */
        temp = it.replace(" : ", ";")
        temp = temp.replace("[md5sum:", "")
        temp = temp.replace("[sha256:", "")
        temp = temp.replaceAll("]", "")
        temp = temp.replaceAll(" ", "")
        name = temp.split(";")[0].split("/")[-1]
        chksum = temp.split(";")[1]
        temp1 = temp.split(";")
        if(temp1.size() > 2) {
            chksum = temp.split(";")[1]
            sha256 = temp.split(";")[2]
        } else {
            sha256 = temp.split(";")[1]
            chksum = "-"
        }
        log_message("NAME:"+name+", chksum:"+chksum+"sha256:"+sha256, false)
        data.put(name,chksum+":"+sha256)
    }
    log_message(data.text, false)
    return (data)
}

def replace_folder_artifactory_url(url, repo, new_folder) {
    def url_components = url.split("/")
    def new_url = ''
    def index = url_components.findIndexOf { it == repo }
    if(index < 0) {
        println("ERROR: repo name not found in artifactory URL")
        return ''
    }
    for(i=0;i<=index;i++) {
        new_url = new_url + url_components[i] + "/"
    }
    new_url = new_url +  new_folder + "/" + url_components[-1]
    println("new_url:-"+new_url)
    return new_url
}

def log_message(String msg, email_content=false) {
    new_time = get_date_time()
    LOG_CONTENT = LOG_CONTENT + "\n" + new_time + " : " + msg
    if(email_content) {
        EMAIL_CONTENT = EMAIL_CONTENT + "<br>" + msg
    }
}

def print_binary_checksums() {
    report = '<table border=1>'
    report = report + System.out.sprintf("<tr><th>SW Binary</th><th>Docker Image</th><th>Sha256/md5sum</th></tr>")
    def res = "No Binary exists !"
    if (fileExists("shaDockerValue_release.yaml") && fileExists("shaDockerValue_prod_release.yaml")) {
        rel_file = readFile  (file: "shaDockerValue_release.yaml")
        prod_file = readFile (file: "shaDockerValue_prod_release.yaml")
        rel = [:]
        prod = [:]

        rel = txt_to_map(rel_file.toString())
        prod = txt_to_map (prod_file.toString())

        res = "All binaries sha256/md5sum compared and OK"
        rel.each {
            report = report + System.out.sprintf("<tr><td>"+it.key+"</td><td>"+it.value[0]+"</td><td>REL: "+it.value[1]+"</td></tr>"+"<tr><td></td><td>"+prod[it.key][0]+"</td><td>PROD: "+prod[it.key][1]+"</td></tr>")
            if (it.value[1] != prod[it.key][1]) {
                res = "ERROR : Binaries sha256 compared and there is a difference"
            }
        }
    }
    if (fileExists("checksum_int_release.yaml") && fileExists("checksum_prod_release.yaml")) {
        rel_file = readFile  (file: "checksum_int_release.yaml")
        prod_file = readFile (file: "checksum_prod_release.yaml")
        rel = [:]
        prod = [:]
        rel = txt_to_map(rel_file.toString())
        prod = txt_to_map (prod_file.toString())
        if(res == "No Binary exists !") {
            res = "All binaries sha256/md5sum compared and OK"
        }
        println(res)
        rel.each {
            if(prod[it.key]) {
                report = report + System.out.sprintf("<tr><td>"+it.key+"</td><td>"+it.value[0]+"</td><td>REL: "+it.value[1]+"</td></tr>"+"<tr><td></td><td>"+prod[it.key][0]+"</td><td>PROD: "+prod[it.key][1]+"</td></tr>")
            } else {
                report = report + System.out.sprintf("<tr><td>"+it.key+"</td><td>"+it.value[0]+"</td><td>REL: "+it.value[1]+"</td></tr>"+"<tr><td></td><td></td><td>PROD: </td></tr>")
            }
            if (prod[it.key] == null  || it.value[1] != prod[it.key][1]) {
                res = "ERROR : Binaries sha256 compared and there is a difference"
            } else {
                println("all good for "+it.value[0])
            }
        }
    }
    println("done.....")
    report = report + "</table>"
    report = report + "<br>" + res
    return (report)
}

def txt_to_map(str) {
    def my_map = [:]
    def my_key = ''
    def docker = ''
    def sha256 = ''
    temp = str.split("\n")
    temp.each {
        if(it.trim() != '') {
            my_key = it.split(":")[0].trim()
            docker = it.split(' ')[0].split(":")[1].trim()+":"+it.split(' ')[0].split(":")[2].trim()
            sha256 = it.split("=")[-1].trim()
            my_map[my_key] = [docker, sha256]
        }
    }
    return my_map
}
