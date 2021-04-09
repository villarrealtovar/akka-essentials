package part2actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object ActorLoggingDemo extends App {

  /**
   * Method 1 - `Explicit Logging` by instantiating a logging member.
   */
  class SimpleActorWithExplicitLogger extends Actor {
    val logger = Logging(context.system, this)

    override def receive: Receive = {
      /*
        Logging is generally done in 4 levels (even in other languages/frameworks)

        1 - Debug: is the most verbose and we generally use these messages to find out
                    exactly what happened in an application.
        2 - Info: these are benign messages
        3 - Warning/Warn: They might signal something wrong wih an application, but they
                          might not be cause for trouble. For example messages sent to
                          `Dead Letters` Actors
        4 - Error: Source of trouble. For example, throwing an exception or something that
                    causes an application to crash
       */
      case message => logger.info(message.toString) // LOG it
    }
  }

  val system = ActorSystem("LoggingDemo")
  val actor = system.actorOf(Props[SimpleActorWithExplicitLogger])

  actor ! "Logging a simple message"

  /**
   * Method 2 - It's the most popular and is called `ActorLogging`
   */
  class ActorWithLogging extends  Actor with ActorLogging {
    override def receive: Receive = {
      case (a, b) => log.info("Two things: {} and {}", a, b) // interpolate
      case message => log.info(message.toString)
    }
  }

  val simplerActor = system.actorOf(Props[ActorWithLogging])
  simplerActor ! "Logging a simple message by extending a trait"
  simplerActor ! (18, 2018)

  /**
   * CONCLUSIONS:
   *
   * 1. Logging is done asynchronously to minimize performance impact.
   *    In particular the Akka logging system that we used is implemented using
   *    actors itself.
   *
   * 2. Logging doesn't depend on a particular logger implementation. The default
   *    logger just dumps things to standard output. But, we can insert some other
   *    logger very easily, for example SLF4J
   */

}
