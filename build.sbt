name := "language-integrated_query"

organization := "org.hablapps"

inThisBuild(Seq(
  scalaOrganization := "org.typelevel",
  scalaVersion := "2.12.4-bin-typelevel-4"
))

addCompilerPlugin("io.tryp" % "splain" % "0.3.1" cross CrossVersion.patch)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

scalacOptions ++= Seq(
  "-Xlint",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Ypartial-unification",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-language:higherKinds")

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.2.26",
  "com.chuusai" %% "shapeless" % "2.3.3",
  "io.getquill" %% "quill-sql" % "2.5.4",
  "org.hablapps" %% "statelesser" % "0.1-SNAPSHOT",
  "io.getquill" %% "quill-async-mysql" % "2.5.4",
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "com.typesafe.slick" %% "slick" % "3.2.3",
  "org.slf4j" % "slf4j-simple" % "1.6.4",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.3",
  "mysql" % "mysql-connector-java" % "5.1.34")

javaOptions ++= Seq(
  "-Dquill.binds.log=true",
  "-Dorg.slf4j.simpleLogger.defaultLogLevel=info")

fork := true

