package EShop.lab4

import EShop.lab2.CartActor
import EShop.lab3.Payment
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration     = 1.seconds

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {

    val newState = event match {
      case CheckoutStarted =>
        selectingDelivery(scheduler.scheduleOnce(timerDuration, self, ExpireCheckout))

      case DeliveryMethodSelected(method) =>
        selectingPaymentMethod(maybeTimer.getOrElse(scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)))

      case CheckOutClosed =>
        if (maybeTimer.isDefined)
          maybeTimer.get.cancel()
        closed

      case CheckoutCancelled =>
        if (maybeTimer.isDefined)
          maybeTimer.get.cancel()
        cancelled

      case PaymentStarted(payment) =>
        if (maybeTimer.isDefined)
          maybeTimer.get.cancel()
        processingPayment(scheduler.scheduleOnce(timerDuration, self, ExpirePayment))
    }

    context become newState
  }

  def receiveCommand: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted)(updateState(_))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      persist(DeliveryMethodSelected(method))(updateState(_, Some(timer)))

    case CancelCheckout =>
      persist(CheckoutCancelled)(updateState(_, Some(timer)))

    case ExpireCheckout =>
      persist(CheckoutCancelled)(updateState(_))
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(payment) =>
      val paymentActor = context.system.actorOf(Payment.props(payment, sender(), self))
      persist(PaymentStarted(paymentActor)) { event =>
        sender() ! PaymentStarted(paymentActor)
        updateState(event, Some(timer))
      }

    case CancelCheckout =>
      persist(CheckoutCancelled)(updateState(_, Some(timer)))

    case ExpireCheckout =>
      persist(CheckoutCancelled)(updateState(_))
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ReceivePayment =>
      persist(CheckOutClosed) { event =>
        cartActor ! CartActor.CloseCheckout
        updateState(event, Some(timer))
      }
      cartActor ! CartActor.CloseCheckout

    case CancelCheckout =>
      persist(CheckoutCancelled)(updateState(_, Some(timer)))

    case ExpirePayment =>
      persist(CheckoutCancelled)(updateState(_))
  }

  def cancelled: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted)(updateState(_))
  }

  def closed: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted)(updateState(_))
  }

  override def receiveRecover: Receive = LoggingReceive {
    case event: Event => updateState(event)
  }
}
