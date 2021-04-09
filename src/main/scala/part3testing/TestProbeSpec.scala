package part3testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

object TestProbeSpec {
  // scenario
  /*
    word counting actor hierarchy in form master-slave

    dataflow:

      send some work to the master
        - master send to the slave the piece of work
        - slave processes the work and replies to master
        - master aggregates the result
      master send the total count to the original requester
   */

  case class Work(text: String)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class Report(totalCount: Int)
  case class Register(slaveRef: ActorRef)
  case object RegistrationAck

  class Master extends Actor {
    override def receive: Receive = {
      case Register(slaveRef) =>
        sender() ! RegistrationAck
        context.become(online(slaveRef, 0))
      case _ => // ignore
    }

    def online(slaveRef: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = totalWordCount + count
        originalRequester ! Report(newTotalWordCount)
        context.become(online(slaveRef, newTotalWordCount))
    }
  }

  /*
    The team has develop Slave Actor, but the only care is about Master Actor because
    this is the actor that we are supposed to test.

    class Slave extends Actor ...

   */
}

class TestProbeSpec extends TestKit(ActorSystem("TestProbeSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
{
  import TestProbeSpec._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // TestProbe is a special actor with some assertion capabilities

      master ! Register(slave.ref) // slave.ref is the Actor Reference with this TestProbe
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref) // slave.ref is the Actor Reference with this TestProbe
      expectMsg(RegistrationAck)

      val workloadString = "I love Akka"
      master ! Work(workloadString) // testActor is an implicit sender

      // testing the interaction between the Master Actor and a fictitious Slave Actor (TestProbe)
      slave.expectMsg(SlaveWork(workloadString, testActor)) // master forwards the SlaveWork to TestProbe
      slave.reply(WorkCompleted(3, testActor)) // mocking the reply to Master Actor

      expectMsg(Report(3)) // Master Actor reply to testActor with Report(3)
    }

    "aggregate data correctly" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref) // slave.ref is the Actor Reference with this TestProbe
      expectMsg(RegistrationAck)
      val workloadString = "I love Akka"

      master ! Work(workloadString)
      master ! Work(workloadString)

      // in the meantime I don't have a slave actor
      slave.receiveWhile() {
        case SlaveWork(`workloadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
      }

      expectMsg(Report(3))
      expectMsg(Report(6))

    }

  }


}
