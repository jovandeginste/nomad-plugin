properties([
    disableConcurrentBuilds(),
])

node {
  generic = new be.kuleuven.icts.Generic()
  mavenbuilder = new be.kuleuven.icts.Maven(
      this, 'icts-p-lnx-snapshots-maven-local',
      'icts-p-lnx-releases-maven-local')
  checkout scm
  generic.time {
    mavenbuilder.build(deployArtifacts: true)
  }
}
