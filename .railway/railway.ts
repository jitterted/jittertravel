import { defineRailway, github, project, service } from "railway/iac";
// This repository manages only its own resources in the environment. Other
// repositories export their own partial name. Postgres and its volume are
// deliberately absent: a named partial never deletes what it does not own.
// See https://docs.railway.com/infrastructure-as-code#multi-repo-projects
export const partial = "jittertravel";
export default defineRailway(() => {
  const jittertravel = service("jittertravel", {
    source: github("jitterted/jittertravel", { branch: "main", checkSuites: false }),
    build: { buildEnvironment: "V3", builder: "DOCKERFILE", dockerfilePath: "Dockerfile" },
    // The health check gates a rollout: a container that cannot boot fails the
    // deploy rather than replacing the healthy one (DEPLOYMENT.md).
    healthcheck: "/actuator/health",
    healthcheckTimeout: 120,
    replicas: { "europe-west4-drams3a": 1 },
    deploy: {
      // Added by hand: `railway config migrate` (CLI 5.74.0) drops these two
      // fields from railway.json, so the generated file would lose them.
      restartPolicyType: "ON_FAILURE",
      restartPolicyMaxRetries: 3,
      limitOverride: { containers: { cpu: 1, memoryBytes: 500000000 } },
    },
    domains: ["jittertravel.com"],
  });

  return project("JitterTravel", {
    // Variables stay owned by the dashboard, as before the migration: a new one
    // is set there before the push (Pre-Push-Tasks.md), and apply never deletes it.
    variables: { managed: false },
    resources: [jittertravel],
  });
});
