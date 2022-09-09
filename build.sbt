val scala3Version = "3.2.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "streams",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test

      // used for playing with S3.scala
      // "software.amazon.awssdk" % "aws-sdk-java" % "2.17.269"
    )
  )
