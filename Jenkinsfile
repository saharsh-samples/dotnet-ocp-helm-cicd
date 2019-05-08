// Define global variable to hold dynamically loaded modules
// Modules will be loaded in 'Initialize' step
def modules = [:]

/**
 * REFERENCE CI/CD PIPELINE FOR OPENSHIFT NATIVE .NET APPLICATION
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
        string(name: 'k8sCloudForDynamicSlaves', defaultValue: 'openshift', description: 'Cloud name for Kubernetes cluster where Jenkins slave pods will be spawned')
        string(name: 'clusterAuthCredentialId', defaultValue: 'ocp-cluster-auth', description: 'ID of Jenkins credential containing OCP Cluster authentication for Helm deploys')
        string(name: 'gitCredentialId', defaultValue: 'git-auth', description: 'ID of Jenkins credential containing Git server username and password')
        string(name: 'confirmationTimeoutValue', defaultValue: '5', description: 'Integer indicating length of time to wait for manual confirmation')
        string(name: 'confirmationTimeoutUnits', defaultValue: 'DAYS', description: 'Time unit to use for CONFIRMATION_WAIT_VALUE')

        // Git Properties
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
        k8sCloudForDynamicSlaves  = "${k8sCloudForDynamicSlaves}"
        clusterAuthCredentialId   = "${clusterAuthCredentialId}"
        gitCredentialId           = "${gitCredentialId}"
        confirmationTimeoutValue  = "${confirmationTimeoutValue}"
        confirmationTimeoutUnits  = "${confirmationTimeoutUnits}"

        // Git Properties
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
                script {

                    // load modules
                    modules.common    = load '.jenkins/groovy/commonutils.groovy'
                    modules.helm      = load '.jenkins/groovy/helm.groovy'
                    modules.openshift = load '.jenkins/groovy/openshift.groovy'

                    // Read Pod templates for dynamic slaves from files
                    env.buildahAgentYaml = readFile '.jenkins/agents/buildah-agent.yml'
                    env.ocpHelmAgentYaml = readFile '.jenkins/agents/ocp-helm-agent.yml'

                    // Determine if build should be skipped
                    env.shouldJobRun = modules.common.shouldJobRun()

                    // Set version information to build environment
                    env.buildVersion         = modules.common.getVersionFromHelmChart(helmChartFile, releaseBranch)
                    // Set version + "git commit hash" information to environment
                    env.buildVersionWithHash = env.buildVersion + '-' + sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                }
            }
        }

        /**
         * STAGE - Feature branch build
         *
         * Uses saharshsingh/container-management:1.0 image to build container image
         */
        stage('Feature branch build') {

            when { not { branch releaseBranch } }

            // 'Feature branch build' agent pod template
            agent {
                kubernetes {
                    cloud k8sCloudForDynamicSlaves
                    label 'buildah'
                    yaml buildahAgentYaml
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
         * STAGE - Deliver Release
         *
         * Only executes on release branch builds. Builds application container image
         * using Openshift build config and delivers image to registry for long term storage
         */
        stage('Deliver Release') {

            when { allOf { branch releaseBranch; expression { return shouldJobRun == 'true' } } }

            // 'Deliver Release' agent pod template
            agent {
                kubernetes {
                    cloud k8sCloudForDynamicSlaves
                    label 'ocp-helm-agent'
                    yaml ocpHelmAgentYaml
                }
            }

            steps {

                withCredentials([usernamePassword(credentialsId: clusterAuthCredentialId, usernameVariable: 'clusterUrl', passwordVariable: 'token')]) {

                    script {

                        // Update (or install if missing) all application related templates
                        container('helm') {

                            // define Helm install context
                            def context               = modules.helm.newInstallContext()
                            context.insecureCluster   = insecureCluster
                            context.tillerNS          = tillerNS
                            context.clusterUrl        = clusterUrl
                            context.clusterAuthToken  = token
                            context.namespace         = developmentNamespace
                            context.deployBuildConfig = 'true'
                            context.appVersion        = buildVersionWithHash
                            context.routeHost         = devRouteHost
                            context.imageTag          = buildVersion
                            context.imagePullPolicy   = 'Always'
                            context.releaseName       = appName + '-dev'
                            context.chartDirectory    = helmChartDirectory

                            // run Helm install
                            modules.helm.install(context)
                        }

                        // build image and deploy
                        container('openshift-client') {

                            // build image tag
                            modules.openshift.buildImageStreamTag(insecureCluster, clusterUrl, token, developmentNamespace, appName, appDirectory)
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

            when { allOf { branch releaseBranch; expression { return shouldJobRun == 'true' } } }

            agent any

            steps {
                withCredentials([usernamePassword(credentialsId: gitCredentialId, usernameVariable: 'gitUser', passwordVariable: 'gitPassword')]) {
                    script {
                        modules.common.tagCommitAndIncrementVersion(gitUser, gitPassword, releaseBranch, buildVersion, helmChartFile)
                    }
                }
            }

        }

        /**
         * STAGE - Deploy to Dev
         *
         * Only executes release branch builds. Deploys to 'Dev' environment.
         */
        stage('Deploy to Dev') {

            when { allOf { branch releaseBranch; expression { return shouldJobRun == 'true' } } }

            // 'Deploy to Dev' agent pod template
            agent {
                kubernetes {
                    cloud k8sCloudForDynamicSlaves
                    label 'ocp-helm-agent'
                    yaml ocpHelmAgentYaml
                }
            }

            steps {

                withCredentials([usernamePassword(credentialsId: clusterAuthCredentialId, usernameVariable: 'clusterUrl', passwordVariable: 'token')]) {

                    // Import image tag and deploy
                    script {
                        container('openshift-client') {
                            def sourceTag      = appName + ':' + buildVersion
                            def destinationTag = appName + ':latest'
                            modules.openshift.importTagAndDeploy(insecureCluster, clusterUrl, token, developmentNamespace, sourceTag, destinationTag, appName)
                        }
                    }
                }
            }
        }

        /**
         * STAGE - Promote to QA
         *
         * Only executes on release branch builds. Promotes release to QA
         */
        stage('Promote to QA') {

            when { allOf { branch releaseBranch; expression { return shouldJobRun == 'true' } } }

            // 'Promote to QA' agent pod template
            agent {
                kubernetes {
                    cloud k8sCloudForDynamicSlaves
                    label 'ocp-helm-agent'
                    yaml ocpHelmAgentYaml
                }
            }

            steps {

                withCredentials([usernamePassword(credentialsId: clusterAuthCredentialId, usernameVariable: 'clusterUrl', passwordVariable: 'token')]) {

                    script {

                        // Update (or install if missing) all application related templates
                        container('helm') {

                            // define Helm install context
                            def context               = modules.helm.newInstallContext()
                            context.insecureCluster   = insecureCluster
                            context.tillerNS          = tillerNS
                            context.clusterUrl        = clusterUrl
                            context.clusterAuthToken  = token
                            context.namespace         = qaNamespace
                            context.deployBuildConfig = 'false'
                            context.appVersion        = buildVersionWithHash
                            context.routeHost         = qaRouteHost
                            context.imageTag          = buildVersion
                            context.imagePullPolicy   = 'IfNotPresent'
                            context.releaseName       = appName + '-qa'
                            context.chartDirectory    = helmChartDirectory

                            // run Helm install
                            modules.helm.install(context)
                        }

                        // Import image tag and deploy
                        container('openshift-client') {
                            def sourceTag      = developmentNamespace + '/' + appName + ':' + buildVersion
                            def destinationTag = appName + ':latest'
                            modules.openshift.importTagAndDeploy(insecureCluster, clusterUrl, token, qaNamespace, sourceTag, destinationTag, appName)
                        }

                    }
                }
            }
        }

        /**
         * STAGE - Confirm Promotion to Production
         *
         * Pipeline halts for configured amount of time and waits for someone to click Proceed or Abort.
         */
        stage('Confirm Promotion to Production') {

            when { allOf { branch releaseBranch; expression { return shouldJobRun == 'true' } } }

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

            when { allOf { branch releaseBranch; expression { return shouldJobRun == 'true' } } }

            // 'Deploy' agent pod template
            agent {
                kubernetes {
                    cloud k8sCloudForDynamicSlaves
                    label 'ocp-helm-agent'
                    yaml ocpHelmAgentYaml
                }
            }

            steps {
                withCredentials([usernamePassword(credentialsId: clusterAuthCredentialId, usernameVariable: 'clusterUrl', passwordVariable: 'token')]) {

                    // Update (or install if missing) all application related templates
                    container('helm') {
                        script {

                            // define Helm install context
                            def context               = modules.helm.newInstallContext()
                            context.insecureCluster   = insecureCluster
                            context.tillerNS          = tillerNS
                            context.clusterUrl        = clusterUrl
                            context.clusterAuthToken  = token
                            context.namespace         = productionNamespace
                            context.deployBuildConfig = 'false'
                            context.appVersion        = buildVersionWithHash
                            context.routeHost         = prodRouteHost
                            context.imageTag          = buildVersion
                            context.imagePullPolicy   = 'IfNotPresent'
                            context.releaseName       = appName
                            context.chartDirectory    = helmChartDirectory

                            // run Helm install
                            modules.helm.install(context)
                        }
                    }

                    // Import image tag and deploy
                    script {
                        container('openshift-client') {
                            def sourceTag      = developmentNamespace + '/' + appName + ':' + buildVersion
                            def destinationTag = appName + ':latest'
                            modules.openshift.importTagAndDeploy(insecureCluster, clusterUrl, token, productionNamespace, sourceTag, destinationTag, null)
                        }
                    }

                }
            }
        }

    }
}
