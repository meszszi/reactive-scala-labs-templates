package EShop.lab5

import EShop.lab5.ProductCatalog.{GetItems, Items}
import akka.actor.{ActorSelection, ActorSystem}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.RootJsonFormat

import scala.concurrent.duration._

class ProductCatalogHTTPServer(system: ActorSystem) extends HttpApp with JsonSupport {
  implicit val marshalGetItems: RootJsonFormat[GetItems]        = jsonFormat2(ProductCatalog.GetItems)
  implicit val marshalItem: RootJsonFormat[ProductCatalog.Item] = jsonFormat5(ProductCatalog.Item)
  implicit val marshalItems: RootJsonFormat[Items]              = jsonFormat1(ProductCatalog.Items)

  implicit val timeout: Timeout = 5.seconds

  private val productCatalog: ActorSelection =
    system.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2553/user/productcatalog")

  override protected def routes: Route = {
    path("items") {
      post {
        entity(as[GetItems]) { query =>
          complete {
            (productCatalog ? query).mapTo[Items]
          }
        }
      }
    }
  }
}

object ProductCatalogHTTPServer extends App {
  private val config = ConfigFactory.load()
  private val system = ActorSystem("ProductCatalogHttp", config.getConfig("httpserver").withFallback(config))
  new ProductCatalogHTTPServer(system).startServer("localhost", 8001)
}
