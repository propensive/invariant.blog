package blog.invariant

import scala.collection.mutable.HashMap

import soundness.*
import honeycomb.*
import punctuation.*
import cellulose.*
import aviation.{Time as _, *}
import contingency.AggregateError

import classloaders.scala
import charDecoders.utf8
import textSanitizers.skip
import calendars.gregorian


object Cache:
  private val blogposts: HashMap[Text, Blogpost] = HashMap()
  private val cache: HashMap[Text, HtmlDoc] = HashMap()

  def blogpost(post: Text): Blogpost =
    erased given [ErrorType <: Exception] => AggregateError[ErrorType] is Unchecked = ###
    blogposts.establish(post):
      mend:
        case _: PathError                 => ???
        case _: ClasspathError            => ???
        case _: DateError                 => ???
        case _: CodlReadError             => ???
      .within:
        val resource: Resource = (Classpath / p"posts" / Name(t"$post.md"))()
        Codl.read[Blogpost](resource.read[Text])

  def apply(post: Text): HtmlDoc raises PathError raises ClasspathError raises MarkdownError =
    cache.establish(post):
      val resource: Resource = (Classpath / p"posts" / Name(t"$post.md"))()
      val text: Text = resource.read[Text]
      val lines: LazyList[Text] = text.cut(t"\n").to(LazyList)
      val content = lines.dropWhile(_ != t"##").tail.join(t"\n")
      val markdown = Markdown.parse(content)

      page
       (List(Div(htmlRenderers.outline.convert(markdown.nodes) :+ Span.fleuron(t"☙"))),
        Address(t"Jon Pretty,", Time(blogpost(post).date.show)),
        Div(markdown.html),
        P.fleuron(t"❦"))
