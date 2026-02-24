import Dependencies.*
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys.{resolvers, *}
import sbt.*
import sbt.util.Level

import scala.collection.{Seq, immutable}

object Settings {

  val commonSettings =
    Seq(
      scalaVersion := "2.13.16",
      scalacOptions := Seq(
        "-Ymacro-annotations",
        "-deprecation",
        "-encoding",
        "utf-8",
        "-explaintypes",
        "-feature",
        "-unchecked",
        "-language:postfixOps",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Xcheckinit",
        "-Xfatal-warnings",
        "-Ywarn-unused:params,-implicits"
      ),
      logLevel := Level.Info,
      scalafmtOnCompile := true,
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      cancelable in Global := true,
      fork in Global := true, // https://github.com/sbt/sbt/issues/2274
      resolvers ++= Seq(
        Resolver.mavenCentral,
        "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
        "Sonatype OSS Snapshots s01" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
        Resolver.DefaultMavenRepository,
        Resolver.mavenLocal,
        Resolver.sonatypeCentralSnapshots
      )
    )

  val commonDependencies: Seq[ModuleID] =
    List(zio, zio_config, zio_config_magnolia, zio_config_typesafe, zio_json, zio_test, zio_test_sbt)
  val httpDependencies: immutable.Seq[ModuleID] = List(zio, zio_http, zio_stream, zio_json)
  val dbDependencies: immutable.Seq[ModuleID] = List(zio, quill, pg, hikaricp)
  val bot4sDependencies: immutable.Seq[ModuleID]  =
    List(bot4s_core, bot4s_akka, ZioIoCats, zio_sttp, sttp_client_backend_zio, catsEffect)

}