#!groovy

def build(Map configMap) {
    application = configMap.get("application":"")
    switch(application) {
        case 'lib':
            def stageMap = [
                "Clone" : true,
                "MavenBuild" : true,
                "Push to Nexus" : true,
            ]
            break
        case 'service':
            def stageMap = [
                "Clone" : true,
                "MavenBuild" : true,
                "Push to Harbor" : true,
                "Deploy to Kubernetes" : true,
            ]
            break
        case 'frontend':
            def stageMap = [
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
                script {
                    echo "Clone..."
                }
            }
            stage("MavenBuild") {
                script {
                    if (stageMap["MavenBuild"]) {
                        echo "Maven Build..."
                        sh "mvn clean install -DskipTests=true"
                    }
                }
            }
            stage("NpmBuild") {
                script {
                    if (stageMap["NpmBuild"]) {
                        echo "Npm Build..."
                        sh "npm install"
                        sh "npm run build"
                        
                        echo "build frontend docker image..."
                        def frontendImage = docker.build("${HARBOR_URL}/pfa-frontend", ".");
                        echo "push frontend image to harbor..."
                        docker.withRegistry("http://${HARBOR_URL}", "${HARBOR_CREDENTIAL_ID}") {
                            frontendImage.push();
                    }
                }
            }
            stage("Push to Nexus") {
                script {
                    if (stageMap["Push to Nexus"]) {
                        echo "Push to Nexus..."
                        def pomExists = fileExists "pom.xml"
                        if (pomExists) {
                            pom = readMavenPom file: "pom.xml";
                            echo "${pom.artifactId}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}"

                            if ("${pom.packaging}" == "pom") {
                                    nexusArtifactUploader(
                                        nexusVersion: NEXUS_VERSION,
                                        protocol: NEXUS_PROTOCOL,
                                        nexusUrl: NEXUS_URL,
                                        groupId: pom.groupId,
                                        version: pom.version,
                                        repository: NEXUS_REPOSITORY,
                                        credentialsId: NEXUS_CREDENTIAL_ID,
                                        artifacts: [
                                            [artifactId: pom.artifactId, classifier: '', file: "pom.xml", type: "pom"]
                                        ]
                                    );
                            }

                            if ("${pom.packaging}" == "jar") {
                                // Find built artifact under target folder
                                filesByGlob = findFiles(glob: "${f.name}/target/*.${pom.packaging}");
                                boolean exists = filesByGlob.length > 0

                                if (exists) {
                                    // Print some info from the artifact found
                                    echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"
                                    // Extract the path from the File found
                                    artifactPath = filesByGlob[0].path;

                                    nexusArtifactUploader(
                                        nexusVersion: NEXUS_VERSION,
                                        protocol: NEXUS_PROTOCOL,
                                        nexusUrl: NEXUS_URL,
                                        groupId: pom.groupId,
                                        version: pom.version,
                                        repository: NEXUS_REPOSITORY,
                                        credentialsId: NEXUS_CREDENTIAL_ID,
                                        artifacts: [
                                            [artifactId: pom.artifactId, classifier: '', file: artifactPath, type: pom.packaging], 
                                            [artifactId: pom.artifactId, classifier: '', file: "pom.xml", type: "pom"]
                                        ]
                                    );
                                }
                            }
                        } else {
                            echo "${f.name}/pom.xml doesn't exist"
                        }
                    }
                }
            }
            stage("Push to Harbor") {
                script {
                    if (stageMap["Push to Harbor"]) {
                        echo "Push to Harbor..."
                    }
                }
            }
            stage("Deploy to Kubernetes") {
                script {
                    if (stageMap["Deploy to Kubernetes"]) {
                        echo "Deploy to Kubernetes..."
                    }
                }
            }
        }
    }
}