def serviceName = 'guest-accounts-password'
def teamName = 'middleware'
def serviceVersion = 'v1:001'
def marathonJson = 'marathon.json'
def serviceImplDir = './' + serviceName + '-impl'

def options = [
        'sonarqube'    : false,
        'checkmarx'    : false,
        'cxPreset'     : 'RCL-Middleware',
        'cxProjectName': 'RCL-Middleware',
        'tools'        : false,
        'reports'      : false,
        'jacoco'       : true,
        'apigee'       : false,
        'apigeeProfile': 'dev',
        'kafka'        : false,
        'environment'  : 'dev',
        'locations'    : 'all',
        'perftest'     : false,
        'pack'         : true,
        'publish'      : true,
        'deploy'       : true
]

commonChassisBuildVersionManaged(teamName, serviceName, serviceVersion, marathonJson, serviceImplDir, options)
