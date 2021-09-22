// imports
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.*
import hudson.util.Secret
import jenkins.model.Jenkins


def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

// parameters
    def jenkinsKeyUsernameWithPasswordParameters = [
      description:  'Created via pipeline',
      id:           'script_access']
      
    jenkinsKeyUsernameWithPasswordParameters.put ('userName', config.username)
    jenkinsKeyUsernameWithPasswordParameters.put ('secret', config.password)

    // get Jenkins instance
    Jenkins jenkins = Jenkins.getInstance()
    
    // get credentials domain
    def domain = Domain.global()
    
    // define Bitbucket secret
    def jenkinsKeyUsernameWithPassword = new UsernamePasswordCredentialsImpl(
      CredentialsScope.GLOBAL,
      jenkinsKeyUsernameWithPasswordParameters.id,
      jenkinsKeyUsernameWithPasswordParameters.description,
      jenkinsKeyUsernameWithPasswordParameters.userName,
      jenkinsKeyUsernameWithPasswordParameters.secret
    )    
    
    // get credentials store
    def store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
    
    // add credential to store
    store.addCredentials(domain, jenkinsKeyUsernameWithPassword)
    
    // save to disk
    jenkins.save()
}


