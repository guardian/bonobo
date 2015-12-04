name := "bonobo"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.7"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, RiffRaffArtifact, UniversalPlugin)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)

riffRaffPackageType := (packageZipTarball in Universal).value
riffRaffBuildIdentifier := sys.env.getOrElse("CIRCLE_BUILD_NUM", "DEV")
riffRaffUploadArtifactBucket := Some("riffraff-artifact")
riffRaffUploadManifestBucket := Some("riffraff-builds")
riffRaffManifestProjectName := {
  if (sys.env.contains("CIRCLECI"))
    "Content Platforms::bonobo::circleci"
  else
    "Content Platforms::bonobo"
}

libraryDependencies ++= Seq(
  ws,
  filters,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.20",
  "com.amazonaws" % "aws-java-sdk-ses" % "1.10.20",
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.4-P24",
  "com.gu" %% "play-googleauth" % "0.3.1",
  "org.scalatest" % "scalatest_2.11" % "2.2.5" % "it,test",
  "org.scalatestplus" % "play_2.11" % "1.4.0-M4" % "it,test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "com.beachape" %% "enumeratum" % "1.3.2",
  "com.beachape" %% "enumeratum-play-json" % "1.3.2"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

routesGenerator := InjectedRoutesGenerator

scalariformSettings

testOptions in Test += Tests.Argument("-oF")

parallelExecution in IntegrationTest := false

sourceDirectory in IntegrationTest <<= baseDirectory { base => base / "integration-test" }
