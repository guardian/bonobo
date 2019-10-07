import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

name := "bonobo"
scalaVersion := "2.12.10"
maintainer := "content.platforms@guardian.co.uk"

val preferences =
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DanglingCloseParenthesis, Preserve)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)

libraryDependencies ++= Seq(
  ws,
  filters,
  "org.webjars" % "bootstrap" % "4.3.1" exclude("org.webjars", "jquery"),
  "org.webjars" % "jquery" % "3.4.1",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.646" exclude("commons-logging", "commons-logging"),
  "com.amazonaws" % "aws-java-sdk-ses" % "1.11.646",
  "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B4",
  "com.gu" %% "play-googleauth" % "0.7.9",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.63",
  "org.scalatest" %% "scalatest" % "3.0.8" % "it,test",
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "it,test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "com.beachape" %% "enumeratum" % "1.5.13",
  "com.beachape" %% "enumeratum-play-json" % "1.5.16"
)

enablePlugins(RiffRaffArtifact, JavaAppPackaging)

Universal / topLevelDirectory := None
Universal / packageName := normalizedName.value
riffRaffPackageType := (Universal / dist).value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffManifestProjectName := s"Content Platforms::${name.value}"

routesGenerator := InjectedRoutesGenerator

testOptions in Test += Tests.Argument("-oF")

parallelExecution in IntegrationTest := false

sourceDirectory in IntegrationTest := (baseDirectory.value / "integration-test")
