def buildAndTestProtocolParser() {
    sh """
        set -ex
        rm -rf build testing/.tmp testing/.btest.failed.dat

        REPOTYPE=\$(cat .repotype)

        case "\$REPOTYPE" in
            BINPAC)
                ./configure
                cd build
                make
                cd ..
            ;;
            SPICY)
                mkdir -p build
                cd build
                cmake ..
                cmake --build . -j 2
                cd ..
                ;;
            ZEEKONLY)
                # No build step needed for Zeek-only parsers - just run btest
                ;;
            esac

            cd testing
            btest -d
    """
}

def call() {
    pipeline {
        agent {
            label 'rhel9'
        }

        options {
            skipDefaultCheckout()
        }

        stages {
            stage('Discover Versions') {
                steps {
                    script {
                        withCredentials([string(credentialsId: 'jjrush-gh-pat', variable: 'CR_PAT')]) {
                            def versionsOutput = sh(
                                script: """
                                    curl -s -H "Accept: application/vnd.github+json" \
                                        -H "Authorization: Bearer \$CR_PAT" \
                                        https://api.github.com/users/mmguero/packages/container/zeek/versions | \
                                    jq -r '.[].metadata.container.tags[]' | \
                                    grep -oE 'v[0-9]+\\.[0-9]+\\.[0-9]+' | \
                                    sed 's/^v//' | \
                                    sort -V -u | \
                                    {
                                        readarray -t VERS;
                                        M=\$(printf '%s\\n' "\${VERS[@]}" | awk -F. '{print \$1}' | sort -n | tail -1);
                                        for B in "\$M" \$((M-1)); do
                                            printf '%s\\n' "\${VERS[@]}" | grep -E "^\${B}\\.0\\.[0-9]+\$" | sort -V | tail -1;
                                        done | sed '/^\$/d';
                                    }
                                """,
                                returnStdout: true
                            ).trim()
                            
                            def ltsVersions = versionsOutput ? versionsOutput.split('\n').findAll { it } : []
                            
                            if (ltsVersions.size() < 2) {
                                error("Expected 2 LTS versions but got: ${ltsVersions}")
                            }
                            
                            // Add 'latest' to the beginning
                            def allVersions = ['latest'] + ltsVersions
                            env.ZEEK_VERSIONS = allVersions.join(',')
                            echo "Testing against Zeek versions: ${env.ZEEK_VERSIONS}"
                        }
                    }
                }
            }

            stage('Pull Docker Images') {
                steps {
                    script {
                        def versions = env.ZEEK_VERSIONS.split(',')
                        def compilers = ['clang', 'gcc']
                        
                        versions.each { version ->
                            compilers.each { compiler ->
                                def tag = version == 'latest' ? "${version}-${compiler}" : "v${version}-${compiler}"
                                sh "docker pull ghcr.io/mmguero/zeek:${tag}"
                            }
                        }
                    }
                }
            }
            
            stage('Remove Old/Dangling Docker Images') {
                steps {
                    script {
                        sh 'docker container prune -f || true'
                        sh 'docker volume prune -f || true'
                        sh 'docker network prune -f || true'
    
                        // Clean up old Zeek images that aren't in our current LTS set
                        sh """
                            # Build the list of images to keep (with both compilers)
                            KEEP_IMAGES=""
                            for version in \$(echo '${env.ZEEK_VERSIONS}' | tr ',' ' '); do
                                if [ "\$version" = "latest" ]; then
                                    KEEP_IMAGES="\$KEEP_IMAGES ghcr.io/mmguero/zeek:latest-clang ghcr.io/mmguero/zeek:latest-gcc"
                                else
                                    KEEP_IMAGES="\$KEEP_IMAGES ghcr.io/mmguero/zeek:v\${version}-clang ghcr.io/mmguero/zeek:v\${version}-gcc"
                                fi
                            done
    
                            # Remove all mmguero/zeek images except the ones we want to keep
                            docker images ghcr.io/mmguero/zeek --format "{{.Repository}}:{{.Tag}}" | while read image; do
                                if ! echo "\$KEEP_IMAGES" | grep -q "\$image"; then
                                    echo "Removing old image: \$image"
                                    docker rmi "\$image" || true
                                fi
                            done
                        """
                        sh  'docker image prune -f' // finally remove any dangling images
                    }
                }
            }
            
            stage('Build and Test Matrix') {
                steps {
                    script {
                        def versions = env.ZEEK_VERSIONS.split(',')
                        def compilers = ['clang', 'gcc']
                        
                        versions.each { version ->
                            stage("Build & Test Zeek ${version}") {
                                def compilerStages = [:]
                                
                                compilers.each { compiler ->
                                    def tag = version == 'latest' ? "${version}-${compiler}" : "v${version}-${compiler}"
                                    def variant = "${version}-${compiler}"
                                    
                                    compilerStages[compiler] = {
                                        docker.image("ghcr.io/mmguero/zeek:${tag}").inside('--user root --entrypoint=') {
                                            dir(variant) {
                                                checkout scm
                                                buildAndTestProtocolParser()
                                            }
                                        }
                                    }
                                }
                                
                                parallel compilerStages
                            }
                        }
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    echo "Cleaning up build \"${WORKSPACE}\" dir"
                }
                // Clean up Jenkins job directories
                deleteDir()
                script{
                    // Clean the @tmp directory if it exists
                    sh """
                        if [[ -d "${WORKSPACE}@tmp" ]]; then
                            echo "Cleaning up dangling \"${WORKSPACE}@tmp\" dir"
                            rm -rf "${WORKSPACE}@tmp"
                        fi
                    """
                }
            }
        }
    }
}
