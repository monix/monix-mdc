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

    "sets the context with the given product fields" in {
      val metadataCtx = genMetadaCtx.sample.get
      val staticFieldName = "staticFieldName"
      val staticValueName = "staticValueName"

      Task(MDC.put(staticFieldName, staticValueName)) *>
        Task(MDC.get(staticFieldName) shouldBe staticValueName) *>
        Task(MonixMDCAdapter.setContext(metadataCtx)) *>
        Task.eval {
          MDC.get("idString") shouldBe metadataCtx.idString
          MDC.get("idInt") shouldBe metadataCtx.idInt.toString
          MDC.get("timestamp") shouldBe metadataCtx.timestamp.toString
          MDC.get("other") shouldBe metadataCtx.other.toString
          // the value is `null` because `setContext` is overwriting it
          MDC.get(staticFieldName) shouldBe null
          MDC.get("random") shouldBe null
        }
    }.runToFuture

    "updates the context with the given product fields" in {
      val metadataCtx = genMetadaCtx.sample.get
      val staticFieldName = "staticFieldName"
      val staticValueName = "staticValueName"
      val randomId = Gen.identifier.sample.get

      Task(MDC.put(staticFieldName, staticValueName)) *>
        Task(MDC.put("idString", randomId)) *>
        Task{
          MDC.get(staticFieldName) shouldBe staticValueName
          MDC.get("idString") shouldBe randomId
        } *>
        Task(MonixMDCAdapter.updateContext(metadataCtx)) *>
        Task.eval {
          MDC.get("idString") shouldBe metadataCtx.idString
          MDC.get("idInt") shouldBe metadataCtx.idInt.toString
          MDC.get("timestamp") shouldBe metadataCtx.timestamp.toString
          MDC.get("other") shouldBe metadataCtx.other.toString

          //the static field name is persisted since we were updating
          MDC.get(staticFieldName) shouldBe staticValueName
          MDC.get("random") shouldBe null
        }
    }.runToFuture

    "does not set context with null values" in {
      val metadataCtx = MetadataContext(null, 1, null, Other(null))

      Task.evalAsync(MonixMDCAdapter.setContext(metadataCtx)) >>
        Task {
          Option(MDC.get("idString")) shouldBe None
          Option(MDC.get("idInt")) shouldBe Some("1")
          Option(MDC.get("timestamp")) shouldBe None
          MDC.get("other") shouldBe metadataCtx.other.toString
        }
    }.runToFuture

  }

}
