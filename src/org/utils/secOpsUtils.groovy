package org.utils;


def docker_hardening(image_name, image_tag, repo, credentialsId, desired_score,  pre_step, post_step)
{
    println ("docker_hardening [start]")
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    ret_code = generalStageUtils.exec_hook(pre_step)
    if(ret_code != 0) {
        error("docker_hardening_prestep failed")
    }
    contArgs = "-v /dockerspace:/dockerspace:rw "
    dockerImage = "${image_name}:${image_tag}"
    def dsl_branch_name = generalStageUtils.getDSLBranchName()
    dir(env.WORKSPACE) {
        git branch: "${dsl_branch_name}", changelog: false, poll: false, url: "${repo}", credentialsId: "${credentialsId}"
    }
    if(desired_score) {
       score = desired_score.toInteger()
    } else {
        score = 14  // default score from CyberLab team
    }
    sh """
        docker run -d --name=java-app -p 8028:8028  "$dockerImage"
        cd resources/docker-bench
        chmod a+x ./build.sh && ./build.sh $score
        cd -
    """
/***
    withDockerContainer(args: "$contArgs", image: "$dockerImage") {
        sh """
            cd resources/docker-bench
            chmod a+x ./build.sh && ./build.sh
            cd -
        """
    }
***/
    ret_code = generalStageUtils.exec_hook(post_step)
    if(ret_code != 0) {
        error("docker_hardening_prestep failed")
    }
    println ("docker_hardening [end]")
}
//End of file
