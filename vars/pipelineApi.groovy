#!groovy

def call(Map configMap) {
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
                    script {
                        echo "Clone..."
                    }
                }
            }
            stage("MavenBuild") {
                when {
                    expression {stageMap["MavenBuild"] }
                }                
                steps {
                    script {
                        echo "Maven Build..."
                        sh "mvn clean install -DskipTests=true"
                    }
                }
            }
            stage("NpmBuild") {
                when {
                    allOf {
                        expression {stageMap["NpmBuild"] }
                        changeset 'src/**'
                    }                
                }
                steps {
                    script {
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
            stage("Push to Nexus") {
                when {
                    expression {stageMap["Push to Nexus"] }
                }                
                steps {
                    script {
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
                                filesByGlob = findFiles(glob: "target/*.${pom.packaging}");
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
                when {
                    expression {stageMap["Push to Harbor"] }
                }                
                steps {
                    script {
                        echo "Push to Harbor..."
                        def pomExists = fileExists "pom.xml"
                        def dockerFileExists = fileExists "Dockerfile"
                        if (pomExists && dockerFileExists) {
                            pom = readMavenPom file: "pom.xml";
                            echo "${pom.artifactId}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}"

                            if ("${pom.packaging}" == "jar") {
                                // Find built artifact under target folder
                                filesByGlob = findFiles(glob: "target/*.${pom.packaging}");
                                boolean exists = filesByGlob.length > 0

                                if (exists) {
                                    // Print some info from the artifact found
                                    echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"

                                    echo "build docker image..."
                                    def dockerImage = docker.build("${HARBOR_URL}/${pom.artifactId}", ".");
                                    echo "push docker image to harbor..."
                                    docker.withRegistry("http://${HARBOR_URL}", "${HARBOR_CREDENTIAL_ID}") {
                                        dockerImage.push();
                                    }
                                }
                            }
                        } else {
                            echo "pom.xml or Dockerfile doesn't exist"
                        }
                    }
                }
            }
            stage("Deploy to Kubernetes") {
                when {
                    expression {stageMap["Deploy to Kubernetes"] }
                }                
                steps {
                    echo "Deploy to Kubernetes..."
                    k3s kubectl delete -f k8s-deploy.yaml
                    k3s kubectl apply -f k8s-deploy.yaml
                }
            }
        }
    }
}
