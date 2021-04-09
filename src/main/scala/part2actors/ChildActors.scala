package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChildActors.CreditCard.{AttachToAccount, CheckStatus}
import part2actors.ChildActors.Parent.{CreateChild, TellChild}


object ChildActors extends App {
  // Actors can create other actors

  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }

  class Parent extends Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} Creating child")
        // how to create a new actor right HERE
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
    }

    def withChild(childRef: ActorRef): Receive = {
      case TellChild(message) =>
        if (childRef != null) childRef forward message
    }

  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(s"${self.path} I got: $message")
    }
  }

  val system = ActorSystem("ParentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")

  parent ! CreateChild("child")
  parent ! TellChild("hey kid")

  // actor hierarchies
  // parent -> child -> grandChild
  //        -> child2

  /*
    `child` is owned by parent, but who owns parent? is parent some
    kind of top level actor? The answer is no. Akka has Guardian actors
    or top level actors. Every Akka actor system has three Guardians.

    Guardian actors (top-level):
      - /system = system guardian: Every actor system has its own actors for
                  managing various things and for example managing the logging
                  system and Akka is also implemented using actors and /system
                  manages all these system actors.

      - /user = user-level guardian: Every actor that we craeted, using `system.actorOf`,
                                      is actually owned by this `/user` guardian which
                                      explains the path of the below code (note the `/user`
                                      inside of the path):

                                      akka://ParentChildDemo/user/parent

                                      `/user` is the top level Guardian actor for every single
                                      actor that we as programmers create.

      - /  = root guardian: The root guardian manages both the system guardian (/system) and the
                            user guardian (/user). The root guardian sets at the level of the actor
                            system itself. So, if the root guardian is an exception or dies in some
                            other way, the whole actor system is break down.

   */

  /**
   * Feature Akka offers called `Actor Selection`
   */
  val childSelection = system.actorSelection("/user/parent/child")
  childSelection ! "I found you"

  /***
   * DANGER!
   *
   * NEVER PASS MUTABLE ACTOR STATE, OR THE `THIS` REFERENCE, TO CHILD ACTORS
   *
   * NEVER IN YOUR LIFE.
   *
   * This has de danger of breaking actor encapsulation: because the child actor
   * suddenly has access to the internals of the parent actor, so it can mutate the
   * state or directly call methods of the parent actor without sending a message.
   * And this breaks are very sacred actor principles. See the following example:
   *
   */

  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAccount
  }

  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._

    var amount = 0

    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachToAccount(this) // !! WRONG: When you use the `this` reference in a message
                                              // You're basically exposing yourself to method call from other
                                              // actors which basically means methods calls from other threads.
                                              // That means that you're exposing yourself to concurrency issues which
                                              // we wanted to avoid in the first place. This is breaking the actor
                                              // encapsulation and every single actor principle.
      case Deposit(funds) => deposit(funds)
      case Withdraw(funds) => withdraw(funds)
    }

    def deposit(funds: Int) = {
      println(s"${self.path} depositing $funds on top of $amount")
      amount += funds
    }
    def withdraw(funds: Int) = {
      println(s"${self.path} withdrawing $funds on from $amount")
      amount -= funds
    }
  }

  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) // !! This is questionable !!
                                                              // a legal definition would be:
                                                              // case class AttachToAccount(bankAccountRef: ActorRef)
    case object CheckStatus
  }
  class CreditCard extends  Actor {
    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attached(account))
    }

    def attached(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
      println(s"${self.path} your message has been processed")
      // benign
      account.withdraw(1) // because I can => here is the problem: we have directly
                                // called a method on it. WRONG in many levels.
                                // 1. This using a method directly from NaiveBankAccount
                                //    by CreditCard Actor
                                // 2. CreditCard Actor doesn't use messages, so this bypasses
                                //    all the logic and the potential security checks that it
                                //    that it would normally do received by `receive` message handler.
                                //
                                // It's extremely hard to debug, especially in a critical environment
                                // like a banking system.
        //
        // this problem extends to both the `this`
        // reference and any mutable state of an actor.
        // This is called `closing over` mutable state or `this` reference.
    }
  }

  import NaiveBankAccount._
  import CreditCard._

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "account")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  // Then, I'm going to look for the child actor of the credit card
  // because I want to send some message directly
  Thread.sleep(500) // <= to make sure that this actor has been created by the bankAccount Actor

  val ccSelection = system.actorSelection("/user/account/card")
  ccSelection ! CheckStatus

  /**
   * CONCLUSION: NEVER NEVER `close over`` mutable state or `this` reference
   */

}
