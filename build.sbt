import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

name := "bonobo"
scalaVersion := "2.12.4"

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
  "org.webjars" % "bootstrap" % "3.3.7-1" exclude("org.webjars", "jquery"),
  "org.webjars" % "jquery" % "3.2.1",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.188" exclude("commons-logging", "commons-logging"),
  "com.amazonaws" % "aws-java-sdk-ses" % "1.11.188",
  "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3",
  "com.gu" %% "play-googleauth" % "0.7.6",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.59",
  "org.scalatest" % "scalatest_2.12" % "3.0.4" % "it,test",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.1" % "it,test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "com.beachape" %% "enumeratum" % "1.5.12",
  "com.beachape" %% "enumeratum-play-json" % "1.5.12-2.6.0-M7"
)

enablePlugins(RiffRaffArtifact, JavaAppPackaging)

Universal / topLevelDirectory := None
Universal / packageName := normalizedName.value
riffRaffPackageType := (Universal / dist).value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffManifestProjectName := s"Content Platforms::${name.value}"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

routesGenerator := InjectedRoutesGenerator

testOptions in Test += Tests.Argument("-oF")

parallelExecution in IntegrationTest := false

sourceDirectory in IntegrationTest := (baseDirectory.value / "integration-test")
