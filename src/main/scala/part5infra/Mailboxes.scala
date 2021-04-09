package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("MailboxDemo")

  /**
   * Interesting case #1 - custom priority mailbox.
   *
   * We want to add some priority to mailbox queue.
   *
   * Use case: Support ticketing system.
   * In this scenario, we hacked a little script to all de-priritize
   * support tickets based on the ticket name.
   *
   * If the ticket name starts with:
   * P0 -> most important and needs to be handled first
   * P1
   * P2
   * P3 -> less important
   *
   */

  // step 1 - mailbox definition
  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(
      PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _ => 4
      })

  // step 2 - make it known in the config (see the application.conf)
  // step 3 - attach the dispatcher to an actor
  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("mailboxesDemo.support-ticket-dispatcher"))
  /* supportTicketLogger ! PoisonPill
  Thread.sleep(1000) */
  /*supportTicketLogger ! "[P3] this thing would be nice to have"
  supportTicketLogger ! "[P0] this needs to be solved NOW"
  supportTicketLogger ! "[P1] do this when you have the time"*/

  // after which time can I send another message and be prioritized accordingly?
  // how long is the wait?
  // The sad answer is that neither can you not nor can you configure the wait
  // because when a thread is allocated to dequeue messages from this actor
  // whatever is put on the queue in that particular order which is ordered by
  // the mailbox will get handled.

/**
 * Interesting case #2 - control-aware mailbox
 *
 * The problem that we want to solve is that some messages need to be processed
 * first regardless of what's queue in the mailbox.
 *
 * we'll use UnboundedControlAwareMailbox
 * */
  // step 1 - mark important messages as control messages
  case object  ManagementTicket extends ControlMessage

  /*
    step 2 - configure who gets the mailbox
    - make the actor attach to the mailbox
   */
  val systemConfig = ActorSystem("MailboxDemoConfig", ConfigFactory.load().getConfig("mailboxesDemo"))

  // method #1 - attaching an actor with a control mailbox
  val controlAwareActor = systemConfig.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))

/*  controlAwareActor ! "[P0] this needs to be solved NOW"
  controlAwareActor ! "[P1] do this when you have the time"
  controlAwareActor ! ManagementTicket*/

  // method #2 - using deployment config
  val alternativeControlAwareActor = systemConfig.actorOf(Props[SimpleActor], "altControlAwareActor")
  alternativeControlAwareActor ! "[P0] this needs to be solved NOW"
  alternativeControlAwareActor ! "[P1] do this when you have the time"
  alternativeControlAwareActor ! ManagementTicket


}
