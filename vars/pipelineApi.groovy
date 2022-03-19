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
        agent any
        stages {
            stage("Clone") {
                steps {
                    script {
                        echo "Clone..."
                    }
                }
            }
            stage("MavenBuild") {
                steps {
                    script {
                        if (stageMap["MavenBuild"]) {
                            echo "Maven Build..."
                            sh "mvn clean install -DskipTests=true"
                        }
                    }
                }
            }
            stage("NpmBuild") {
                steps {
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
                }
            }
            stage("Push to Nexus") {
                steps {
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
            }
            stage("Deploy to Kubernetes") {
                steps {
                    script {
                        if (stageMap["Deploy to Kubernetes"]) {
                            echo "Deploy to Kubernetes..."
                        }
                    }
                }
            }
            stage("Push to Harbor") {
                steps {
                    script {
                        if (stageMap["Push to Harbor"]) {
                            echo "Push to Harbor..."
                        }
                    }
                }
            }
        }
    }
}
