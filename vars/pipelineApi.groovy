#!groovy

def execute(Map configMap) {
    echo "configMap: ${configMap}"
    application = configMap["application"]
    stageMap = []
    switch(application) {
        case 'lib':
            stageMap = [
                "Clone" : true,
                "MavenBuild" : true,
                "Push to Nexus" : true,
            ]
            break
        case 'service':
            stageMap = [
                "Clone" : true,
                "MavenBuild" : true,
                "Push to Harbor" : true,
                "Deploy to Kubernetes" : true,
            ]
            break
        case 'frontend':
            stageMap = [
                "Clone" : true,
                "NpmBuild" : true,
                "Push to Harbor" : true,
                "Deploy to Kubernetes" : true,
            ]
            break
        default:
            error "application pipeline : Invalid application, ${application}"
            break
    }

    echo "Executing pipeline with the following stages, ${stageMap}"

    pipeline {
        environment {
            // This can be nexus3 or nexus2
            NEXUS_VERSION = "nexus3"
            // This can be http or https
            NEXUS_PROTOCOL = "http"
            // Where your Nexus is running
            NEXUS_URL = "127.0.0.1:8081"
            // Repository where we will upload the artifact
            NEXUS_REPOSITORY = "maven-releases"
            // Jenkins credential id to authenticate to Nexus OSS
            NEXUS_CREDENTIAL_ID = "nexus-credentials"

            HARBOR_URL = "192.168.86.43:9080/library"
            HARBOR_CREDENTIAL_ID = "harbor-credentials"
        }

        stages {
            stage("Clone") {
                steps {
                    echo "Clone..."
                }
            }
        }
    }
}
