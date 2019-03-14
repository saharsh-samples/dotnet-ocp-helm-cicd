/**
 * Installs Helm to designated Openshift cluster
 *
 * Params:
 *   - insecureCluster   : Set to true if cluster URL is HTTPS but verification
 *                         should be skipped
 *   - tillerNs          : Openshift project where Tiller server is deployed
 *   - clusterUrl        : Openshift Cluster URL
 *   - clusterAuthToken  : Token used to authenticate to the Openshift Cluster.
 *                         This will be set in the `kubectl` context
 *   - namespace         : Openshift project where application will be deployed
 *   - deployBuildConfig : Set to 'true' to deploy build config. 'false'
 *                         otherwise
 *   - appVersion        : Version of application to pass to the application
 *                         (application specific)
 *   - routeHost         : route Host to set given target environment
 *   - imageTag          : Tag of application container image to pull
 *   - imagePullPolicy   : Image pull policy to set in application's deployment
 *                         template
 *   - releaseName       : Helm Release to use
 *   - chartDirectory    : Location of directory in repo containing Helm charts
 *                         (relative to repo top level) 
 */
def helmInstall(insecureCluster, tillerNs, clusterUrl, clusterAuthToken, namespace, deployBuildConfig, appVersion, routeHost, imageTag, imagePullPolicy, releaseName, chartDirectory) {

    if(insecureCluster == 'true') {
        clusterUrl = clusterUrl + ' --insecure-skip-tls-verify'
    }

    sh '''

    export HOME="`pwd`"
    export TILLER_NAMESPACE="''' + tillerNS + '''"

    kubectl config set-cluster development --server=''' + clusterUrl + '''
    kubectl config set-credentials jenkins --token="''' + clusterAuthToken + '''"
    kubectl config set-context helm --cluster=development --namespace="''' + namespace + '''" --user=jenkins
    kubectl config use-context helm

    helm upgrade --install \
        --namespace "''' + namespace + '''" \
        --set build.deploy="''' + deployBuildConfig + '''" \
        --set app.version="''' + appVersion + '''" \
        --set route.host="''' + routeHost + '''" \
        --set image.tag="''' + imageTag + '''" \
        --set image.pullPolicy="''' + imagePullPolicy + '''" \
        ''' + releaseName + ''' \
        ''' + chartDirectory
}

/**
 * Wait for deployments to finish
 *
 * Params:
 *   - dc : The deployment object on which to wait
 */
def waitForDeploymentToFinish(dc) {

    def keepTrying              = true
    def deploymentTimeoutMins   = 5
    def deploymentTimeoutMillis = deploymentTimeoutMins * 60 * 1000
    def tryTill                 = System.currentTimeMillis() + deploymentTimeoutMillis

    while (keepTrying) {

        try {

            // Wait till deployment completes
            dc.rollout().status('--request-timeout=' + deploymentTimeoutMins + 'm')
            keepTrying = false

        } catch (err) {

            if (System.currentTimeMillis() < tryTill) {

                // ignore errors till time out
                echo "Ignoring errors till timeout: ${err}"

            } else {

                // bubble up the error if time out has occurred
                throw err

            }

        }

    }
}

/**
 * Build a new image tag using the specified build config
 *
 * Params:
 *   - insecureCluster      : Set to true if cluster URL is HTTPS but verification
 *                            should be skipped
 *   - clusterUrl           : Openshift Cluster URL
 *   - clusterAuthToken     : Token used to authenticate to the Openshift Cluster.
 *                            This will be set in the `kubectl` context
 *   - namespace            : Openshift project where the build will take place
 *   - buildConfigName      : Name of the build configuration to execute
 *   - contextDirectory     : Relative directory path from git repository to pass
 *                            to build execution as the build context
 */
def buildImageStreamTag(insecureCluster, clusterUrl, clusterAuthToken, namespace, buildConfigName, contextDirectory) {

    // If connecting to a server over TLS whose certification should be ignored,
    // use the custom 'insecure' protocol
    if(insecureCluster == 'true') {
        clusterUrl = clusterUrl.replaceFirst('https', 'insecure')
    }

    // build new tag using build configuration
    openshift.withCluster(clusterUrl, clusterAuthToken) {
        openshift.withProject(namespace) {
            result = openshift.startBuild(buildConfigName, '--from-dir=' + contextDirectory, '--follow')
            echo "${result.out}"
        }
    }

}

/**
 * Import an image stream tag. Optionally, after the import, wait for deployment
 * to successfully finish
 *
 * Params:
 *   - insecureCluster      : Set to true if cluster URL is HTTPS but verification
 *                            should be skipped
 *   - clusterUrl           : Openshift Cluster URL
 *   - clusterAuthToken     : Token used to authenticate to the Openshift Cluster.
 *                            This will be set in the `kubectl` context
 *   - namespace            : Openshift project to use as the context
 *   - sourceImageStreamTag : Source image stream tag to import
 *   - targetImageStreamTag : Destination image stream tag
 *   - deploymentConfigName : Name of the deployment configuration to watch. Leave
 *                            null, if no deployment to watch
 */
def importTagAndDeploy(insecureCluster, clusterUrl, clusterAuthToken, namespace, sourceImageStreamTag, targetImageStreamTag, deploymentConfigName) {

    // If connecting to a server over TLS whose certification should be ignored, use
    // the custom 'insecure' protocol
    if(insecureCluster == 'true') {
        clusterUrl = clusterUrl.replaceFirst('https', 'insecure')
    }

    openshift.withCluster(clusterUrl, clusterAuthToken) {
        openshift.withProject(namespace) {

            // import tag
            result = openshift.raw('tag', sourceImageStreamTag, targetImageStreamTag)
            echo "${result.out}"

            if (deploymentConfigName != null) {

                // deployment is triggered automatically. wait for it to finish
                def dc = openshift.selector('dc', deploymentConfigName)
                waitForDeploymentToFinish(dc)

            }
        }
    }

}

/**
 * REFERENCE CI/CD PIPELINE FOR KUBERNETES NATIVE .NET APPLICATION
 */
pipeline {

    parameters {

        // Application Properties
        string(name: 'appName', defaultValue: 'dotnet-ocp-helm-sample', description: 'Used as the base in Helm Release names')
        string(name: 'appDirectory', defaultValue: 'sample-dotnet-app', description: 'Relative path to .NET code and Dockerfile')
        string(name: 'helmChartDirectory', defaultValue: 'deployment', description: 'Relative path to Helm chart and templates')

        // Cluster Properties
        string(name: 'insecureCluster', defaultValue: 'true', description: 'Set to true if cluster URL is HTTPS but verification should be skipped')
        string(name: 'productionNamespace', defaultValue: 'sample-projects', description: 'Production namespace. Appended with -dev and -qa for those environments')
        string(name: 'tillerNS', defaultValue: 'tiller', description: 'Namespace on K8S cluster where tiller server is installed')
        string(name: 'devRouteHost', defaultValue: 'dotnet-ocp-helm-sample-sample-projects-dev.192.168.99.100.nip.io', description:'Route Host to set when deploying in Dev environment.')
        string(name: 'qaRouteHost', defaultValue: 'dotnet-ocp-helm-sample-sample-projects-qa.192.168.99.100.nip.io', description:'Route Host to set when deploying in QA environment.')
        string(name: 'prodRouteHost', defaultValue: 'dotnet-ocp-helm-sample-sample-projects.192.168.99.100.nip.io', description:'Route Host to set when deploying in Production environment.')

        // Jenkins Properties
        string(name: 'clusterAuthCredentialId', defaultValue: 'ocp-cluster-auth', description: 'ID of Jenkins credential containing OCP Cluster authentication for Helm deploys')
        string(name: 'gitCredentialId', defaultValue: 'git-auth', description: 'ID of Jenkins credential containing Git server username and password')
        string(name: 'confirmationTimeoutValue', defaultValue: '5', description: 'Integer indicating length of time to wait for manual confirmation')
        string(name: 'confirmationTimeoutUnits', defaultValue: 'DAYS', description: 'Time unit to use for CONFIRMATION_WAIT_VALUE')

        // Git Properties
        string(name: 'mainBranch', defaultValue: 'develop', description: 'Main branch of Git repostory. This is the source and destination of feature branches')
        string(name: 'releaseBranch', defaultValue: 'master', description: 'Release branch of Git repostory. Merges to this trigger releases (Git tags and version increments)')
    }

    environment {

        // Application Properties
        appName            = "${appName}"
        appDirectory       = "${appDirectory}"
        helmChartDirectory = "${helmChartDirectory}"
        helmChartFile      = "${helmChartDirectory + '/Chart.yaml'}"

        // Cluster Properties
        insecureCluster      = "${insecureCluster}"
        tillerNS             = "${tillerNS}"
        productionNamespace  = "${productionNamespace}"
        qaNamespace          = "${productionNamespace + '-qa'}"
        developmentNamespace = "${productionNamespace + '-dev'}"
        prodRouteHost        = "${prodRouteHost}"
        qaRouteHost          = "${qaRouteHost}"
        devRouteHost         = "${devRouteHost}"

        // Jenkins Properties
        clusterAuthCredentialId   = "${clusterAuthCredentialId}"
        gitCredentialId           = "${gitCredentialId}"
        confirmationTimeoutValue  = "${confirmationTimeoutValue}"
        confirmationTimeoutUnits  = "${confirmationTimeoutUnits}"

        // Git Properties
        mainBranch    = "${mainBranch}"
        releaseBranch = "${releaseBranch}"
    }

    // no default agent/pod to stand up
    agent none 

    stages {

        /**
         * STAGE - INITIALIZE
         *
         * The intention of this stage is to do any initialization that modifies the 
         * pipeline environment before running any of the other stages.
         */
        stage('Initialize') {

            agent any

            steps {

                // set build version from helm chart and current branch
                script {

                    // Read Helm Chart file line by line
                    readFile(helmChartFile).split('\r|\n').each({ line ->

                        // Look for line that starts with 'appVersion'
                        if(line.trim().startsWith("appVersion")) {

                            // Strip out everything on the line except the semantic version (i.e. #.#.#)
                            def version = line.replaceFirst(".*appVersion.*(\\d+\\.\\d+\\.\\d+).*", "\$1")

                            // If not on release branch, append branch name to semantic version
                            if(! releaseBranch.equals(BRANCH_NAME)) {
                                version = version + '-' + BRANCH_NAME
                                // feature branches may have the 'feature/branch-name' structure
                                // replace any '/' with '-' to keep version useable as image tag
                                version = version.replace('/', '-')
                            }

                            // Set version information to build environment
                            env.buildVersion         = version
                            // Set version + "git commit hash" information to environment
                            env.buildVersionWithHash = version + '-' + sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                        }
                    })
                }
            }
        }

        /**
         * STAGE - Feature branch build
         *
         * Uses saharshsingh/container-management:1.0 image to build container image
         */
        stage('Feature branch build') {

            when { not { anyOf { branch mainBranch; branch releaseBranch } } }

            // 'Feature branch build' agent pod template
            agent {
                kubernetes {
                    cloud 'openshift'
                    label 'buildah'
                    yaml """
apiVersion: v1
kind: Pod
spec:
    serviceAccountName: jenkins-privileged
    containers:
      - name: jnlp
        image: 'jenkinsci/jnlp-slave:alpine'
      - name: buildah
        image: 'saharshsingh/container-management:1.0'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
        securityContext:
          privileged: true
        volumeMounts:
          - mountPath: /var/lib/containers
            name: buildah-storage
    volumes:
      - name: buildah-storage
        emptyDir: {}
"""
                }
            }

            steps {

                // build (and optionally deliver) container image
                container('buildah') {
                    script {
                        sh 'buildah bud -t "${appName}:${buildVersion}" ${appDirectory}'
                    }
                }
            }
        }

        /**
         * STAGE - Deploy to Staging
         *
         * Only executes on main and release branch builds. Deploys to either 'Dev'
         * or 'QA' environment, based on whether main or release branch is being
         * built.
         */
        stage('Deploy to Staging') {

            when { anyOf { branch mainBranch; branch releaseBranch } }

            // 'Deploy to Staging' agent pod template
            agent {
                kubernetes {
                    cloud 'openshift'
                    label 'ocp-build-agent'
                    yaml """
apiVersion: v1
kind: Pod
spec:
    containers:
      - name: jnlp
        image: 'jenkinsci/jnlp-slave:alpine'
      - name: helm
        image: 'saharshsingh/helm:2.12.3'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
      - name: openshift-client
        image: 'openshift/origin-cli:v3.11.0'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
"""
                }
            }

            steps {

                withCredentials([usernamePassword(credentialsId: clusterAuthCredentialId, usernameVariable: 'clusterUrl', passwordVariable: 'token')]) {

                    script {

                        def namespace         = developmentNamespace
                        def routeHost         = devRouteHost
                        def imagePullPolicy   = 'Always'
                        def helmRelease       = appName + '-dev'

                        // if on release branch, override them for QA environment
                        if(releaseBranch.equals(BRANCH_NAME)) {
                            namespace         = qaNamespace
                            routeHost         = qaRouteHost
                            imagePullPolicy   = 'IfNotPresent'
                            helmRelease       = appName + '-qa'
                        }

                        // Update (or install if missing) all application related templates
                        container('helm') {
                            helmInstall(insecureCluster, tillerNS, clusterUrl, token, namespace, 'true', buildVersionWithHash, routeHost, buildVersion, imagePullPolicy, helmRelease, helmChartDirectory)
                        }

                        // build image and deploy
                        container('openshift-client') {

                            // build image tag
                            buildImageStreamTag(insecureCluster, clusterUrl, token, namespace, appName, appDirectory)

                            // move latest to point to built tag to trigger deployment
                            sourceTag      = appName + ':' + buildVersion
                            destinationTag = appName + ':latest'
                            importTagAndDeploy(insecureCluster, clusterUrl, token, namespace, sourceTag, destinationTag, appName)
                        }

                    }
                }
            }
        }

        /**
         * STAGE - Tag and Increment Version
         *
         * Only executes on release branch builds. Creates a Git tag on current commit
         * using version from Helm chart in repository. Also, checks out the HEAD of
         * main branch and increments the patch component of the Helm chart version
         */
        stage('Tag and Increment Version') {

            when { branch releaseBranch }

            agent any

            steps {
                withCredentials([usernamePassword(credentialsId: gitCredentialId, usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh '''

                    # Configure Git for tagging/committing and pushing
                    ORIGIN=$(echo "$(git config remote.origin.url)" | sed -E "s~(http[s]*://)~\\1${USER}@~")
                    git config --global user.email "jenkins@email.com"
                    git config --global user.name "Jenkins"
                    printf "exec echo \\"${PASS}\\"" > $HOME/askgitpass.sh
                    chmod a+x $HOME/askgitpass.sh

                    # Tag Release Candidate
                    TAG="v${buildVersion}"
                    git tag -a "$TAG" -m "Release $TAG created and delivered"
                    GIT_ASKPASS=$HOME/askgitpass.sh git push "$ORIGIN" "$TAG"

                    # Increment version on main branch
                    git checkout ${mainBranch}
                    git reset --hard origin/${mainBranch}
                    new_version="$(echo "${buildVersion}" | cut -d '.' -f 1,2).$(($(echo "${buildVersion}" | cut -d '.' -f 3) + 1))"
                    sed -i -E s/"appVersion.*[0-9]+\\.[0-9]+\\.[0-9]+"/"appVersion: $new_version"/ ${helmChartFile}
                    git commit -a -m "Updated app version from ${buildVersion} to $new_version"
                    GIT_ASKPASS=$HOME/askgitpass.sh git push "$ORIGIN" ${mainBranch}
                    '''
                }
            }

        }

        /**
         * STAGE - Confirm Promotion to Production
         *
         * Pipeline halts for configured amount of time and waits for someone to click Proceed or Abort.
         */
        stage('Confirm Promotion to Production') {

            when { branch releaseBranch }

            steps {
                timeout(time : Integer.parseInt(confirmationTimeoutValue), unit : confirmationTimeoutUnits) {
                    input "Promote ${appName} version ${buildVersionWithHash} to production?"
                }
            }

        }

        /**
         * STAGE - Promote to Production
         *
         * Once promotion is confirmed in previous step, build is promoted to production
         */
        stage('Promote to Production') {

            when { branch releaseBranch }

            // 'Deploy' agent pod template
            agent {
                kubernetes {
                    cloud 'openshift'
                    label 'ocp-build-agent'
                    yaml """
apiVersion: v1
kind: Pod
spec:
    containers:
      - name: jnlp
        image: 'jenkinsci/jnlp-slave:alpine'
      - name: helm
        image: 'saharshsingh/helm:2.12.3'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
      - name: openshift-client
        image: 'openshift/origin-cli:v3.11.0'
        imagePullPolicy: IfNotPresent
        command:
          - /bin/cat
        tty: true
"""
                }
            }

            steps {
                withCredentials([usernamePassword(credentialsId: clusterAuthCredentialId, usernameVariable: 'clusterUrl', passwordVariable: 'token')]) {

                    // Update (or install if missing) all application related templates
                    container('helm') {
                        helmInstall(insecureCluster, tillerNS, clusterUrl, token, productionNamespace, 'false', buildVersionWithHash, prodRouteHost, buildVersion, 'IfNotPresent', appName, helmChartDirectory)
                    }

                    // Import image tag and deploy
                    container('openshift-client') {

                        script {

                            // first import the version
                            def sourceTag      = qaNamespace + '/' + appName + ':' + buildVersion
                            def destinationTag = appName + ':' + buildVersion
                            importTagAndDeploy(insecureCluster, clusterUrl, token, productionNamespace, sourceTag, destinationTag, null)

                            // next move latest to imported tag to trigger deployment
                            sourceTag      = destinationTag
                            destinationTag = appName + ':latest'
                            importTagAndDeploy(insecureCluster, clusterUrl, token, productionNamespace, sourceTag, destinationTag, null)

                        }
                    }

                }
            }
        }

    }
}
