package pack1recap

object ThreadModelLimitations extends App {

  /**
   * Andres' rants (AR)
   */

  /**
   * AR #1: OOP encapsulation is only valid in the SINGLE THREADED MODEL
   */
  class BankAccount(private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int) = this.amount -= money
    def deposit(money: Int) = this.amount += money
    def getAmount = amount
  }

  val account = new BankAccount(2000)
  for (_ <-1 to 1000) {
    new Thread(() => account.withdraw(1)).start()
  }

  for (_ <-1 to 1000) {
    new Thread(() => account.deposit(1)).start()
  }

  println(account.getAmount) // change in every single execution because of threads
  /*
  * Everytime that is executed the current code (so line 29), the println
  * displays different results: sometimes 2000, others 1999, 1997, and so on.
  *
  * OOP encapsulation is broken in a multithreaded environment.
  * So, OOP encapsulation also involves syncronization (synchronization to the rescue)
  * */

  class BankAccountSync(private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int) = this.synchronized{
      this.amount -= money
    }
    def deposit(money: Int) = this.synchronized {
      this.amount += money
    }

    def getAmount = amount
  }

  val accountSync = new BankAccountSync(2000)
  for (_ <-1 to 1000) {
    new Thread(() => account.withdraw(1)).start()
  }

  for (_ <-1 to 1000) {
    new Thread(() => account.deposit(1)).start()
  }

  println(accountSync.getAmount) // Always is 2000

  /*
  * synchronization seems to solve this problem, but they introduce some
  * other problems like: deadlocks, livelocks
  *
  * When you get more complex structures in your applications, and deadlocks with
  * distributed data structures or resources, then the app blows up.
  * Also, locking those is orders magnitude slower.
  * */

  /*
   * Conclusion: We need a data structure that would be fully encapsulated in a multi-threaded
   * environment or even in a distributed environment without the use of locking.
   */

  /**
   * AR #2: Delegating something to a thread is a PAIN
   */
  // you have a running thread and you want to pass a runnable to the thread. For example,

  var task: Runnable = null

  val runningThread: Thread = new Thread(() => {
    while(true) {
      while (task == null) {
        runningThread.synchronized {
          println("[background] waiting for a task")
          runningThread.wait()
        }
      }

      task.synchronized {
        println("[background] I have a task!")
        task.run()
        task = null
      }
    }
  })

  def delegateToBackgroundThread(r: Runnable) = {
    if (task == null) task = r
      runningThread.synchronized{
        runningThread.notify();
      }
    }

  runningThread.start()
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println(42))
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println("this should be run in the background"))

  /*
  * The below code is complex and becomes more complex in bigger applications.
  *
  * Other problems:
  *
  *  - What if you need to send some other kind of signals for example run this every X seconds
  *  - What if there are multiple background tasks. How do you identify which thread gets which
  *    task from the running thread
  *  - From the running thread, how do you identify who gave you the signal
  *  - What if the background thread gets stuck or throws some kind of exception. What do you do?
  *
  * */

  /*
  * Conclusion: We need a data structure that can safely receive any kind of signal that can identify
  * who gave that sign and be itself easily identifiable and that can guard itself against failure.
  * */

  /**
   * AR #3: tracing and dealing with errors in a multithreaded environment is a "Pain in the neck"
   */
  // 1 million number in between 10 threads

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future

  val futures = (0 to 9)
    .map(i => 100000 * i until 100000 * (i + 1)) // 0 - 99.999, 100.000 - 199.999, 200.000 - 299.999, ...
    .map(range => Future {
      if (range.contains(546735)) throw new RuntimeException("invalid number") // simulating an error
      range.sum
    })

  val sumFuture = Future.reduceLeft(futures)(_+_) // Future with the sum of all the numbers

  sumFuture.onComplete(println)

  /*
  * Debugging in multi-threaded and distributed systems is a pain in the neck. Finding the source
  * of this error in a big application is hard (almost impossible).
  * */

  /**
   * Conclusions:
   *
   * OOP is not encapsulated
   *   - race conditions
   *
   * Locks to the rescue?
   *    - deadlocks, livelocks => headaches
   *    - a massive pain in distributed environments
   *
   * Delegating tasks
   *   - hard, error-prone
   *   - never feels "first-class" altouhg often needed
   *   - should never be done in a blocking fashion
   *
   * Dealing with error
   *  - a monumental task in even samll systems
   *
   * */

}
