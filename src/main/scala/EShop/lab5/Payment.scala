package EShop.lab5

import EShop.lab2.Checkout
import EShop.lab3.Payment.{DoPayment, PaymentConfirmed}
import EShop.lab5.Payment.{PaymentRejected, PaymentRestarted}
import EShop.lab5.PaymentService.PaymentSucceeded
import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, Terminated}
import akka.stream.StreamTcpException

import scala.concurrent.duration._

object Payment {

  case object PaymentRejected
  case object PaymentRestarted

  def props(method: String, orderManager: ActorRef, checkout: ActorRef) =
    Props(new Payment(method, orderManager, checkout))

}

class Payment(
  method: String,
  orderManager: ActorRef,
  checkout: ActorRef
) extends Actor
  with ActorLogging {

  override def receive: Receive = {
    case DoPayment =>
      context.actorOf(PaymentService.props(method, self))
    case PaymentSucceeded =>
      orderManager ! PaymentConfirmed
      checkout ! Checkout.ReceivePayment
  }

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 5.seconds) {
      case _: PaymentService.PaymentClientError | _: StreamTcpException =>
        notifyAboutRejection()
        Stop
      case _: PaymentService.PaymentServerError =>
        notifyAboutRestart()
        Restart
    }

  //please use this one to notify when supervised actor was stoped
  private def notifyAboutRejection(): Unit = {
    orderManager ! PaymentRejected
    checkout ! PaymentRejected
  }

  //please use this one to notify when supervised actor was restarted
  private def notifyAboutRestart(): Unit = {
    orderManager ! PaymentRestarted
    checkout ! PaymentRestarted
  }
}
