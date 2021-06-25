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

import monix.execution.misc.Local
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.spi.ThreadContextMap

import collection.JavaConverters._
import java.{util => ju}

class MonixMDCAdapter extends ThreadContextMap {
  private[this] val local = Local[Map[String, String]](Map.empty[String, String])

  override def put(key: String, `val`: String): Unit = local.update(local() + (key -> `val`))

  override def get(key: String): String = local().getOrElse(key, null)

  override def remove(key: String): Unit = local.update(local() - key)

  // Note: we're resetting the Local to default, not clearing the actual hashmap
  override def clear(): Unit = local.clear()

  override def containsKey(key: String): Boolean = local().contains(key)

  override def getCopy: ju.Map[String, String] = local().asJava

  override def getImmutableMapOrNull: ju.Map[String, String] = {
    if (local().isEmpty) {
      null
    } else {
      getCopy
    }
  }

  override def isEmpty: Boolean = local().isEmpty
}

object MonixMDCAdapter {

  /**
   * Initializes the [[MonixMDCAdapter]] by overriding the default ThreadContextMap. Typically
   * you would call this once in your Main (or equivalent).
   *
   * NOTE: This will override the default ThreadContextMap which means that ThreadContext will
   * no longer propagate via [[ThreadLocal]]
   */
  def initialize(): Unit = {
    System.setProperty("log4j2.threadContextMap", "monix.mdc.MonixMDCAdapter")
  }

  private[this] def productToMap[T <: Product](product: T): Map[String, String] = {
    val fields = product.getClass.getDeclaredFields.map(_.getName)
    val values = product.productIterator.toSeq.map {
      case maybe: Option[_] => maybe
      case value => Option(value)
    }
    fields.zip(values).collect { case (k, Some(v)) => (k, v.toString) }.toMap
  }

  /*
   * Set the diagnostic context with the values of the given
   * product with its respective field names as the keys.
   *
   * The current context will be fully overwritten with the new values.
   */
  def setContext[T <: Product](product: T): Unit = {
    ThreadContext.clearAll()
    ThreadContext.putAll(productToMap(product).asJava)
  }

  /**
   * Puts the values of the given product with its respective
   * field names as the keys into the diagnostic context.
   *
   * The current context will remain the same, but only the
   * fields that already existed will be overwritten.
   */
  def updateContext[T <: Product](product: T): Unit = {
    productToMap(product).foreach(kv => ThreadContext.put(kv._1, kv._2))
  }

}
