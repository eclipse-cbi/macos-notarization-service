local deployment = import "kube-deployment.libsonnet";

[ 
deployment.newDeployment("cbi.eclipse.org", "macos-notarization", "foundation-codesigning", [
  "172.30.206.146", 
]), 
deployment.newDeployment("cbi-staging.eclipse.org", "macos-notarization-staging", "foundation-codesigning", [
  "172.30.206.145",
]),
]