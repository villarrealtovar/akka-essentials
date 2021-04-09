package part4faulttolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}

object StartingStoppingActor extends App {

  object Parent {
    case class StartChild(name: String)
    case class StopChild(name: String)
    case object Stop
  }

  class Parent extends Actor with ActorLogging {
    import Parent._

    override def receive: Receive = withChildren(Map())

    def withChildren(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Starting child $name")
        context.become(withChildren(children + (name -> context.actorOf(Props[Child], name))))
      case StopChild(name) =>
        log.info(s"Stopping child with the name $name")
        val childOption = children.get(name)
        childOption.foreach(childRef => context.stop(childRef)) // `context.stop` stops the child. It sends
                                                                // a signal to stop, but that doesn't mean that
                                                                // that it's stopped immediately.
      case Stop =>
        log.info("Stopping myself")
        context.stop(self) // <= stop itself and all its children. In fact, context stops all the children
                          // first and then it stops the parent
      case message => log.info(message.toString)
    }
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  /**
   * Application domain
   */

  /**
   * Method 1 - stop children using context.stop
   */
  import Parent._

  val system = ActorSystem("StoppingActorsDemo")
  val parent = system.actorOf(Props[Parent], "parent")
  // parent ! StartChild("child1")
  // val child = system.actorSelection("/user/parent/child1")
  // child ! "hi kid!"

  // stopping a child
  // parent ! StopChild("child1")
  // for (_ <- 1 to 50) child ! "are you still there?" // after the StopChild message, the child can log some
                                                    // messages because all happens in asynchronous way.

  // When parent stops, then parent stops all its children as well
  // parent ! StartChild("child2")
  // val child2 = system.actorSelection("/user/parent/child2")
  // child2 ! "hi, second child"
  // parent ! Stop
  // for (_ <- 1 to 10) parent ! "Parent, are you still there?" // should not be received
  // for (i <- 1 to 100) child2 ! s"[$i]second kid, are you still alive?"

  /**
   * Method 2 -  using special messages
   */

  // PoisonPill
//  val looseActor = system.actorOf(Props[Child])
//  looseActor ! "hello, loose actor"
//  looseActor ! PoisonPill // `PoisonPill` is one of the special messages that are handled by actors.
//                          // `PoisonPill` will invoke the stopping procedure
//  looseActor ! "loose actor, are you still there?"
//
//  // Kill
//  val abruptlyTerminatedActor = system.actorOf(Props[Child])
//  abruptlyTerminatedActor ! "you are about to be terminated"
//  abruptlyTerminatedActor ! Kill // `Kill` is more brutal than `PoisonPill` because `Kill` makes the
//                                  // actor throws an `ActorKilledException` Error
//  abruptlyTerminatedActor ! "you have been terminated"

  // `PoisonPill` and `Kill` messages are handled by separated Actors so,
  // you cannot handle these exceptions/messages in the Actor's `receive` method

  /**
   * Mechanism -  Death watch: mechanism for being notified when an actor dies.
   */
  class Watcher extends Actor with ActorLogging {
    import Parent._

    override def receive: Receive = {
      case StartChild(name) =>
        val child = context.actorOf(Props[Child], name)
        log.info(s"Started and watching child $name")
        context.watch(child) // `context.watch register `Watcher` actor for the death of
                            // the child. When the child dies, this actor will receive a
                            // special `Terminated` message.
                            // Also, any actor can watch any other actor which is not necessarily
                            // its children.
      case Terminated(ref) =>
        log.info(s"the reference that I'm watching $ref has been stopped")
    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("watchedChild")

  val watchedChild = system.actorSelection("/user/watcher/watchedChild")

  Thread.sleep(500) // to be sure that watchedChild has been created, but in practice this is not used
  watchedChild ! PoisonPill





}
