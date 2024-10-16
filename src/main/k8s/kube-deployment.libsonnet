local newDeployment(host, name, namespace, ips = []) = {
  name: name,
  namespace: namespace,

  local labels(name) = {
    "org.eclipse.cbi.service/name": name,
  },
  local metaData(name) = {
    name: name,
    labels: labels(name),
    namespace: namespace,
  },

  route: {
    apiVersion: "route.openshift.io/v1",
    kind: "Route",
    metadata: metaData(name) + {
      annotations: {
        "haproxy.router.openshift.io/timeout": "600s",
        "haproxy.router.openshift.io/rewrite-target": "/macos-notarization-service"
      },
    },
    spec: {
      host: host,
      path: "/macos/xcrun",
      port: {
        targetPort: "http"
      },
      tls: {
        insecureEdgeTerminationPolicy: "Redirect",
        termination: "edge"
      },
      to: {
        kind: "Service",
        name: name,
        weight: 100
      },
    }
  },
  service: {
    apiVersion: "v1",
    kind: "Service",
    metadata: metaData(name),
    spec: {
      type: "ClusterIP",
      ports: [
        {
          name: "http",
          port: 80,
          protocol: "TCP",
          targetPort: 8383
        }
      ],
    }
  },
  endpoints: {
    apiVersion: "v1",
    kind: "Endpoints",
    metadata: metaData(name),
    subsets: [
      {
        addresses: [
          {
            ip: ip
          }
        for ip in ips ],
        ports: [
          {
            name: "http",
            port: 8383,
            protocol: "TCP"
          }
        ]
      }
    ]
  },
  "kube.yml": std.manifestYamlStream([$.route, $.service, $.endpoints], true, c_document_end=false),
};
{
  newDeployment:: newDeployment,
}

