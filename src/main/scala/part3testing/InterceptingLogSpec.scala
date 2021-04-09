package part3testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
 * Say you have a little online shop and you want to implement a checkout
 * flow in your online shop and you have a bunch of actors that are talking
 * one to another.
 *
    CheckoutActor receives commands to check out items
    in your online store and CheckoutActor will talk to two
    other actors: an actor called PaymentManager and other actor
    called FulfillmentManager.

    The PaymentManager will reply to the CheckoutActor with a
    Payment accepted or denied. If the payment is accepted, the
    CheckoutActor will talk to the FulfillmentManager to dispatch
    your order and then, when the CheckoutActor receives a reply
    from the FulfillmentManager, the CheckoutActor will revert back
    to its normal state where it can accept other checkout commands
    as well.
 */

object InterceptingLogSpec {


  case class Checkout(item:String, creditCard: String)
  case class AuthorizeCard(creditCard: String)
  case object PaymentAccepted
  case object PaymentDenied
  case class DispatchOrder(item: String)
  case object OrderConfirmed

  class CheckoutActor extends Actor {
    private val paymentManager = context.actorOf(Props[PaymentManager])
    private val fulfillmentManager = context.actorOf(Props[FulfillmentManager])

    override def receive: Receive = awaitingCheckout

    def awaitingCheckout: Receive = {
      case Checkout(item, card) =>
        paymentManager ! AuthorizeCard(card)
        context.become(pendingPayment(item))
    }

    def pendingPayment(item: String): Receive = {
      case PaymentAccepted =>
        fulfillmentManager ! DispatchOrder(item)
        context.become(pendingFulfillment(item))
      case PaymentDenied =>
        throw new RuntimeException("I can't handle this anymore")
    }

    def pendingFulfillment(item: String): Receive = {
      case OrderConfirmed => context.become(awaitingCheckout)
    }

  }

  class PaymentManager extends Actor {
    override def receive: Receive = {
      /*
      * Of course, the PaymentManager will have very complex logic for
      * validating a credit card. It will go to our payment processor,
      * it will query the bank account, and all that kind of stuff.
      *
      * Here, we're going to implement a very simple logic
      * */
      case AuthorizeCard(card) =>
        if (card.startsWith("0")) sender() ! PaymentDenied
        else {
          Thread.sleep(4000) // by default, TestKit's Interceptor waits for 3 seconds. But you can overwrite
                            // this timeout with filter-leeway (see application.conf)
          sender() ! PaymentAccepted
        }
    }
  }

  class FulfillmentManager extends Actor with ActorLogging {
    var orderId = 43
    override def receive: Receive = {
      case DispatchOrder(item: String) =>
        orderId += 1
        log.info(s"Order $orderId for item $item has been dispatched.")
        sender() ! OrderConfirmed
    }
  }

}

class InterceptingLogSpec extends TestKit(ActorSystem("InterceptingLogsSpec", ConfigFactory.load().getConfig("interceptingLogMessages")))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
{

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  /*
    Interception of log messages is very useful in integration tests when it's very hard
    to inject test probes into your actor architecture
   */
  import InterceptingLogSpec._

  val item = "I love Akka"
  val creditCard = "1234-1234-1234-1234"
  val invalidCreditCard = "0000-0000-0000-0000"
  "A checkout flow" should {
    "correctly log the dispatch of an order" in {
      EventFilter.info(pattern=s"Order [0-9]+ for item $item has been dispatched.", occurrences = 1) intercept {
        // our test code
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, creditCard)
      }
    }
  }

  "freak out if the payment is denied" in {
    EventFilter[RuntimeException](occurrences = 1) intercept {
      val checkoutRef = system.actorOf(Props[CheckoutActor])
      checkoutRef ! Checkout(item, invalidCreditCard)
    }
  }
}
