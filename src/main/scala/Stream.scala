import scala.collection.{Factory, mutable}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.collection.mutable.Queue

object Stream {

  /** Constructs a Stream from the iterable. Note that if the provided iterable
    * blocks on iteration then so will the returned stream
    */
  def apply[T](iterable: Iterable[T]): Stream[T] = {
    val iterator = iterable.iterator
    new Stream[T] {
      override def next()(implicit ctx: ExecutionContext): Future[Option[T]] =
        Future.successful(iterator.nextOption())
    }
  }

}

trait Stream[T] {

  /** Fold each item into an initial value */
  def fold[U](init: U, f: (U, T) => Future[U])(implicit
      ctx: ExecutionContext
  ): Future[U] = {
    def inner(acc: U): Future[U] =
      this.next().flatMap {
        case Some(t) => f(acc, t).flatMap(u => inner(u))
        case None    => Future.successful(acc)
      }

    inner(init)
  }

  /** Run the provided function on each item in the source */
  def foreach(
      f: T => Future[Unit]
  )(implicit ctx: ExecutionContext): Future[Unit] = {
    def inner(): Future[Unit] =
      this.next().flatMap {
        case Some(t) => f(t).flatMap(_ => inner())
        case None    => Future.successful(())
      }

    inner()
  }

  /** Transforms a source into a collection, returning a future representing the
    * result of that computation. The returned future will be resolved when the
    * stream is exhausted.
    */
  def collect[C](implicit
      factory: Factory[T, C],
      ctx: ExecutionContext
  ): Future[C] = {
    def inner(
        collection: mutable.Builder[T, C]
    ): Future[mutable.Builder[T, C]] = {
      this
        .next()
        .flatMap {
          case Some(t) => inner(collection.addOne(t))
          case None    => Future.successful(collection)
        }
    }

    inner(factory.newBuilder).map(_.result())
  }

  /** Skips the first `n` items of the stream * */
  def skip(n: Int): Stream[T] = {
    val self = this
    new Stream[T] {
      var init = false;

      override def next()(implicit ctx: ExecutionContext): Future[Option[T]] = {
        if (!init) {
          (1 to n).foreach(_ => self.next())
          init = true
        }

        self.next()
      }
    }
  }

  /** Concatenates the `other` stream to this. This stream will be fully
    * exhausted before items are pulled from `other`
    */
  def concat(other: Stream[T]): Stream[T] = {
    val self = this
    new Stream[T] {
      var inner: Stream[T] = self
      def next()(implicit ctx: ExecutionContext): Future[Option[T]] = {
        inner.next().flatMap {
          case Some(t) => Future.successful(Some(t))
          case None =>
            inner = other
            inner.next()
        }
      }
    }
  }

  /** Zips items from the `other` stream with this. This stream will be
    * exhausted when either this or `other` is.
    */
  def zip[U](other: Stream[U]): Stream[(T, U)] = {
    val self = this
    new Stream[(T, U)] {

      def next()(implicit ctx: ExecutionContext): Future[Option[(T, U)]] = {
        for {
          t <- self.next()
          u <- other.next()
        } yield {
          for {
            t <- t
            u <- u
          } yield {
            (t, u)
          }
        }
      }
    }
  }

  /** Buffers `n` items from the stream */
  def buffered(n: Int): Stream[T] = {
    val self = this
    new Stream[T] {
      val q = new mutable.Queue[Future[Option[T]]](n)

      // noinspection NoTailRecursionAnnotation
      override def next()(implicit ctx: ExecutionContext): Future[Option[T]] = {
        q.dequeueFirst(_ => true) match {
          // yield first item and enqueue another
          case Some(f) =>
            f.onComplete(_ => synchronized(q.enqueue(self.next())))
            f

          // first pull, need to enqueue `n` items
          case None =>
            val first = self.next()
            (0 to n).foreach(_ => q.enqueue(self.next()))
            first
        }
      }
    }
  }

  /** Transforms the items in the stream by applying the provided function to
    * values when they are ready.
    */
  def map[U](f: T => U): Stream[U] = {
    val self = this

    new Stream[U] {
      def next()(implicit ctx: ExecutionContext): Future[Option[U]] = {
        self.next().flatMap {
          case Some(t) => Future.successful(Some(f(t)))
          case None    => Future.successful(None)
        }
      }
    }
  }

  /** Transforms the items in the stream by applying the provided function to
    * values when they are ready. The stream will be able to yield whenever the
    * function's Future is ready.
    */
  def flatMap[U](f: T => Future[U]): Stream[U] = {
    val self = this

    new Stream[U] {
      def next()(implicit ctx: ExecutionContext): Future[Option[U]] = {
        self.next().flatMap {
          case Some(t) => f(t).map(Some(_))
          case None    => Future.successful(None)
        }
      }
    }
  }

  /** Retrieves a future containing the next value from the stream. `None` is
    * contained if the stream is exhausted
    */
  def next()(implicit ctx: ExecutionContext): Future[Option[T]]
}
