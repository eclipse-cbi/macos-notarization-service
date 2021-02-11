local deployment = import "kube-deployment.libsonnet";

deployment.newDeployment([
  "172.30.206.145", 
  // "172.30.206.145"
])