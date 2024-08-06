package blog.invariant

import soundness.*
import honeycomb.*
import punctuation.*
import scintillate.*
import aviation.{Time as _, *}
import amok.*

import logFormats.ansiStandard
import classloaders.scala
import charEncoders.utf8
import charDecoders.utf8
import orphanDisposal.cancel
import threadModels.platform
import pathHierarchies.simple
import stdioSources.virtualMachine.ansi
import textSanitizers.skip

given Realm = realm"invariant"
given HtmlConverter = HtmlConverter(AmokRenderer)
given Message is Loggable = safely(supervise(Log.route(Out))).or(Log.silent)
erased given ConcurrencyError is Unchecked = ###

// zmnqm

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
      Link(rel = Rel.Stylesheet, href = % / p"amok.css"),
      Link(rel = Rel.Icon, href = % / p"images" / p"icon.webp")),
    Body
     (Nav(Ul(menu.map { (label, link) => Li(A(href = link)(label)) }.to(List))),
      Header(Img(src = % / p"images" / p"panorama2.webp")),
      Main(Aside(side), Article(content*)),
      Footer
       (Img(src = % / p"images" / p"panorama.webp"),
        P(t"© Copyright 2024 Jon Pretty & Propensive")))))

@main
def server(): Unit = supervise(tcp"8080".serve[Http](handle))

class Service() extends JavaServlet(handle)

def notFound(path: Text): HtmlDoc = page
 (Nil,
  H1(t"Not Found"),
  P(t"There is no page at the url ", Code(path), t"."))

def about: HtmlDoc raises ClasspathError raises MarkdownError =
  val markdown = Markdown.parse((Classpath / p"about.md")())
  page(Nil, Div(markdown.html))

case class Blogpost(path: Text, date: Date, title: Text, description: Text)
val posts = List(t"error-handling", t"error-handling-2", t"error-handling-3", t"error-handling-4")

def home: HtmlDoc raises ClasspathError raises MarkdownError raises PathError = page
 (Nil,
  Div(Markdown.parse((Classpath / p"home.md")()).html),
  Section.posts(posts.map(Cache.blogpost(_)).sortBy(_.date).flatMap:
    case Blogpost(path, date, title, description) =>
      List(H3(A(href = % / Name(path))(title)), Time(date.show), P(description))))

def contact: HtmlDoc =
  page
   (Nil,
    H1(t"Contact Me"),
    P(t"To get in touch, please email me at jon.pretty@propensive.com"))

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
      case % / p"amok.css"             => HttpResponse(Classpath / p"amok" / p"styles.css")
      case % / Name(post)              => HttpResponse(Cache(post))
      case _                           => HttpResponse(notFound(request.pathText))
