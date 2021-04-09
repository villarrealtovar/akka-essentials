package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.{Counter, system}
import part2actors.ChangingActorBehavior.Citizen.VoteStatusRequest
import part2actors.ChangingActorBehavior.Mom.{CHOCOLATE, Food, VEGETABLE}

object ChangingActorBehavior extends App{

  object FussyKid {
    case object KidAccept
    case object KidReject

    val HAPPY = "happy"
    val SAD = "sad"
  }
  class FussyKid extends Actor {
    import FussyKid._
    import Mom._

    // internal state of the kid
    var state = HAPPY
    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  class StatelessFussyKid extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)// change my receive handler to sadReceive
      case Food(CHOCOLATE) => // stay happy
      case Ask(_) => sender() ! KidAccept
    }
    def sadReceive: Receive =  {
      case Food(VEGETABLE) => context.become(sadReceive, false)
      case Food(CHOCOLATE) => context.unbecome()
      case Ask(_) => sender() ! KidReject
    }
  }


  object Mom {
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(message: String) // do you want to play?

    val VEGETABLE = "veggies"
    val CHOCOLATE = "chocolate"
  }
  class Mom extends Actor {
    import Mom._
    import FussyKid._

    override def receive: Receive = {
      case MomStart(kidRef) =>
        // test our interaction
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(CHOCOLATE)
        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("do you want to play?")
      case KidAccept => println("Yay, my kid is happy")
      case KidReject => println("My kid is sad, but as he's healthy")
    }
  }

  import Mom._

  val system = ActorSystem("changingActorBehaviorDemo")
  val fussyKid = system.actorOf(Props[FussyKid])
  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid])
  val mom = system.actorOf(Props[Mom])

  //  mom ! MomStart(fussyKid)
  mom ! MomStart(statelessFussyKid)

  /**
  * Exercise 1 - Recreate the Counter Actor with context.become and NO MUTABLE STATE
  * */
  // DOMAIN of the Counter
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter extends  Actor {
    import Counter._

    override def receive: Receive = countReceive(0)

    def countReceive(currentCount: Int): Receive = {
      case Increment =>
        println(s"[counterReceive($currentCount)] incrementing")
        context.become(countReceive(currentCount + 1))
      case Decrement =>
        println(s"[countReceive($currentCount)] decrementing")
        context.become(countReceive(currentCount - 1))
      case Print => println(s"[countReceive($currentCount)] My current count is $currentCount")

    }
  }

  val counter = system.actorOf(Props[Counter], "myCounter")

  import Counter._
  (1 to 5).foreach(_ => counter ! Increment)
  (1 to 2).foreach(_ => counter ! Decrement)
  counter ! Print

  /**
   *  Exercise 2 - Simplified voting system
   */

  // SOLUTION 1 - WITH MUTABLE STATE
  object Citizen {
    case class Vote(candidate: String)
    case object VoteStatusRequest
    case class VoteStatusReply(candidate: Option[String])
  }

  class Citizen extends Actor {
    import Citizen._
    var candidate: Option[String] = None

    override def receive: Receive = {
      case Vote(c) => candidate = Some(c)
      case VoteStatusRequest => sender() ! VoteStatusReply(candidate)
    }
  }
  object VoteAggregator {
    case class AggregateVotes(citizens: Set[ActorRef])
  }
  class VoteAggregator extends Actor {
    import VoteAggregator._
    import Citizen._

    var stillWaiting: Set[ActorRef] = Set()
    var currentStats: Map[String, Int] = Map()

    override def receive: Receive = {
      case AggregateVotes(citizens) =>
        stillWaiting = citizens
        citizens.foreach(citizenRef => citizenRef ! VoteStatusRequest)
      case VoteStatusReply(None) =>
        // a citizen hasn't voted yet
        sender() ! VoteStatusRequest  // this might end up in a infinite loop when a citizen
                                      // hasn't voted. But in this test cases, all citizens have voted.
      case VoteStatusReply(Some(candidate)) =>
        val newStillWaiting = stillWaiting - sender()
        val currentVotesOfCandidate = currentStats.getOrElse(candidate, 0)
        currentStats = currentStats + (candidate -> (currentVotesOfCandidate + 1))
        if (newStillWaiting.isEmpty) {
          println(s"[aggregator pool stats: $currentStats")
        } else {
          stillWaiting = newStillWaiting
        }
    }
  }


  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  import Citizen._
  import VoteAggregator._

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))


  // SOLUTION 2 - WITH IMMUTABLE STATE
  object CitizenImmutable {
    case class Vote(candidate: String)
    case object VoteStatusRequest
    case class VoteStatusReply(candidate: Option[String])
  }

  class CitizenImmutable extends Actor {
    import Citizen._

    override def receive: Receive = {
      case Vote(c) => context.become(voted(c))
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def voted(candidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate))
    }
  }
  object VoteAggregatorImmutable {
    case class AggregateVotes(citizens: Set[ActorRef])
  }
  class VoteAggregatorImmutable extends Actor {
    import VoteAggregator._
    import Citizen._

    override def receive: Receive = awaitingCommand

    def awaitingCommand: Receive = {
      case AggregateVotes(citizens) =>
        citizens.foreach(citizenRef => citizenRef ! VoteStatusRequest)
        context.become(awaitingStatuses(citizens, Map())) // <- trick for initialize with Map as boolean
    }

    def awaitingStatuses(stillWaiting: Set[ActorRef], currentStats: Map[String, Int]): Receive = {
      case VoteStatusReply(None) =>
        // a citizen hasn't voted yet
        sender() ! VoteStatusRequest  // this might end up in a infinite loop when a citizen
      // hasn't voted. But in this test cases, all citizens have voted.
      case VoteStatusReply(Some(candidate)) =>
        val newStillWaiting = stillWaiting - sender()
        val currentVotesOfCandidate = currentStats.getOrElse(candidate, 0)
        val newStats = currentStats + (candidate -> (currentVotesOfCandidate + 1))
        if (newStillWaiting.isEmpty) {
          println(s"[aggregator immutable pool stats: $newStats")
        } else {
          // still need to process some statuses
          context.become(awaitingStatuses(newStillWaiting, newStats))
        }
    }

  }

  val alice2 = system.actorOf(Props[CitizenImmutable])
  val bob2 = system.actorOf(Props[CitizenImmutable])
  val charlie2 = system.actorOf(Props[CitizenImmutable])
  val daniel2 = system.actorOf(Props[CitizenImmutable])

  import Citizen._
  import VoteAggregator._

  alice2 ! Vote("Martin")
  bob2 ! Vote("Jonas")
  charlie2 ! Vote("Roland")
  daniel2 ! Vote("Roland")

  val voteAggregatorImmutable = system.actorOf(Props[VoteAggregatorImmutable])
  voteAggregatorImmutable ! AggregateVotes(Set(alice, bob, charlie, daniel))


  /*
    Print the status of the vote. For example:

    Martin -> 1
    Jonas -> 1
    Roland -> 2

   */
}
