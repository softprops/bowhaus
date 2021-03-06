import AssemblyKeys._ // put this at the top of the file

assemblySettings

name := "bowhaus"

version := "0.1.1"

jarName in assembly <<= (version).map { v => "bowhaus-%s.jar" format(v) }

description := "a jvm hausing for bower components"

scalacOptions ++= Seq("-feature", "-deprecation")

scalaVersion := "2.10.0"

libraryDependencies += "net.databinder" %% "unfiltered-netty-server" % "0.6.8"

libraryDependencies += "com.twitter" %% "storehaus-redis" % "0.4.0"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.2"

libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"
