package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  private def scheduleTimer(duration: FiniteDuration, msg: Any): Cancellable =
    scheduler.scheduleOnce(duration, self, msg)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  override def receive: Receive = notStarted

  private def notStarted: Receive = {
    case StartCheckout =>
      log.debug("Starting checkout")
      val checkoutTimer = scheduleTimer(checkoutTimerDuration, ExpireCheckout)
      context become selectingDelivery(checkoutTimer)
  }

  def selectingDelivery(timer: Cancellable): Receive = {
    case CancelCheckout =>
      log.debug("Cancelling checkout")
      timer.cancel()
      context become cancelled

    case ExpireCheckout =>
      log.debug("Checkout timer expired")
      context become cancelled

    case SelectDeliveryMethod(method) =>
      log.debug(s"Selecting delivery method: $method")
      context become selectingPaymentMethod(timer)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = {
    case CancelCheckout =>
      log.debug("Cancelling checkout")
      timer.cancel()
      context become cancelled

    case ExpireCheckout =>
      log.debug("Checkout timer expired")
      context become cancelled

    case SelectPayment(payment) =>
      log.debug(s"Selecting payment: $payment")
      timer.cancel()
      val paymentTimer = scheduleTimer(paymentTimerDuration, ExpirePayment)
      context become processingPayment(paymentTimer)
  }

  def processingPayment(timer: Cancellable): Receive = {
    case CancelCheckout =>
      log.debug("Cancelling checkout")
      timer.cancel()
      context become cancelled

    case ExpirePayment =>
      log.debug("Payment timer expired")
      context become cancelled

    case ReceivePayment =>
      log.debug("Receiving payment")
      timer.cancel()
      context become closed
  }

  def cancelled: Receive = notStarted

  def closed: Receive = notStarted
}
