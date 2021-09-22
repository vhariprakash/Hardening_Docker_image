def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
     try {
        env.http_proxy= 'http://http-proxy.health.ge.com:88'
        env.https_proxy= 'https://http-proxy.health.ge.com:88'
        if(env.gitlabBranch == 'master') {
            string dockerRepo = 'docker-snapshot-eis'
            def app = docker.build("${dockerRepo}"+"/"+"${config.project_docker}"+"/"+"${config.dockerName}")
            docker.withRegistry('https://hc-eu-west-aws-artifactory.cloud.health.ge.com', 'gip_sv01_artifactory_eu') {
                app.push("${config.masterDockerTag}")
                app.push("latest")
            }
        }  	 
        if (!(env.gitlabBranch == 'master')) {	
            string dockerRepo = 'docker-eis-dev'		
            def app = docker.build("${dockerRepo}"+"/"+"${config.project_docker}"+"/"+"${config.dockerName}")
            docker.withRegistry('https://hc-eu-west-aws-artifactory.cloud.health.ge.com', 'gip_sv01_artifactory_eu') {
                app.push("${env.gitlabBranch}-${config.featureDockerTag}") 
            }
        }
    } catch(err) {
        echo 'Failed to send create docker image '+ err
        throw err
    }  
}