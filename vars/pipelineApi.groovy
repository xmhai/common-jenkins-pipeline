//#!groovy

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
        agent any
        stages {
            stage('Clone') {
                steps {
                    echo "Clone..."
                }
            }
        }
    }
}
