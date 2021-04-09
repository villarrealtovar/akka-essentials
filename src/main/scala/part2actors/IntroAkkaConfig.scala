package part2actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object IntroAkkaConfig extends App {
  /**
   * Method 1 - inline configuration
   */
  val configString =
    """
      | akka {
      |   loglevel = "ERROR"
      | }
      |""".stripMargin

  val config = ConfigFactory.parseString(configString)

  class SimpleLoggingActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }


  val system = ActorSystem("ConfigurationDemo", ConfigFactory.load(config))
  val actor = system.actorOf(Props[SimpleLoggingActor])

  actor ! "A message to remember"

  /**
   * Method 2 - configuration in a file
   */
  val defaultConfigFileSystem = ActorSystem("DefaultConfigFileDemo")
  val defaultConfigActor = defaultConfigFileSystem.actorOf(Props[SimpleLoggingActor])

  defaultConfigActor ! "Remember me"

  /**
   * Method 3 - separate config in the same file: resources/application.conf
   */
  val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
  val specialConfigSystem = ActorSystem("SpecialConfigDemo", specialConfig)
  val specialConfigActor = specialConfigSystem.actorOf(Props[SimpleLoggingActor])
  specialConfigActor ! "Remember me, I'm special"

  /**
   * Method 4 - Separate config in another file
   */
  val separateConfig = ConfigFactory.load("secretFolder/secretConfiguration.conf")
  println(s"separate config log level: ${separateConfig.getString("akka.loglevel")}")

  /**
   * Method 5 - different file formats: JSON, Properties
   */
  val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
  println(s"json config: ${jsonConfig.getString("aJsonProperty")}")
  println(s"json config: ${jsonConfig.getString("akka.loglevel")}")

  val propsConfig = ConfigFactory.load("props/propsConfiguration.properties")
  println(s"properties config: ${propsConfig.getString("my.simpleProperty")}")
  println(s"properties config: ${propsConfig.getString("akka.loglevel")}")
}
