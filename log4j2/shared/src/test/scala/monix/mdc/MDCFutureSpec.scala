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
import monix.execution.misc.Local
import monix.execution.schedulers.TracingScheduler
import org.apache.logging.log4j.ThreadContext
import org.scalatest.{Assertion, BeforeAndAfter}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.{ExecutionContext, Future}

class MDCFutureSpec extends AsyncWordSpec with Matchers with InitializeMDC with BeforeAndAfter {
  implicit val scheduler: TracingScheduler = TracingScheduler(ExecutionContext.global)

  override def executionContext: ExecutionContext = scheduler

  implicit val opts: Task.Options = Task.defaultOptions.enableLocalContextPropagation

  before {
    ThreadContext.clearMap()
  }

    "Mixing Task with Future" can {
      "Write with Task and get in Future" in {
        val keyValue = KeyValue.keyValueGenerator.sample.get

        val task = for {
          _ <- Task {
                ThreadContext.put(keyValue.key, keyValue.value)
              }
          get <- Task.fromFuture {
                  Future {
                    ThreadContext.get(keyValue.key)
                  }
                }
        } yield get

        task.runToFutureOpt.map { _ shouldBe keyValue.value }
      }

      "Write with Task and get in Future inside Future for comprehension" in {
        val keyValue = KeyValue.keyValueGenerator.sample.get

        val future = for {
          _ <- Task {
                ThreadContext.put(keyValue.key, keyValue.value)
              }.runToFutureOpt
          get <- Future {
                  ThreadContext.get(keyValue.key)
                }
        } yield get

        future.map { _ shouldBe keyValue.value }
      }

      "Write with Future and get in Task" in {
        val keyValue = KeyValue.keyValueGenerator.sample.get

        val task = for {
          _ <- Task.deferFuture {
                Future {
                  ThreadContext.put(keyValue.key, keyValue.value)
                }
              }
          get <- Task {
                  ThreadContext.get(keyValue.key)
                }
        } yield get

        task.runToFutureOpt.map { _ shouldBe keyValue.value }
      }

      def getAndPutTask(key: String, value: String): Future[String] =
        for {
          _ <- Task {
            ThreadContext.put(key, value)
            Local.getContext()
          }.runToFutureOpt
          get <- Future {
            assert(ThreadContext.getImmutableContext.size == 1)
            ThreadContext.get(key)
          }
        } yield get

      "Write with Task and get in Future inside Future for comprehension concurrently" in {
        (0 to 1000).foldLeft[Future[Assertion]](Future.successful(1 shouldBe 1)) { case (lastAssertion, _) =>
          val keyValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

          val f = lastAssertion

          val futures = keyValues.keysAndValues.map { keyValue =>
            getAndPutTask(keyValue.key, keyValue.value)
          }


          val future: Future[Set[String]] =
            f.flatMap(_ => Future.sequence(futures))

           future.map { retrievedKeyValues =>
            retrievedKeyValues shouldBe keyValues.keysAndValues.map(_.value)
          }
        }
      }
    }

    def getAndPut(key: String, value: String): Future[String] =
      for {
        _ <- Future {
              ThreadContext.put(key, value)
            }
        get <- Future {
                ThreadContext.get(key)
              }
      } yield get

    "Using Future only" can {
      "Write and get a value" in {
        val keyValue = KeyValue.keyValueGenerator.sample.get

        val future = getAndPut(keyValue.key, keyValue.value)

        future.map { _ shouldBe keyValue.value }
      }

      "Write and get different values concurrently" in {
        val keyValues = MultipleKeysMultipleValues.multipleKeyValueGenerator.sample.get

        val futures = keyValues.keysAndValues.map { keyValue =>
          Local.isolate(getAndPut(keyValue.key, keyValue.value))
        }

        val future = Future.sequence(futures)

        future.map { retrievedKeyValues =>
          retrievedKeyValues.size shouldBe keyValues.keysAndValues.size
          retrievedKeyValues shouldBe keyValues.keysAndValues.map(_.value)
        }
      }
    }
}
