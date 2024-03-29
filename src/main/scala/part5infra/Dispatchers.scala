package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object Dispatchers extends App {
  class Counter extends Actor with ActorLogging {
    var count = 0

    override def receive: Receive = {
      case message =>
        count += 1
        log.info(s"[$count] $message")
    }
  }

  val system = ActorSystem("DispatcherDemo") //, ConfigFactory.load().getConfig("dispatcherDemo"))

  // method #1. programmatic/in code
  val actors = for (i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")

  val r = new Random()
/*  for (i <- 1 to 1000) {
    actors(r.nextInt(10)) ! i
  }*/

  // method # 2. from config
  val javtActor = system.actorOf(Props[Counter], "javt")


  /**
   * Dispatchers implement the ExecutionContext trait
   */
  class DBActor extends Actor with ActorLogging {
    // implicit val executionContext: ExecutionContext = context.dispatcher
    implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher")

    override def receive: Receive = {
      case message => Future {
        // wait on a resource
        Thread.sleep(5000)
        log.info(s"Success: $message")
      }

    }
  }

  val dbActor = system.actorOf(Props[DBActor])
  // dbActor ! "the meaning of life is 38"

  val nonBlockingActor = system.actorOf(Props[Counter])
  for(i <- 1 to 1000) {
    val message = s"important message $i"
    dbActor ! message
    nonBlockingActor ! message
  }
}
