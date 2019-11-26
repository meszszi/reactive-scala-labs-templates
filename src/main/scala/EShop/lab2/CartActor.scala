package EShop.lab2

import EShop.lab2.CartActor._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command
  case object GetItems             extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef, cart: Cart) extends Event
  case class ItemAdded(itemId: Any, cart: Cart)                 extends Event
  case class ItemRemoved(itemId: Any, cart: Cart)               extends Event
  case object CartEmptied                                       extends Event
  case object CartExpired                                       extends Event
  case object CheckoutClosed                                    extends Event
  case class CheckoutCancelled(cart: Cart)                      extends Event

  def props() = Props(new CartActor())
}

class CartActor extends Actor {

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      log.debug(s"Adding item to cart: $item")
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)

    case GetItems =>
      sender ! Seq.empty
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      log.debug(s"Adding item to cart: $item")
      context become nonEmpty(cart.addItem(item), timer)

    case RemoveItem(item) =>
      if (cart.contains(item)) {
        log.debug(s"Removing item from cart: $item")
        val newCart = cart.removeItem(item)
        if (newCart.size == 0) {
          timer.cancel()
          context become empty
        } else {
          context become nonEmpty(newCart, timer)
        }
      } else {
        log.debug(s"Trying to remove non-existing item from cart: $item")
      }

    case ExpireCart =>
      log.debug(s"Expiring cart with items: ${cart.items.mkString(",")}")
      context become empty

    case StartCheckout =>
      log.debug(s"Starting checkout (cart items: ${cart.items.mkString(",")})")
      timer.cancel()
      val checkoutActor = context.system.actorOf(Checkout.props(self))
      checkoutActor ! Checkout.StartCheckout
      sender() ! CheckoutStarted(checkoutActor, cart)
      context become inCheckout(cart)

    case GetItems =>
      sender ! cart.items
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      log.debug(s"Cancelling checkout")
      context become nonEmpty(cart, scheduleTimer)
    case CloseCheckout =>
      log.debug(s"Closing checkout")
      context become empty
  }
}
