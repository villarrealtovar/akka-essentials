package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App {
 /*
    Use case: we will have an actor with a locked access to a resource

    ResourceActor
      - open => it can receive read/write requests to the resource
      - otherwise it will postpone all read/write requests until the state is open

    ResourceActor is closed
      - Open => switch to the open state
      - Read, Write messages are POSTPONED

    ResourceActor is open
      - Read, Write are handle
      - Close => switch to the closed state

     Example:
     - First ResourceActor receives => [Open, Read, Read, Write]
        - switch to the open state
        - read the data
        - read the data again
        - write the data

     - Later ResourceActor receives => [Read, Open, Write]
        - The first time when ResourceActor sees the Read message, it starts in the Close state.
          So, this Read message will be postponed. Therefore, ResourceActor will put the Read message
          in a special memory zone called `Stash`.
          - Stash Read
            So, our stash will have a special queue with the Read message
            Stash: [Read]
        - open => switch to the open state
           Now that ResourceActor is open, then it handles other messages.
           Mailbox: [Read, Write]
        - Read and Write are handled.

  */

  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // step 1 - mix-in the Stash trait
  // `Stash` trait allows the capability of putting messages aside for later and
  // popping them out for prepending to the mailbox
  class ResourceActor extends Actor with ActorLogging with Stash {
   private var innerData: String = ""

    override def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("Opening resource")
        // step 3 - unstashAll when you switch the message handler
        unstashAll()
        context.become(open)
      case message =>
        log.info(s"Stashing $message because I can't handle it in the closed stated")
        // step 2 - stash away what you can't handle
        stash()
    }

    def open: Receive = {
      case Read =>
        // do some actual computation
        log.info(s"I have read $innerData")
      case Write(data) =>
        log.info(s"I am writing $data")
        innerData = data
      case Close =>
        log.info("Closing resource")
        unstashAll()
        context.become(closed)
      case message =>
        log.info(s"Stashing $message because I can't handle it in the open stated")
        stash()
    }

  }

  val system = ActorSystem("StashDemo")
  val resourceActor = system.actorOf(Props[ResourceActor])

  resourceActor ! Read // stashed
  resourceActor ! Open // switch to open state
  resourceActor ! Open // stashed
  resourceActor ! Write("I love stash") // I am writing I love stash
  resourceActor ! Close // switch to closed; switch to open
  resourceActor ! Read // I have read I love stash
}
