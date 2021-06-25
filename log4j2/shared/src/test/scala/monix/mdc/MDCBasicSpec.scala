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

import monix.eval.{Task, TaskLocal}
import monix.execution.Scheduler
import org.apache.logging.log4j.ThreadContext
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.ExecutionContext

class MDCBasicSpec extends AsyncWordSpec with Matchers with InitializeMDC with BeforeAndAfter {
  implicit val scheduler: Scheduler               = Scheduler.global
  override def executionContext: ExecutionContext = scheduler
  implicit val opts: Task.Options                 = Task.defaultOptions.enableLocalContextPropagation

  before {
    ThreadContext.clearMap()
  }

  def putAndGet(key: String, value: String): Task[String] =
    for {
      _ <- Task {
        ThreadContext.put(key, value)
      }
      get <- Task {
        ThreadContext.get(key)
      }
    } yield get

  "Task with ThreadContext" can {
    "Write and get a value" in {
      val keyValue = KeyValue.keyValueGenerator.sample.get

      val task = putAndGet(keyValue.key, keyValue.value)
      task.runToFutureOpt.map { _ shouldBe keyValue.value }
    }

    "Write and get different values concurrently" in {
      val keyValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

      val tasks = keyValues.keysAndValues.map { keyValue =>
        TaskLocal.isolate(putAndGet(keyValue.key, keyValue.value).executeAsync)
      }

      val task = Task.parSequence(tasks)

      task.runToFutureOpt.map { retrievedKeyValues =>
        retrievedKeyValues.size shouldBe keyValues.keysAndValues.size
        retrievedKeyValues shouldBe keyValues.keysAndValues.map(_.value)
      }
    }

    "Isolate nested modifications" in {

      val test = for {
        // to initialize the map
        _ <- Task(ThreadContext.put("key", "0"))
        _ <- TaskLocal.isolate(Task(ThreadContext.put("key", "1")))
      } yield ThreadContext.get("key") shouldBe "0"

      test.runToFutureOpt
    }

  }
}
