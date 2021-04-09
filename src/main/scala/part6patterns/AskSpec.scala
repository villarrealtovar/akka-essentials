package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// step 1 - import the ask and pipe
import akka.pattern.ask
import akka.pattern.pipe

class AskSpec extends TestKit(ActorSystem("AskSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
{
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import AskSpec._

  "An authenticator" should {
    authenticatorTestSuite(Props[AuthManager])
  }

  "An piped authenticator" should {
    authenticatorTestSuite(Props[PipedAuthManager])
  }

  def authenticatorTestSuite(props: Props) = {
    import AuthManager._

    "fail to authenticate a non-registered user" in {
      val authManager = system.actorOf(props)
      authManager ! Authenticate("jose", "javt")
      expectMsg(AuthFailure(AUTH_FAILURE_NOT_FOUND))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("jose", "javt")
      authManager ! Authenticate("jose", "iloveakka")
      expectMsg(AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT))
    }

    "successfully authenticate a registered user" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("jose", "javt")
      authManager ! Authenticate("jose", "javt")
      expectMsg(AuthSuccess)
    }
  }

}

/*
  Use case: User authentication

  Let's assume we have a little authentication manager actor which will handle
  user authentication via username and password and we are using a external library
  which holds a Key-Value Store inside of an Actor (which we're going to implement).
 */
object AskSpec {
  // assume this code is somewhere else in your app i.e. someone else wrote it.
  case class Read(key: String)
  case class Write(key: String, value: String)

  // KVActor means KeyValueActor
  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map())

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Trying to read the value at the key $key")
        sender() ! kv.get(key) // Option[String]
      case Write(key, value) =>
        log.info(s"Writing the value $value for the key $key")
        context.become(online(kv + (key -> value)))
    }
  }

  // user authenticator actor
  case class RegisterUser(username: String, password: String)
  case class Authenticate(username: String, password: String)
  case class AuthFailure(message: String)
  case object AuthSuccess
  object AuthManager {
    val AUTH_FAILURE_NOT_FOUND = "username not found"
    val AUTH_FAILURE_PASSWORD_INCORRECT = "password incorrect"
    val AUTH_FAILURE_SYSTEM = "system error"
  }
  class AuthManager extends Actor with ActorLogging {
    import AuthManager._

    protected val authDb = context.actorOf(Props[KVActor])
    // step 2 -logistics
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val executionContext: ExecutionContext = context.dispatcher

    override def receive: Receive = {
      case RegisterUser(username, password) =>
        authDb ! Write(username, password)
      case Authenticate(username, password) =>
        handleAuthentication(username, password)
    }

    def handleAuthentication(username: String, password: String) = {
      val originSender = sender()
      // step 3 - ask the actor
      val future = authDb ? Read(username)
      // step 4 - handle the future e.g with onComplete
      future.onComplete {
        // step 5  - most important
        // NEVER CALL METHODS ON THE ACTOR INSTANCE OR ACCESS MUTABLE STATE IN ONCOMPLETE.
        // avoid closing over the actor instance or mutable state
        case Success(None) => originSender ! AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Success(Some(dbPassword)) =>
          if (dbPassword == password) originSender ! AuthSuccess
          else originSender ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
        case Failure(_) => originSender ! AuthFailure(AUTH_FAILURE_SYSTEM)
      }
    }
  }

  /**
   * The functionality between AuthManager and PipedAuthManager is identical. But in practice,
   * always prefer the piped approach because this doesn't expose you to uncomplete callbacks.
   * where you can break the actor encapsulation
   */
  class PipedAuthManager extends AuthManager {
    import AuthManager._

    override def handleAuthentication(username: String, password: String): Unit = {
      // step 3 - ask the actor
      val future = authDb ? Read(username) // The future is Any (Future[Any]) because the compiler does not
                                            // know in advance what kind of message complete the future

      // step 4 - process the future until you get the responses you will send back.
      val passwordFuture = future.mapTo[Option[String]] // a Future[Option[String]]
      val responseFuture = passwordFuture.map {
        case None => AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Some(dbPassword) =>
          if (dbPassword == password) AuthSuccess
          else AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
      } // This would be a Future[AuthSuccess or AuthFailure]. For the compiler, this will be a
        // Future[Any] - will be completed with the response I will send back.

      // step 5 - pipe the resulting future to the actor you wan to send the result
      /*
        When the future completes, send the response to the actor ref in the arg list.
        And sending back the response from the responseFuture is done via:
       */
      responseFuture.pipeTo(sender())
    }
  }
}
