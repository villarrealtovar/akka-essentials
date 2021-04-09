package pack1recap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object MultithreadingRecap extends App {

  // creating threads on the JVM

  val aThread = new Thread(new Runnable {
    override def run(): Unit = println("I'm running in parallel")
  })

  val bThread = new Thread(() => println("I'm running in parallel")) // Syntax sugar

  bThread.start()
  bThread.join()

  val threadHello = new Thread(() => (1 to 1000).foreach(_ => println("hello")))
  val threadGoodbye = new Thread(() => (1 to 1000).foreach(_ => println("goodbye")))

  // different runs produce different results
  threadHello.start()
  threadGoodbye.start()


  class BankAccount(private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int) = this.amount -= money
  }

  /*
    Initial BankAccount(10.000)

    Thread1 -> withdraw 1.000
    Thread2 -> withdraw 2.000

    // OS schedule and handle threads
    Thread1 -> this.amount = this.amount - ..... // PREEMPTED by the OS
    Thread2 -> this.amount = this.amount - 2.000  = 8.000
    Thread1 -> - 1.000 = 9.000 // OS starts again this thread

    => result = 9.000 // this is because the operation
                          this.amount = this.amount - 1.000 is NOT ATOMIC
                          ATOMIC means thread safe
                          That means no two threads can execute this at the
                          same time because we would get into these very funky results

  */

  // Ways to be thread safe
  class BankAccountSafeThread(@volatile private var amount: Int) { // 1. @volatiile
    override def toString: String = "" + amount

    def safeWithdraw(money: Int) = this.synchronized {  // 2. this.synchronized
      this.amount -= money
    }
  }

  // inter-thread communication on the JVM
  // wait - notify mechanism

  // Scala Futures

  val future = Future {
    // long computation - on a different thread
    42
  }

  future.onComplete {
    case Success(42) => println("I found the meaning of life")
    case Failure(_) => println("something happened with the meaning of the life")
  }

  val aProcessedFuture = future.map(_ + 1) // Future with 43
  val aFlatFuture = future.flatMap {
    value => Future(value + 2)
  } // Future with 42

  val filteredFuture = future.filter(_ % 2 == 0)

  // for comprehensions
  val aNonsenseFuture = for {
    meaningOfLife <- future
    filteredMeaning <- filteredFuture
  } yield meaningOfLife + filteredMeaning

  // andThen, recover/recoverWith

  // Promises
}
