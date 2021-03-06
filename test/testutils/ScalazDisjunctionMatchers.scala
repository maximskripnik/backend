/*
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

package testutils

import org.scalatest.matchers.{BeMatcher, MatchResult, Matcher}

import scalaz.{-\/, \/, \/-}

trait ScalazDisjunctionMatchers {

  def beLeft[E](element: E): Matcher[E \/ _] = BeScalazLeftDisjunctionMatcher[E](element)

  def left[E]: BeMatcher[E \/ _] = new IsScalazLeftDisjunctionMatcher[E]

  def beRight[T](element: T): Matcher[_ \/ T] = BeScalazRightDisjunctionMatcher[T](element)

  def right[T]: BeMatcher[_ \/ T] = new IsScalazRightDisjunctionMatcher[T]

  case class BeScalazRightDisjunctionMatcher[T](element: T) extends Matcher[_ \/ T] {

    def apply(disjunction: _ \/ T): MatchResult = {
      MatchResult(
        disjunction match {
          case \/-(`element`) => true
          case _              => false
        },
        s"$disjunction did not contain a Right disjunction element matching '$element'.",
        s"$disjunction contained a Right disjunction element matching '$element', but should not have."
      )
    }
  }

  case class BeScalazLeftDisjunctionMatcher[E](element: E) extends Matcher[E \/ _] {

    def apply(disjunction: E \/ _): MatchResult = {
      MatchResult(
        disjunction match {
          case -\/(`element`) => true
          case _              => false
        },
        s"$disjunction did not contain a Left disjunction element matching '$element'.",
        s"$disjunction contained a Left disjunction element matching '$element', but should not have."
      )
    }
  }

  class IsScalazLeftDisjunctionMatcher[E] extends BeMatcher[E \/ _] {
    def apply(disjunction: E \/ _) = MatchResult(
      disjunction.isLeft,
      s"$disjunction was not a Left disjunction, but should have been.",
      s"$disjunction was a Left disjunction, but should *NOT* have been."
    )
  }

  class IsScalazRightDisjunctionMatcher[T] extends BeMatcher[_ \/ T] {

    def apply(disjunction: _ \/ T) = MatchResult(
      disjunction.isRight,
      s"$disjunction was not a Right disjunction, but should have been.",
      s"$disjunction was a Right disjunction, but should *NOT* have been."
    )
  }

}
