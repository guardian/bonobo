name := """bonobo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
  .enablePlugins(RiffRaffArtifact, UniversalPlugin)

riffRaffPackageType := (packageZipTarball in Universal).value

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  ws,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.20",
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.4-P24",
  "org.scalatest" % "scalatest_2.11" % "2.2.5",
  "org.scalatestplus" % "play_2.11" % "1.4.0-M4",
  "org.mockito" % "mockito-all" % "1.10.19",
  "com.gu" %% "play-googleauth" % "0.3.1"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

scalariformSettings

testOptions in Test += Tests.Argument("-oF")