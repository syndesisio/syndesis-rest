def mavenVersion='3.3.9'
node {
    inNamespace(cloud:'openshift', prefix: 'e2e') {

	setupMaven(namespace: "${KUBERNETES_NAMESPACE}")
	
        env = []
        env.add(containerEnvVar(key:'NAMESPACE_USE_EXISTING', value: "${KUBERNETES_NAMESPACE}"))
        env.add(containerEnvVar(key:'NAMESPACE_DESTROY_ENABLED', value: "false"))
        env.add(containerEnvVar(key:'NAMESPACE_CLEANUP_ENABLED', value: "false"))
        env.add(containerEnvVar(key:'ENV_INIT_ENABLED', value: "false"))

        stage 'Building'
        slave {
            withOpenshift {
                    withMaven(mavenImage: "maven:${mavenVersion}",
                    envVar: env,
                    serviceAccount: "builder", mavenRepositoryClaim: "m2-local-repo", mavenSettingsXmlSecret: 'm2-settings') {
                        inside {
    				checkout scm

                                stage 'Building'
                            	container(name: 'maven') {
                                	sh "mvn clean install fabric8:build -U -Pci -Duser.home=/home/jenkins -Dfabric8.namespace=${KUBERNETES_NAMESPACE}"
                            	}

                                stage 'Prepare Environment'
				createEnvironment(cloud: 'openshift', name: "${KUBERNETES_NAMESPACE}",
                                    environmentSetupScriptUrl: 'https://raw.githubusercontent.com/syndesisio/syndesis-system-tests/master/src/test/resources/setup.sh',
                                    environmentTeardownScriptUrl: 'https://raw.githubusercontent.com/syndesisio/syndesis-system-tests/master/src/test/resources/teardown.sh',
                                    waitForServiceList: ['syndesis-rest', 'syndesis-ui', 'syndesis-keycloak', 'syndesis-verifier'],
                                    waitTimeout: 600000L,
                                    namespaceDestroyEnabled: false,
                                    namespaceCleanupEnabled: false)

                                stage 'System Tests'
                                def testingNamespace = currentNamespace()
                                test(component: 'syndesis-pipeline-library', envInitEnabled: false, namespace: "${KUBERNETES_NAMESPACE}", serviceAccount: 'jenkins')
                        }
                    }
            }
        }
    }
}
