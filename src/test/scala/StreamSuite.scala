import scala.collection.immutable.Queue
import scala.concurrent.*
import scala.concurrent.duration.*

class StreamSuite extends munit.FunSuite {

  import concurrent.ExecutionContext.Implicits.global

  def await[T](f: Future[T]): T = Await.result(f, 1.seconds)

  test("zip two sources together") {

    val s1 = Stream.apply(Seq.apply(1))
    val s2 = Stream.apply(Seq.apply(1))

    assertEquals(await(s1.zip(s2).next()), Some((1, 1)))
  }

  test("collect a list") {
    val s = Stream.apply(Seq(1, 1, 2, 2, 3, 3))
    assertEquals(await(s.collect[List[Int]]), List(1, 1, 2, 2, 3, 3))
  }

  test("collect a set") {
    val s = Stream.apply(Seq(1, 1, 2, 2, 3, 3))
    assertEquals(await(s.collect[Set[Int]]), Set(1, 2, 3))
  }

  test("backpressure is nothing fancy") {

    val q = scala.collection.mutable.Queue[Int](1,2,3)

    val s1 = new Stream[Int]:
      override def next()(implicit ctx: ExecutionContext): Future[Option[Int]] =
        Future.successful(q.dequeueFirst(_ => true))

    assertEquals(await(s1.next()), Some(1))

    // we have a really slow consumer..
    Thread.sleep(15)

    // q is unchanged.
    assertEquals(q.toSeq, Seq(2, 3))
  }

  test("foreach exhausts the source") {
    val s1 = Stream.apply(Seq.apply(1, 2, 3))
    assertEquals(await(s1.foreach(_ => Future.successful(()))), ())
    assertEquals(await(s1.next()), None)
  }

  test("fold exhausts the source") {
    val s1 = Stream.apply(Seq.apply(1, 2, 3))
    val sum = await(s1.fold(0, {case(a, b) => Future.successful(a+b)}))
    assertEquals(sum, 6)
    assertEquals(await(s1.next()), None)
  }

  test("concat eats one at a time") {
    val makeStream = (items: Seq[Int]) => {
      val q = scala.collection.mutable.Queue.apply(items: _*)

      (q, new Stream[Int] {
        override def next()(implicit ctx: ExecutionContext): Future[Option[Int]] =
          Future.successful(q.dequeueFirst(_ => true))
      })
    }

    val (q1, s1) = makeStream(Seq(1,2,3))
    val (q2, s2) = makeStream(Seq(4,5,6))
    val c1 = s1.concat(s2)

    val res1 = await(for {
      s11 <- c1.next()
      s12 <- c1.next()
      s13 <- c1.next()
    } yield (s11, s12, s13))

    assertEquals(res1, (Some(1),Some(2),Some(3)))
    assertEquals(q1.toSeq, Seq())
    assertEquals(q2.toSeq, Seq(4,5,6))

    val res2 = await(for {
      s21 <- c1.next()
      s22 <- c1.next()
      s23 <- c1.next()
    } yield (s21, s22, s23))

    assertEquals(res2, (Some(4), Some(5), Some(6)))
    assertEquals(q1.toSeq, Seq())
    assertEquals(q2.toSeq, Seq())
  }

  test("buffered runs multiple things at once") {
    val start = System.currentTimeMillis()

    await(Stream.apply(Seq(1,2,3,4,5,6,7,8))
      .flatMap(_ => Future {
        Thread.sleep(100)
      })
      .buffered(4)
      .collect[List[Unit]])

    assert((System.currentTimeMillis() - start) < 210)
  }
  
  test("map, flatMap, actually map things") {
    val res = await(Stream(Some(1))
      .flatMap(i => Future.successful(i * 2))
      .collect[List[Int]])

    assertEquals(res.head, 2)
    
    val res2 = await(Stream(Some(1))
      .map(i => i * 2)
      .collect[List[Int]])

    assertEquals(res2.head, 2)
  }

  test("skip some items and they never appear") {
    val s = Seq(1,2,3)
    assertEquals(
      await(Stream(Seq(1,2,3,4,5,6)).skip(2).collect[Seq[Int]]),
      Seq(3,4,5,6)
    )
  }
}
