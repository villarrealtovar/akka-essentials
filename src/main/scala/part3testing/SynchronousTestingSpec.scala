package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.Duration


object SynchronousTestingSpec {

  case object Increment
  case object Read

  class Counter extends Actor {
    var count = 0

    override def receive: Receive = {
      case Increment => count += 1
      case Read => sender() ! count
    }
  }
}

/**
 * Synchronous Testing doesn't need TestKit Infrastructure
 * */
class SynchronousTestingSpec extends WordSpecLike with BeforeAndAfterAll {
  import SynchronousTestingSpec._

  implicit val system = ActorSystem("SynchronousTestingSpec")

  override def afterAll(): Unit = {
    system.terminate()
  }

  "A counter" should {
    "synchronously increases its counter" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter ! Increment // counter has ALREADY received the message because sending a message
                          // to a TestActorFef happens in the same calling thread

      // counter.underlyingActor.count => I can poke inside the actor instance
      assert(counter.underlyingActor.count == 1)
    }

    "synchronously increase its counter a the call of the receive function" in {
      val counter = TestActorRef[Counter](Props[Counter]) // <= this needs an implicit ActorSystem. That is why
                                                          // `system` is implicit
      counter.receive(Increment) // it's basically the same thing that counter ! Increment
                                // because sending a message is already happening in on the
                                // same thread
      assert(counter.underlyingActor.count == 1)
    }

    // other method of making the single thread behavior is
    // by using the calling thread dispatcher: basically means that
    // the communication with the actor happens on the calling thread
    // hence the name.
    "work on the calling thread dispatcher" in {
      // `.withDispatcher(CallingThreadDispatcher.Id))` means that whatever message I sent
      // to this counter will happen on the calling thread.
      val counter = system.actorOf(Props[Counter].withDispatcher(CallingThreadDispatcher.Id))
      val probe = TestProbe()

      probe.send(counter, Read)
      probe.expectMsg(Duration.Zero, 0) // probe has ALREADY received the message 0
    }

  }

}
