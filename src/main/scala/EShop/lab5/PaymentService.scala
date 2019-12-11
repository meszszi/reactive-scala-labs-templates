package EShop.lab5

import EShop.lab5.PaymentService.{PaymentClientError, PaymentServerError, PaymentSucceeded}
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.pattern.PipeToSupport

object PaymentService {

  case object PaymentSucceeded // http status 200
  class PaymentClientError extends Exception // http statuses 400, 404
  class PaymentServerError extends Exception // http statuses 500, 408, 418

  def props(method: String, payment: ActorRef) = Props(new PaymentService(method, payment))

}

class PaymentService(method: String, payment: ActorRef) extends Actor with ActorLogging with PipeToSupport {

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  import context.dispatcher
  private val http = Http(context.system)
  private val URI  = getURI

  override def preStart(): Unit = http.singleRequest(HttpRequest(uri = URI)).pipeTo(self)

  override def receive: Receive = {
    case HttpResponse(statusCode, _, _, _) =>
      statusCode match {

        case StatusCodes.OK =>
          payment ! PaymentSucceeded
          self ! PoisonPill

        case StatusCodes.BadRequest | StatusCodes.NotFound =>
          throw new PaymentClientError

        case StatusCodes.InternalServerError | StatusCodes.RequestTimeout | StatusCodes.ImATeapot =>
          throw new PaymentServerError
      }

    case Status.Failure(exception) => throw exception
  }

  private def getURI: String = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }
}
