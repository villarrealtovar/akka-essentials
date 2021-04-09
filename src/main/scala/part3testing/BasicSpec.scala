package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import part3testing.BasicSpec._

import scala.concurrent.duration._
import scala.util.Random

/**
 * Recommendation: create a companion object for storing
 * all the information or all the methods, or all the values
 * that you're going to use in your tests.
 */
object BasicSpec {
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case message => sender() ! message
    }
  }

  class BlackHole extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  class LabTestActor extends Actor {
    val random = new Random()
    override def receive: Receive = {
      case "greeting" =>
        if (random.nextBoolean()) sender() ! "hi" else sender() ! "hello"
      case "favoriteTech" =>
        sender() ! "Scala"
        sender() ! "Akka"
      case message: String => sender() ! message.toUpperCase()
    }
  }
}

class BasicSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  // basic setup
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system) // system is a member of TestKit
  }

  /* General Test Structure

  "The thing being tested" should { // <= this is a test suite
    "do this" in { // test
      // testing scenario
    }
  }

  */

  "An simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val message = "hello, test"
      echoActor ! message

      expectMsg(message) // the timeout is 3 sec by default. But, it's configurable in
      // the config object: akka.test.single-expect-default
    }
  }

  "A blackhole actor" should {
    "send back some message" in {
      val blackholeActor = system.actorOf(Props[BlackHole])
      val message = "hello, test"
      blackholeActor ! message

      expectNoMessage(1 second)
    }
  }

  "A lab test actor" should {
    val labTestActor = system.actorOf(Props[LabTestActor])

    "turn a string into uppercase 1" in {
      labTestActor ! "I love Akka"
      expectMsg("I LOVE AKKA")
    }

    "turn a string into uppercase 2" in {
      labTestActor ! "I love Akka"
      val reply = expectMsgType[String] // Obtain the message with expectMsgType

      // do complex assertions with `reply`
      assert(reply == "I LOVE AKKA")
    }

    "reply to a greeting" in {
      labTestActor ! "greeting"
      expectMsgAnyOf("hi", "hello")
    }

    "reply with favorite tech" in {
      labTestActor ! "favoriteTech"
      expectMsgAllOf("Scala", "Akka")
    }

    "reply with cool tech in a different way" in {
      labTestActor ! "favoriteTech"
      val messages = receiveN(2) // returns a Seq[Any]

      // free to do more complicated assertions
    }

    "reply with cool tech in a fancy way" in {
      labTestActor ! "favoriteTech"

      expectMsgPF() {
        case "Scala" => // only care that the PF is defined
        case "Akka" =>
      }
    }

  }

  // message assertions
}


