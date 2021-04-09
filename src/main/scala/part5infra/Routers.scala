package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, FromConfig, RoundRobinGroup, RoundRobinPool, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory

object Routers extends App {

  /**
    Method #1 - manual router
   */
  class Master extends Actor {
    // Step 1 - Create routees
    // Creating 5 actor routees based off Slave Actor
    private val slaves = for(i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"Slave_$i")
      context.watch(slave)
      ActorRefRoutee(slave)
    }

    // Step 2 -  define router
    /*
      Supported options for routing logic:
       - round robin: cycles between routees

       - random: random

       - smallest mailbox: it always sends the next message to the actor
                          with the fewest messages in the queue

       - broadcast: It's a redundancy measure. This sends the same message
                    to all the routees.

       - scatter-gather-first: it broadcasts so it sends to everyone and waits
                              for the first reply and all the next replies are discarded.

        - tail-chopping: it forwards the next message to each actor sequentially until
                        first reply was received and all the other replies are discarded.

        - consistent-hashing: all message with the same hash get to the same actor
     */
    private var router = Router(RoundRobinRoutingLogic(), slaves)

    override def receive: Receive = {
      // step 3 - route the messages
      case message =>
        router.route(message, sender()) // passing the `sender()`, Slave actors can
                                        // reply directly to main sender without involving
                                        // the `Master` Actor. However, you can pass the Master
                                        // Actor if Master acts like a middleman.
      // step 4 -handle the termination/lifecyle of the routees
      case Terminated(ref) =>
        /*
        * Let's assume for this example that if one of my slaves dies, I'll replace it
        * with another slave. So the way that I'm going to do that is:
        *
        * 1. Remove the routee from the router
        * 2. Create a new slave to replace it
        * 3. Register the new slave to the router
        * */
        router = router.removeRoutee(ref)
        val newSlave = context.actorOf(Props[Slave])
        context.watch(newSlave)
        router.addRoutee(newSlave)
    }
  }

  class Slave extends Actor with ActorLogging {

    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("Routes")
  val master = system.actorOf(Props[Master])

/*  for (i <- 1 to 10) {
    master ! s"[$i] Hello from the world"
  }*/


  /**
    Method #2 - a router with its won children
                as known as POOL router
   */
    // 2.1 programmatically (in code)
  val poolMaster = system.actorOf(RoundRobinPool(5).props(Props[Slave]), "simplePoolMaster")
/*  for (i <- 1 to 10) {
      poolMaster ! s"[$i] Hello from the world"
  }*/

  // 2.2 from configuration
  val systemWithConfig = ActorSystem("RoutesWithConfig", ConfigFactory.load().getConfig("routersDemo"))
  val poolMaster2 = systemWithConfig.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
/*  for (i <- 1 to 10) {
      poolMaster ! s"[$i] Hello from the world"
  }*/

  /**
    Method #3 - router with actors created elsewhere
                as known as GROUP router
   */
    // let's assume that in another part of my application, I created myself
    // five instances of slave
  val slaveList = (1 to 5).map(i => system.actorOf(Props[Slave], s"slave_$i")).toList // Somebody created these
                                                                                      // actors for me and I want
                                                                                      // to create a group router
                                                                                      // around them

  // I need their paths
  val slavePaths = slaveList.map(slaveRef => slaveRef.path.toString)

  // 3.1 in the code
  val groupMaster = system.actorOf(RoundRobinGroup(slavePaths).props())
/*  for (i <- 1 to 10) {
    groupMaster ! s"[$i] Hello from the world"
  }*/

  // 3.2 from configuration
  val slaveListGroup = (1 to 5).map(i => systemWithConfig.actorOf(Props[Slave], s"slave_$i")).toList
  val groupMaster2 = systemWithConfig.actorOf(FromConfig.props(), "groupMaster2")

  for (i <- 1 to 10) {
    groupMaster2 ! s"[$i] Hello from the world"
  }


  /**
   * Special messages
   */
  groupMaster2 ! Broadcast("hello, everyone") // 1. this message will be sent to every single routee actor
                                              // regardless of the routing strategy

  // 2. PoisonPill and Kill are NOT routed: They are handled by the routing actor.
  // 3. There are management message such as AddRoutee, RemoveRoutee, GetRoutee, which are
  //    handle only by the routing actor


}
