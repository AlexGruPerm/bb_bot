import sbt._

object Dependencies {
  val zio = "dev.zio" %% "zio" % Version.zio
  val zio_json = "dev.zio" %% "zio-json" % Version.zio_json
  val zio_http = "dev.zio" %% "zio-http" % Version.zio_http
  val zio_stream = "dev.zio" %% "zio-streams" % Version.zio_stream
  val zio_config = "dev.zio" %% "zio-config" % Version.zio_config
  val zio_config_magnolia= "dev.zio" %% "zio-config-magnolia" % Version.zio_config
  val zio_config_typesafe = "dev.zio" %% "zio-config-typesafe" % Version.zio_config

  val zio_test = "dev.zio" %% "zio-test" % Version.zio
  val zio_test_sbt = "dev.zio" %% "zio-test-sbt" % Version.zio

  val pg = "org.postgresql" % "postgresql" % Version.pg

  val quill = "io.getquill" %% "quill-jdbc-zio" % Version.quill_version
  val hikaricp = "com.zaxxer" % "HikariCP" % Version.hikari

  val bot4s_core = "com.bot4s" %% "telegram-core" %  Version.bot4s
  val bot4s_akka = "com.bot4s" %% "telegram-akka" %  Version.bot4s

  val ZioIoCats = "dev.zio" %% "zio-interop-cats" % Version.zioInteropCats

  val zio_sttp                = "com.softwaremill.sttp.client3" %% "zio" % Version.sttp
  val sttp_client_backend_zio = "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % Version.sttp

  val catsEffect              = "org.typelevel" %% "cats-effect" %  Version.ce
}

object Version {
  val zio            = "2.1.22"
  val zio_log        = "2.5.1"
  val zio_http       = "3.5.1"
  val zio_json       = "0.7.44"
  val zio_stream     = "2.1.22"
  val zio_config     = "4.0.5"
  val zioLogSlf4j    = "2.5.1"
  val quill_version  = "4.8.5"
  val pg             = "42.7.8"
  val hikari         = "7.0.2"
  val bot4s          = "6.0.0"
  val sttp           = "3.11.0"
  val zioInteropCats = "23.1.0.5"
  val ce             = "3.5.4" // try 3.6.3
}