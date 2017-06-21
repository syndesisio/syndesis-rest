def mavenVersion='3.3.9'
def GITHUB_OAUTH_CLIENT_ID
def GITHUB_OAUTH_CLIENT_SECRET

node('master') {
    GITHUB_OAUTH_CLIENT_ID = env.GITHUB_OAUTH_CLIENT_ID
    GITHUB_OAUTH_CLIENT_SECRET = env.GITHUB_OAUTH_CLIENT_SECRET
}

slave {
    withOpenshift {
            //Comment out until pvc issues are resolved
            //withMaven(mavenImage: "maven:${mavenVersion}", serviceAccount: "jenkins", mavenRepositoryClaim: "m2-local-repo", mavenSettingsXmlSecret: 'm2-settings') {
              withMaven(mavenImage: "maven:${mavenVersion}", serviceAccount: "jenkins", mavenSettingsXmlSecret: 'm2-settings', envVars: [
                containerEnvVar(key: 'GITHUB_OAUTH_CLIENT_ID', value: GITHUB_OAUTH_CLIENT_ID),
                containerEnvVar(key: 'GITHUB_OAUTH_CLIENT_SECRET', value: GITHUB_OAUTH_CLIENT_SECRET)
              ]) {
                inside {
                    def testingNamespace = generateProjectName()

                    checkout scm

                    stage 'Build'
                    container(name: 'maven') {
                        sh "mvn clean install fabric8:build -Pci -Duser.home=/home/jenkins"
                    }

                    stage 'System Tests'
                    test(component: 'syndesis-rest', namespace: "${testingNamespace}", serviceAccount: 'jenkins')
                 }

        }
    }
}
