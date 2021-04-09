package part4faulttolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

object ActorLifeCycle extends App {

  object StartChild

  class LifecycleActor extends Actor with ActorLogging {

    override def preStart(): Unit = log.info("I am starting")
    override def postStop(): Unit = log.info("I am stopped")

    override def receive: Receive = {
      case StartChild =>
        context.actorOf(Props[LifecycleActor], "child")
    }
  }

  val system = ActorSystem("LifecycleDemo")
//  val parent = system.actorOf(Props[LifecycleActor], "parent")
//  parent ! StartChild
//  parent ! PoisonPill

  /**
   * restart hook
   */
  object Fail
  object FailChild
  object CheckChild
  object Check

  class Parent extends Actor {
    val child = context.actorOf(Props[Child], "supervisedChild")

    override def receive: Receive = {
      case FailChild => child ! Fail
      case CheckChild => child ! Check
    }
  }

  class Child extends Actor with ActorLogging {

    override def preStart(): Unit = log.info("supervised child started")
    override def postStop(): Unit = log.info("supervised child stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.info(s"supervised actor restarting because of ${reason.getMessage}")

    override def postRestart(reason: Throwable): Unit =
      log.info("supervised actor restarted")

    override def receive: Receive = {
      case Fail =>
        log.warning("child will fail now")
        throw new RuntimeException("I failed")
      case Check =>
        log.info("alive and kicking")
    }
  }

  val supervisor = system.actorOf(Props[Parent], "superviser")
  supervisor ! FailChild
  supervisor ! CheckChild

  /*
    Even if the `Child` Actor threw an exception previously which is a ver
    serious thing, the child was still restarted and it was able to process
    more messages. And this is part of the default what it's called

    `SUPERVISION STRATEGY`

    And the default Supervision Strategy says the following:

      If an Actor threw an exception while processing a message, this message which
      caused the exception to be thrown is removed from the queue and no put back
      in the mailbox again. And after it's restarted which means the mailbox is untouched,
   */


}
