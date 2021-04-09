package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorsIntro extends App {

  // part 1 - actor systems
  val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)

  /*
  * - Actors are uniquely identified within an actor system
  * - Messages are passed and processed asynchronously
  * - Each actor has a unique behavior or unique way of processing
  *   the message
  * - Actors are (really) encapsulated: you cannot ever invade the
  *   other actor.
  *
  * */
  // part 2 - create actors

  // word count actor
  class WordCountActor extends Actor {
    // internal data
    var totalWords = 0

    // behavior (or the receive handler that akka will invokes)
    // PartialFunction[Any, Unit] has an aliases called `Receive`
    def receive: PartialFunction[Any, Unit] = {
      case message: String =>
        println(s"[word counter] I have received: $message")
        totalWords += message.split(" ").length
      case msg => println(s"[word counter] I cannot understand ${msg.toString}")
    }
  }

  // part3 - instantiate our actor

  /*
  * the difference between actors and normal object in Akka is that
  * you cannot instantiate an actor by calling `new`, but by using
  * invoking the actor system
  *
  *     val wc = new WordCountActor <-- That doesn't work.
  *
  * */
  // the way actor system instantiates an actor
  val wordCounter: ActorRef = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
  val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")

  // how instantiate a actor with params?
  class Person (name: String) extends Actor {
    override def receive: Receive = {
      case "hi" => println(s"Hi, my name is $name")
      case _ =>
    }
  }

  val person = actorSystem.actorOf(Props(new Person("Andres")))
  // the instantiation of `new Person` inside Props apply method is legal, but this is
  // also discouraged. The best practice for create objects with constructor arguments
  // is create a companion object


  object PersonBest {
    def props(name: String) = Props(new Person(name)) // this has the advantage that we did not create akka instances ourselves
                                                      // but the factory method creates props with actor instances for us
  }
  class PersonBest (name: String) extends Actor {
    override def receive: Receive = {
      case "hi" => println(s"Hi, my name is $name")
      case _ =>
    }
  }
  val personBest = actorSystem.actorOf(PersonBest.props("Jose"))

  // part4 - communicate!

  wordCounter ! "I am learning Akka and it's pretty damn cool!"
  anotherWordCounter ! "A different message"
  // <- sending a message is completely asynchronous
  // ! methods is also known as `tell`

  person ! "hi"
  personBest ! "hi"



}
