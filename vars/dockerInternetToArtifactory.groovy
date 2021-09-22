def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def sourceImage = "${config.source_image}"
    def destinationImage = "${config.destination_image}"
    def sourceTag = "${config.source_docker_tag}"
    def destinationTag = "${config.destination_image_tag}"
    def dockerImageResponseCode = checkIfImageExists (destinationImage, destinationTag)
    echo "response code is" + dockerImageResponseCode
    if (!dockerImageResponseCode.equals('200')){
    promoteDocker(sourceImage, sourceTag, destinationImage, destinationTag)
    }
    else{
        println 'Image already exists with the same tag'
    }
}

def promoteDocker(sourceImage, sourceTag, destinationImage, destinationTag){
    sh """
    docker pull ${sourceImage}:${sourceTag}
    docker tag ${sourceImage}:${sourceTag} ${destinationImage}:${destinationTag}
    docker push ${destinationImage}:${destinationTag}
    """
}

def checkIfImageExists(dockerImage, tag){
    println tag
    imageUrl="https://" + dockerImage.split('/',2)[0] + "/artifactory/" + dockerImage.split('/',2)[1] + '/' + tag + '/'
    println "the image url to validate is ${imageUrl}"
    dockerImageResponseCode = sh returnStdout: true, script: """curl -s -o /dev/null -I -w "%{http_code}" ${imageUrl}"""
    return dockerImageResponseCode
}

