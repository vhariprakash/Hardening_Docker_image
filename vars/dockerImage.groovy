def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	def app
	println("In dockerImage module")
	try {
		//build docker image
		echo "REPO: " + "${config.dockerRepo}"
		echo "PROJECT:" + "${config.project_docker}"
		echo "DOCKERNAME:" + "${config.dockerName}"
		echo "config.dockerTag : ${config.dockerTag}"
		echo "config.dockerDirectory: ${config.dockerDirectory}"
		echo "artifactoryUrl : + ${config.artifactoryUrl}"
		if(config.dockerDirectory){
			app = docker.build("${config.artifactoryUrl}"+"/"+"${config.dockerRepo}"+"/"+"${config.project_docker}"+"/"+"${config.dockerName}", "${config.dockerDirectory}")
		}
		else{
			app = docker.build("${config.artifactoryUrl}"+"/"+"${config.dockerRepo}"+"/"+"${config.project_docker}"+"/"+"${config.dockerName}")
		}
		// push image to registry
		docker.withRegistry("https://${config.artifactoryUrl}", config.cred_id) {
			app.push("${config.dockerTag}")
			app.push("latest")
		}
	}
	catch(err) {
		echo 'Failed to send create docker image '+ err
		throw err
	}
}
