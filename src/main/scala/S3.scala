//import scala.jdk.FutureConverters.*
//import org.reactivestreams.{Subscriber, Subscription}
//import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
//import software.amazon.awssdk.core.ResponseBytes
//import software.amazon.awssdk.core.async.{
//  AsyncResponseTransformer,
//  ResponsePublisher
//}
//import software.amazon.awssdk.regions.Region
//import software.amazon.awssdk.services.s3.S3AsyncClient
//import software.amazon.awssdk.services.s3.model.{
//  GetObjectRequest,
//  GetObjectResponse
//}
//
//import java.nio.ByteBuffer
//import java.util.concurrent.{CompletableFuture, ConcurrentLinkedQueue}
//import scala.collection.mutable
//import scala.concurrent.{ExecutionContext, Future}
//import scala.util.Try
//
//
//object S3 {
//
//  private val client: S3AsyncClient =
//    S3AsyncClient
//      .builder()
//      .credentialsProvider(DefaultCredentialsProvider.create())
//      .region(Region.US_EAST_2)
//      .build();
//
//  class ObjectBody() extends Stream[ByteBuffer] {
//
//    val subscription: Option[Subscription] = None
//    val queue = new ConcurrentLinkedQueue[ByteBuffer]()
//    var bufferedN: Int = 1
//
//    val subscriber: Subscriber[ByteBuffer] = new Subscriber[ByteBuffer] {
//      override def onComplete(): Unit = {}
//      override def onError(t: Throwable): Unit = ??? // FIXME
//
//      override def onNext(t: ByteBuffer): Unit = {
//        queue.add(t)
//        subscription.foreach(_.request(bufferedN))
//      }
//
//      override def onSubscribe(s: Subscription): Unit = {
//        s.request(bufferedN)
//      }
//    }
//
//    override def buffered(n: Int): Stream[ByteBuffer] = {
//      bufferedN = n
//      this
//    }
//
//    override def next()(implicit
//        ctx: ExecutionContext
//    ): Future[Option[ByteBuffer]] =
//      // FIXME: None doesn't mean the source is exhausted
//      // It probably just needs polled again. Stream is exhausted when onComplete/onError are called _and_
//      // the queue is empty, I think!
//      Future.successful(Try(queue.remove()).map(Some.apply).getOrElse(None))
//  }
//
//  // FIXME: just offer up ObjectBody, and call the getObjectRequest on the first `next()`
//  // has the added benefit of adding more proper buffereding in the first subscription.request(),
//  // and an overall simpler API. But, consider... the user may want the object metadata
//  // which we are deliberately discarding..
//  // `.subscribe` doesn't need to happen now, it could still be later, and we
//  // have signature Future[(Metadata, Body)]
//  def getObjectBody(bucket: String, key: String): Future[Stream[ByteBuffer]] = {
//    val objectRequest: GetObjectRequest =
//      GetObjectRequest
//        .builder()
//        .bucket(bucket)
//        .key(key)
//        .build()
//
//    val futResponse: CompletableFuture[ResponsePublisher[GetObjectResponse]] =
//      client
//        .getObject(objectRequest, AsyncResponseTransformer.toPublisher)
//
//    futResponse.thenApply { resp =>
//      val source = new ObjectBody()
//      resp.subscribe(source.subscriber); source
//    }.asScala
//  }
//}
//
