def buildAndTestProtocolParser() {
    sh """
        set -ex
        rm -rf build
        if [ -f ./configure ]; then
            ./configure
            cd build
            make
        else
            mkdir build
            cd build
            cmake ..
            cmake --build . -j \$(nproc)
        fi
        cd ../testing
        btest
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

            stage('Build and Test Matrix') {
                steps {
                    script {
                        def versions = env.ZEEK_VERSIONS.split(',')
                        def compilers = ['clang', 'gcc']
                        def parallelStages = [:]
                        
                        versions.each { version ->
                            compilers.each { compiler ->
                                def tag = version == 'latest' ? "${version}-${compiler}" : "v${version}-${compiler}"
                                def variant = "${version}-${compiler}"
                                
                                parallelStages[variant] = {
                                    docker.image("ghcr.io/mmguero/zeek:${tag}").inside('--user root --entrypoint=') {
                                        dir(variant) {
                                            checkout scm
                                            buildAndTestProtocolParser()
                                        }
                                    }
                                }
                            }
                        }
                        
                        parallel parallelStages
                    }
                }
            }
        
        post {
            always {
                script {
                    sh 'docker container prune -f || true'
                    sh 'docker volume prune -f || true'
                    sh 'docker network prune -f || true'
                    sh 'docker image prune -f || true'
                }
            }
        }
    }
}