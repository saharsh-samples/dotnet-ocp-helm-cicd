/**
 * Data object containing necessary configuration needed for Helm install
 */
class InstallContext {

    // Set to true if cluster URL is HTTPS but verification should be skipped
    String insecureCluster

    // Openshift project where Tiller server is deployed
    String tillerNS

    // Openshift Cluster URL
    String clusterUrl

    // Token used to authenticate to the Openshift Cluster. This will be set in the `kubectl` context
    String clusterAuthToken

    // Openshift project where application will be deployed
    String namespace

    // Set to 'true' to deploy build config. 'false' otherwise
    String deployBuildConfig

    // Version of application to pass to the application (application specific)
    String appVersion

    // Route Host to set given target environment
    String routeHost

    // Tag of application container image to pull
    String imageTag

    // Image pull policy to set in application's deployment template
    String imagePullPolicy

    // Helm Release to use
    String releaseName

    // Location of directory in repo containing Helm charts (relative to repo top level)
    String chartDirectory
}

/**
 * returns a new instance of InstallContext containing no data
 */
def newInstallContext() {
    return new InstallContext()
}

/**
 * Installs Helm to designated Openshift cluster
 *
 * Params:
 *   - context : data object containing necessary configuration for install
 */
def install(InstallContext context) {

    if(context.insecureCluster == 'true') {
        context.clusterUrl = context.clusterUrl + ' --insecure-skip-tls-verify'
    }

    sh '''

    export HOME="`pwd`"
    export TILLER_NAMESPACE="''' + context.tillerNS + '''"

    kubectl config set-cluster development --server=''' + context.clusterUrl + '''
    kubectl config set-credentials jenkins --token="''' + context.clusterAuthToken + '''"
    kubectl config set-context helm --cluster=development --namespace="''' + context.namespace + '''" --user=jenkins
    kubectl config use-context helm

    helm upgrade --install \
        --namespace "''' + context.namespace + '''" \
        --set build.deploy="''' + context.deployBuildConfig + '''" \
        --set app.version="''' + context.appVersion + '''" \
        --set route.host="''' + context.routeHost + '''" \
        --set image.tag="''' + context.imageTag + '''" \
        --set image.pullPolicy="''' + context.imagePullPolicy + '''" \
        ''' + context.releaseName + ''' \
        ''' + context.chartDirectory
}

return this
