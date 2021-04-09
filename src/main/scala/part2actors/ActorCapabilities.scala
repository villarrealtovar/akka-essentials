package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.Person.LiveTheLife

object ActorCapabilities extends App{

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi!" => context.sender() ! "Hello, there!" // replying to a message
      case message: String => println(s"[$self] I have received $message")
      case number: Int => println(s"[simple actor] I have received a NUMBER: $number")
      case SpecialMessage(contents) => println(s"[simple actor] I have received something special $contents")
      case SendMessageToYourself(content) => self ! content
      case SayHelloTo(ref) => ref ! "Hi!"
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // keep the original sender of the WirelessPhoneMessage
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  // PRINCIPLE1: messages can be of any type: You can send strings, numbers, and any
  // primitive type by default, ...
  simpleActor ! "hello actor"
  simpleActor ! 39

  // but you can also define your own.
  case class SpecialMessage(content: String)
  simpleActor ! SpecialMessage("some special contents")

  // You can send any messages under two conditions:
  // a) messages must be IMMUTABLE: so nobody can/has to touch this message
  // b) messages must be SERIALIZABLE: means JVM can can transform it into a
  //    byte stream and send it to another JVM whether it's on the same machine
  //    or over the network. SERIALIZABLE is a Java interface and there are a number
  //    of serialization protocols: in practice, we often use `case classes` and
  //    `case objects` for messages.

  // PRINCIPLE2: Actor have information about their context and about themselves. So,
  // each actor has a member call `context`. The `context` is a complex data structure
  // that has references to information regarding to the environment that the actor runs.

  // Actor's context
  // context.self === self === `this` in OOP

  case class SendMessageToYourself(content: String)

  simpleActor ! SendMessageToYourself("I am an actor and I proud of it")

  // PRINCIPLE3: actors can REPLY to messages
  val andres = system.actorOf(Props[SimpleActor], "andres")
  val caro = system.actorOf(Props[SimpleActor], "caro")

  case class SayHelloTo(ref: ActorRef)

  andres ! SayHelloTo(caro)

  // who is the sender() when the sender is null (this happens in a "big context" ->
  // in another words, when a actor doesn't send the message to another one, but yes to
  // the context

  // PRINCIPLE4: Dead Letters
  andres ! "Hi!" // deadLetters is a fake actor which takes care to receive the messages
                 // that aren't sent to anyone. (It's like garbage pool of messages)

  // PRINCIPLE5: Forwarding Messages. We can devise some mechanism to make Andres forward
  //             a message to Caro by keeping the original sender. It something like
  //             "wireless phone" game.
  //
  // Forwarding means sending a message with the original sender.

  case class WirelessPhoneMessage(content: String, destinationRef: ActorRef)

  // Big Context (noSender) sends a message to andres, and andres actor forward the
  // message to caro actor, but keeping the original sender (Big Context or noSender)
  andres ! WirelessPhoneMessage("Hi", caro)


  /**
   * Exercises
   *
   * 1. a Counter actor with three messages:
   *     - Increment
   *     - Decrement
   *     - Print
   *
   *  2. a bank account as actor with the following messages:
   *     receives:
   *      - Deposit an amount
   *      - Withdraw an amount
   *      - Statement
   *
   *     reply with:
   *      - Success
   *      - Failure
   *
   *    Recommendation: bank account interacts with some other kind of actor
   * */
  // Solution for 1 exercise

  // DOMAIN of the Counter
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter extends  Actor {
    import Counter._

    var count = 0
    override def receive: Receive = {
      case Increment => count += 1
      case Decrement => count -= 1
      case Print => println(s"[counter] My current count is $count")
    }
  }


  val counter = system.actorOf(Props[Counter], "myCounter")
  /*
  counter ! Counter.Increment
  counter ! Counter.Increment
  counter ! Counter.Increment
  counter ! Counter.Decrement
  counter ! Counter.Print
  */

  import Counter._
  (1 to 5).foreach(_ => counter ! Increment)
  (1 to 2).foreach(_ => counter ! Decrement)
  counter ! Print

  // Solution for 2 exercise

  object BankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object Statement

    case class TransactionSuccess(message: String)
    case class TransactionFailure(reason: String)
  }

  class BankAccount extends Actor {
    import BankAccount._

    var funds = 0

    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount < 0) sender() ! TransactionFailure("invalid deposit amount")
        else {
          funds += amount
          sender() ! TransactionSuccess(s"successfully deposited $amount")
        }
      case Withdraw(amount) =>
        if (amount < 0) sender() ! TransactionFailure("invalid withdraw amount")
        else if (amount > funds) sender() ! TransactionFailure("insufficient funds")
        else {
          funds -= amount
          sender() ! TransactionSuccess(s"successfully withdrew $amount")
        }
      case Statement => sender() ! s"Your balances is $funds"
    }
  }

  object Person {
    case class LiveTheLife(account: ActorRef)
  }
  class Person extends Actor {
    import Person._
    import BankAccount._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(10000)
        account ! Withdraw(90000)
        account ! Withdraw(500)
        account ! Statement
      case message => println(message.toString)
    }
  }

  val account = system.actorOf(Props[BankAccount], "bankAccount")
  val person = system.actorOf(Props[Person], "billionaire")

  person ! LiveTheLife(account)


}
