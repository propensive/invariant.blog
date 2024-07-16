package blog.invariant

import scala.collection.mutable.HashMap

import soundness.*
import honeycomb.*
import punctuation.*

import classloaders.scala
import charDecoders.utf8
import textSanitizers.skip
import htmlRenderers.scalaSyntax


object Cache:
  private val cache: HashMap[Text, HtmlDoc] = HashMap()

  def apply(post: Text): HtmlDoc raises PathError raises ClasspathError raises MarkdownError =
    cache.establish(post):
      val markdown = Markdown.parse((Classpath / p"posts" / Name(t"$post.md"))())

      page
       (List(Div(htmlRenderers.outline.convert(markdown.nodes) :+ Span.fleuron(t"☙"))),
        Address(t"Jon Pretty,", Time(t"15 July 2024")),
        Div(markdown.html),
        P.fleuron(t"❦"))
