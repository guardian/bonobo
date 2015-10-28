name := """bonobo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, RiffRaffArtifact, UniversalPlugin)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)

riffRaffPackageType := (packageZipTarball in Universal).value

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  ws,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.20",
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.4-P24",
  "com.gu" %% "play-googleauth" % "0.3.1",
  "org.scalatest" % "scalatest_2.11" % "2.2.5" % "it,test",
  "org.scalatestplus" % "play_2.11" % "1.4.0-M4" % "it,test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

scalariformSettings

testOptions in Test += Tests.Argument("-oF")

sourceDirectory in IntegrationTest <<= baseDirectory { base => base / "integration-test" }
