package monix.mdc

import monix.eval.Task
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.slf4j.MDC
import monix.execution.Scheduler.Implicits.global

import java.time.Instant

class MDCProductSpec extends AsyncWordSpec with Matchers {

  case class Other(irrelevant: String)

  case class MetadataContext(idString: String, idInt: Int, timestamp: Instant, other: Other)

  val genMetadaCtx: Gen[MetadataContext] = {
    for {
      idString <- Gen.identifier
      idInt <- Gen.choose(1, 9999)
      irrelevant <- Gen.identifier
    } yield MetadataContext(idString, idInt, Instant.now, Other(irrelevant))
  }

  "MonixMDC" should {

    "update context with field names" in {
      val metadataCtx = genMetadaCtx.sample.get

      Task.evalAsync(MonixMDCAdapter.updateContext(metadataCtx)) >>
        Task {
          MDC.get("idString") shouldBe metadataCtx.idString
          MDC.get("idInt") shouldBe metadataCtx.idInt.toString
          MDC.get("timestamp") shouldBe metadataCtx.timestamp.toString
          MDC.get("other") shouldBe metadataCtx.other.toString
        }
    }.runToFuture


    "does not update context with null values" in {
      val metadataCtx = MetadataContext(null, 1, null, Other(null))

      Task.evalAsync(MonixMDCAdapter.updateContext(metadataCtx)) >>
        Task {
          Option(MDC.get("idString")) shouldBe None
          Option(MDC.get("idInt")) shouldBe Some("1")
          Option(MDC.get("timestamp")) shouldBe None
          MDC.get("other") shouldBe metadataCtx.other.toString
        }
    }.runToFuture

  }

}
