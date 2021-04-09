package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.util.Random
import scala.concurrent.duration._

object TimedAssertionSpec {

  case class WorkResult(result: Int)

   class WorkerActor extends  Actor {
     override def receive: Receive = {
       case "work" =>
         // simulate long computation
         Thread.sleep(500)
         sender() ! WorkResult(42)
       case "workSequence" =>
         // simulate other kind of Actor which replies in Rapid Fire
         val r = new Random()
         for (i <- 1 to 10) {
           Thread.sleep(r.nextInt(50)) // smaller work
           sender() ! WorkResult(1)
         }

     }
   }
}

class TimedAssertionsSpec extends TestKit(
  ActorSystem("Timed", ConfigFactory.load().getConfig("specialTimedAssertionsConfig")))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {
  import TimedAssertionSpec._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A worker actor" should {
    val workerActor = system.actorOf(Props[WorkerActor])

    "reply with the meaning of life in a timely manner" in {
      // Time box test: the code must happen between at least
      // 500 milliseconds and at most 1 second
      within(500 millis, 1 second) {
        workerActor ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with the meaning of life in a timely manner at most" in {
      // Time box test: the code must happen between at most
      // 600 milliseconds
      within(600 millis) {
        workerActor ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with valid work at a reasonable cadence" in {
      within(1 second) {
        workerActor ! "workSequence"
        val results: Seq[Int] = receiveWhile[Int](max = 2 seconds, idle=500 millis, messages=10){
          case WorkResult(result) => result
        }

        assert(results.sum > 5)
      }
    }

    "reply to a test probe in a timely manner" in {
      within(1 second) {
        val probe = TestProbe()
        probe.send(workerActor, "work")
        probe.expectMsg(WorkResult(42)) // timeout of 0.3 seconds according to loaded application.conf
                                        // therefore this test failed
      }

    }

  }

}
