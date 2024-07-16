package blog.invariant

import soundness.*
import honeycomb.*
import punctuation.*
import scintillate.*

import logFormats.ansiStandard
import classloaders.scala
import charEncoders.utf8
import charDecoders.utf8
import orphanDisposal.cancel
import threadModels.platform
import pathHierarchies.simple
import stdioSources.virtualMachine.ansi
import htmlRenderers.scalaSyntax
import textSanitizers.skip

given Realm = realm"invariant"
given Message is Loggable = safely(supervise(Log.route(Out))).or(Log.silent)
erased given ConcurrencyError is Unchecked = ###

val menu: Map[Text, SimplePath] = Map
 (t"Home"    -> % / p"")/*,
  t"About"   -> % / p"about",
  t"Contact" -> % / p"contact")*/


def page(side: Seq[Html[Flow]], content: Html[Article.Content]*): HtmlDoc =
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

class Service() extends JavaServlet(handle)

@main
def server(): Unit = supervise(tcp"8080".serve[Http](handle))

def notFound(path: Text): HtmlDoc = page
 (Nil,
  H1(t"Not Found"),
  P(t"There is no page at the url ", Code(path), t"."))

def about: HtmlDoc raises ClasspathError raises MarkdownError =
  val markdown = Markdown.parse((Classpath / p"about.md")())
  page(Nil, Div(markdown.html))


def home: HtmlDoc raises ClasspathError raises MarkdownError = page
 (Nil,
  Div(Markdown.parse((Classpath / p"home.md")()).html),
  Section.post
   (Time(t"16 July 2024"),
    H3(A(href = % / p"error-handling")(t"Effective Error Handling")),
    P(t"""The first in a new series of blogposts introducing and exploring Soundness's approach to
          error handling. But first, I establish a basic understanding of errors, and present a
          manifesto of desirable qualities for systematic error handling.""")))


def contact: HtmlDoc =
  page(Nil, H1(t"Contact Me"), P(t"To get in touch, please email me at jon.pretty@propensive.com"))

def handle(using HttpRequest): HttpResponse[?] =
  mend:
    case MarkdownError(detail)   => HttpResponse(page(Nil, H1(t"Bad markdown: $detail")))
    case ClasspathError(path)    => HttpResponse(page(Nil, H1(t"Path $path not found")))
    case PathError(path, reason) => HttpResponse(page(Nil, P(t"$path is not valid: $reason")))
  .within:
    request.path match
      case %                           => HttpResponse(home)
      case % / p"about"                => HttpResponse(about)
      case % / p"contact"              => HttpResponse(contact)
      case % / p"images" / Name(image) => HttpResponse(Classpath / p"images" / Name(image))
      case % / p"styles.css"           => HttpResponse(Classpath / p"styles.css")
      case % / Name(post)              => HttpResponse(Cache(post))
      case _                           => HttpResponse(notFound(request.pathText))
