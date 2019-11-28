package EShop.lab4

import EShop.lab2.{Cart, Checkout}
import akka.actor.{Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PersistentCartActor {

  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5.seconds

  private def scheduleTimer: Cancellable =
    context.system.scheduler
      .scheduleOnce(cartTimerDuration, self, ExpireCart)

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {

    val newState = event match {
      case CartExpired | CheckoutClosed => empty

      case CheckoutCancelled(cart) => nonEmpty(cart, scheduleTimer)

      case ItemAdded(item, cart) =>
        nonEmpty(cart.addItem(item), timer.getOrElse(scheduleTimer))

      case CartEmptied =>
        if (timer.isDefined)
          timer.get.cancel()
        empty

      case ItemRemoved(item, cart) =>
        if (timer.isDefined)
          timer.get.cancel()
        nonEmpty(cart.removeItem(item), scheduleTimer)

      case CheckoutStarted(checkoutRef, cart) =>
        if (timer.isDefined)
          timer.get.cancel()
        checkoutRef ! Checkout.StartCheckout
        inCheckout(cart)
    }

    context become newState
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart.empty))(updateState(_))

    case GetItems =>
      sender() ! Seq.empty
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, cart))(updateState(_, Some(timer)))

    case RemoveItem(item) =>
      if (cart.contains(item)) {
        val newCart = cart.removeItem(item)
        if (newCart.size == 0)
          persist(CartEmptied)(updateState(_, Some(timer)))
        else
          persist(ItemRemoved(item, cart))(updateState(_, Some(timer)))
      } else {
        log.debug(s"Trying to remove non-existing item from cart: $item")
      }

    case StartCheckout =>
      val checkoutActor = context.system.actorOf(Checkout.props(self))
      persist(CheckoutStarted(checkoutActor, cart)) { event =>
        sender() ! CheckoutStarted(checkoutActor, cart)
        updateState(event, Some(timer))
      }

    case ExpireCart =>
      persist(CartExpired)(updateState(_, Some(timer)))

    case GetItems =>
      sender() ! cart.items
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      persist(CheckoutCancelled(cart))(updateState(_))

    case CloseCheckout =>
      persist(CheckoutClosed)(updateState(_))
  }

  override def receiveRecover: Receive = LoggingReceive {
    case event: Event => updateState(event)
  }
}
