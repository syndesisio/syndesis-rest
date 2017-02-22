@Library('github.com/redhat-ipaas/ipaas-pipeline-library@master')
def mavenVersion='3.3.9'

def date = new java.text.SimpleDateFormat("yyyyMMddHHmm").format(new Date())
def versionSuffix = "${date}-${env.BUILD_NUMBER}"


openshiftTemplate {
    mavenNode(mavenImage: "maven:${mavenVersion}") {
        def pom = readMavenPom(file: 'pom.xml')
        def version = pom.version.replaceAll("SNAPSHOT", "versionSuffix")

        stage 'Build'
        container(name: 'maven') {
            sh "mvn clean install"
        }

        stage 'System Tests'
        runSystemTests(component: 'ipaas-rest', version: "${version}")

        stage 'Deploy'
        updateComponent(component: 'ipaas-rest', version: "${version}", namespace: 'ipaas-staging')
    }
}
