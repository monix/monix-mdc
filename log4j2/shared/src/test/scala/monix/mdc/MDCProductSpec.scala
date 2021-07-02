/*
 * Copyright (c) 2021-2021 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.mdc

import monix.eval.Task
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import monix.execution.Scheduler.Implicits.global
import org.apache.logging.log4j.ThreadContext

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

      Task(ThreadContext.put(staticFieldName, staticValueName)) *>
        Task(ThreadContext.get(staticFieldName) shouldBe staticValueName) *>
        Task(MonixMDCAdapter.setContext(metadataCtx)) *>
        Task.eval {
          ThreadContext.get("idString") shouldBe metadataCtx.idString
          ThreadContext.get("idInt") shouldBe metadataCtx.idInt.toString
          ThreadContext.get("timestamp") shouldBe metadataCtx.timestamp.toString
          ThreadContext.get("other") shouldBe metadataCtx.other.toString
          // the value is `null` because `setContext` is overwriting it
          ThreadContext.get(staticFieldName) shouldBe null
          ThreadContext.get("random") shouldBe null
        }
    }.runToFuture

    "updates the context with the given product fields" in {
      val metadataCtx = genMetadaCtx.sample.get
      val staticFieldName = "staticFieldName"
      val staticValueName = "staticValueName"
      val randomId = Gen.identifier.sample.get

      Task(ThreadContext.put(staticFieldName, staticValueName)) *>
        Task(ThreadContext.put("idString", randomId)) *>
        Task{
          ThreadContext.get(staticFieldName) shouldBe staticValueName
          ThreadContext.get("idString") shouldBe randomId
        } *>
        Task(MonixMDCAdapter.updateContext(metadataCtx)) *>
        Task.eval {
          ThreadContext.get("idString") shouldBe metadataCtx.idString
          ThreadContext.get("idInt") shouldBe metadataCtx.idInt.toString
          ThreadContext.get("timestamp") shouldBe metadataCtx.timestamp.toString
          ThreadContext.get("other") shouldBe metadataCtx.other.toString

          //the static field name is persisted since we were updating
          ThreadContext.get(staticFieldName) shouldBe staticValueName
          ThreadContext.get("random") shouldBe null
        }
    }.runToFuture

    "does not set context with null values" in {
      val metadataCtx = MetadataContext(null, 1, null, Other(null))

      Task.evalAsync(MonixMDCAdapter.setContext(metadataCtx)) >>
        Task {
          Option(ThreadContext.get("idString")) shouldBe None
          Option(ThreadContext.get("idInt")) shouldBe Some("1")
          Option(ThreadContext.get("timestamp")) shouldBe None
          ThreadContext.get("other") shouldBe metadataCtx.other.toString
        }
    }.runToFuture

  }

}
