package blog.invariant

import scala.collection.mutable.HashMap

import soundness.*
import honeycomb.*
import punctuation.*
import scintillate.*

import logFormats.ansiStandard
import classloaders.scala
import charEncoders.utf8
import charDecoders.utf8
import textSanitizers.skip
import orphanDisposal.cancel
import threadModels.platform
import serpentine.hierarchies.simple
import stdioSources.virtualMachine.ansi
import htmlRenderers.scalaSyntax

given Realm = realm"invariant"
given Message is Loggable = safely(supervise(Log.route(Out))).or(Log.silent)

def menu = Map
 (t"Home"    -> % / p"",
  t"About"   -> % / p"about",
  t"Contact" -> % / p"contact")

def page(side: Seq[Html[Flow]], content: Html[Flow]*): HtmlDoc =
  HtmlDoc(Html
   (Head
     (Title(t"Invariant.blog"),
      Meta(charset = enc"UTF-8"),
      Link(rel = Rel.Stylesheet, href = % / p"styles.css"),
      Link(rel = Rel.Icon, href = % / p"images" / p"logo.svg")),
    Body
     (Nav(Ul(menu.map { (label, link) => Li(A(href = link)(label)) }.to(List))),
      Header(Img(src = % / p"images" / p"panorama2.webp")),
      Main(Aside(side), Article(content*)),
      Footer
       (Img(src = % / p"images" / p"panorama.webp"),
        P(t"© Copyright 2024 Jon Pretty & Propensive")))))

@main
def server(): Unit =
  erased given ConcurrencyError is Unchecked = ###
  supervise(tcp"8080".serve[Http](handle))

class Service() extends JavaServlet(handle)

def notFound(path: Text): HtmlDoc = page
 (Nil,
  H1(t"Not Found"),
  P(t"There is no page at the url ", Code(path), t"."))
def about: HtmlDoc = page(Nil, H1(t"About"), P(t"Invariant.blog is a blog about invariance."))

def home: HtmlDoc =
  val markdown = md"""
Welcome to my blog. Here I write about programming, primarily with
[Scala 3](https://scala-lang.org/), but more generally in advanced statically-typed programming
languages. This is my outlet to elaborate on the technical decisions, influences and motivations that
contributed towards my main software projects: [Soundness](https://soundness.dev/),
[Fury](https://github.com/propensive/fury), [Amok](https://github.com/propensive/amok) and
[CoDL](https://github.com/propensive/codl).

This blog is new, so there is only one post so far; the first in a series about error handling.
  """

  page
   (Nil,
    H2(t"A blog about programming with ", A(href = url"https://soundness.dev/")(t"Soundness")),
    Div
     (markdown.html*))

def contact: HtmlDoc =
  page(Nil, H1(t"Contact Me"), P(t"To get in touch, please email me at jon.pretty@propensive.com"))

object Cache:
  private val cache: HashMap[Text, HtmlDoc] = HashMap()

  def apply(post: Text): HtmlDoc raises PathError raises ClasspathError raises MarkdownError =
    cache.establish(post):
      val markdown = Markdown.parse((Classpath / p"posts" / Name(t"$post.md"))())

      page
       (List(Div(htmlRenderers.outline.convert(markdown.nodes) :+ Span.fleuron(t"☙"))),
        (Address(t"Jon Pretty,", Time(t"11 July 2024")) :: (markdown.html.to(List) :+ P.fleuron(t"❦")))*)

def handle(using HttpRequest): HttpResponse[?] =
  quash:
    case MarkdownError(detail) =>
      HttpResponse(page(Nil, H1(t"Bad markdown: $detail")))

    case ClasspathError(path) =>
      HttpResponse(page(Nil, H1(t"Path $path not found")))

    case PathError(path, reason) =>
      HttpResponse(page(Nil, P(t"$path is not valid because $reason")))

  .within:
    request.path match
      case %                           => HttpResponse(home)
      case % / p"about"                => HttpResponse(about)
      case % / p"contact"              => HttpResponse(contact)
      case % / p"images" / Name(image) => HttpResponse(Classpath / p"images" / Name(image))
      case % / p"styles.css"           => HttpResponse(Classpath / p"styles.css")
      case % / Name(post)              => HttpResponse(Cache(post))
      case _                           => HttpResponse(notFound(request.pathText))
