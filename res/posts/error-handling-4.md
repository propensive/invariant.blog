title  The Mechanics of Mitigation
path   error-handling-4
date   2024-08-08

description
    This is where we start writing some real-world code.
##

# The Mechanics of Mitigation

**WE SAW IN THE LAST POST** how Soundness provides syntax for
[capturing and recovering from errors](/error-handling-3/). We can use the
`mend`/`within` construct to _mend_ an error that occurs _within_ an
expression, providing an alternative result, so execution can proceed.

This much is similar to a `try`/`catch` expression. But a `mend`/`within`
expression is _checked_. The set of cases in the `mend` block determines the
types of error that will be considered _safe_ in the `within` block. That
means any expression raising a matching error type can be evaluated freely,
and any expression raising a different type of error cannot—unless that error
type is handled elsewhere.

We will continue with the example from last time: a small application that
reads some JSON data of people's heights and weights from a file, and
calculates their average BMI.

Here is the full code, with the adaptations we established in the last post:

```amok
syntax scala
##
import soundness.*

type Bmi = Quantity[Kilograms[1] & Metres[-2]]

case class Person
    (name:   Text,
     age:    Int,
     height: Quantity[Metres[1]],
     weight: Quantity[Kilograms[1]]):

  def bmi: Bmi = weight/(height*height)

@main
def run(path: Text): Unit = Out.println:
  mend:
    case ParseError(line, _, _) => t"There was a parsing error at $line."
    case PathError(_, reason)   => t"There was a path error because $reason."
    case IoError(_)             => t"There was an I/O error."

  .within:
    val json = Json.parse(path.decode[Path].as[File])
    val data = unsafely(json.as[List[Person])
    val mean = data.map(_.bmi)/data.length

    t"The average BMI is $mean"
```


## `raise` Declarations

It was stated—without any proof!—that the expression
`Json.parse(path.decode[Path].as[File])` could be responsible for three
types of error:
- `Json.parse` raises `ParseError`
- `decode` raises `PathError`, and
- `Path#as` raises `IoError`

How should we, as programmers, know this? And how does the compiler know that
these error types need to be addressed?

The answer lies in the method signatures. And this should hardly be surprising
to any Java programmer, since that's exactly where checked exceptions are
declared in `throws` clauses.

Here is the definition of `Json.parse` from the
[Jacinta](https://github.com/propensive/jacinta/) module of Soundness:
```amok
syntax scala
##
object Json:
  def parse[SourceType: Readable by Bytes]
      (value: SourceType)
          : Json raises ParseError =
    Json(JsonAst.parse(value))
```

This is an interesting signature for several reasons, but the part which
interests us here is the return type, `Json raises ParseError`. And it
needs to be made clear that this entire three-word phrase is a _type_. It is
not a return type of `Json` with an additional declaration whose nature we are
yet to learn; the entire thing is a type, and can appear in any type position
in Scala code.

`Json raises ParseError` and another type which appears above,
`Readable by Bytes`, are fully-applied _infix types_. They are identical to
the types `raises[Json, ParseError]` and `by[Readable, Bytes]`, formed from
type constructors `raises` and `by`. But we are permitted by the compiler
to write them in infix style.

### Principal types and supplements

This provides welcome fluidity to our code, and means we can write a return
type like `Json raises ParseError` and have it first express the most important
detail of the return type, that the result will be an instance of `Json`, but
furthermore, that `ParseError` is raised.

I'm not aware of any better terminology for the concept, so I shall call `Json`
the _principal type_ of `Json raises ParseError` and `raises ParseError` is a
_supplement_ to the type. The supplement is not a type itself, but it can be
interpreted as a type constructor, since supplementing `Json` with
`raises ParseError` transforms the proper type `Json` into a new proper type.

I will use this terminology exclusively for infix type
constructors, and the purpose of the terms _principal_ and _supplement_ is to
describe the syntactic role played by each part of the full type. These are not
new types of type in Scala's type sysetm. They are just a means of
labelling existing concepts for better understanding.

It is nevertheless the supplement that tells the compiler that calling
`Json.parse` raises `ParseError`s, which must be handled. And equally, it is
this supplement which tells the programmer, _you or I_, that we must write the
code to ensure that `ParseError` is handled, one way or another.

## Implementation

Let's explore the underlying mechanism that makes this possible.

### `Tactic`s and `raise`ing Errors

Let's revisit the definition of `Json.parse`. We can write that definition
in a different way, in terms of a contextual `Tactic` instance for handling
`ParseError`s.

Select ❶ or ❷ below to see these alternative, but equivalent, ways of
defining `parse`:
```amok
syntax scala
transform
  before   with infix raises type
  after    with Tactic using-parameter
  replace  Json raises ParseError  Json
  replace  SourceType)  SourceType)(using Tactic[ParseError])
##
object Json:
  def parse[SourceType: Readable by Bytes]
      (value: SourceType)
          : Json raises ParseError =
    Json(JsonAst.parse(value))
```

A `Tactic` can be thought of as a localized strategy for handling a particular
type of error, passed into the method from the call-site. This delegates the
choice of how each type of error will be handled to the outside world, and
it means we must work to a simple abstract interface within the method body.

(In the example of `Json.parse`, the implementation is not so so interesting:
it just delegates to a lower-level method, so we do not see this
interface—yet.)

Either signature for `parse` works equally well. Thanks to the definition of
`raises`, they are equivalent. But it will take several steps
to show it. Here's the definition of `raises`:
```amok
syntax scala
##
infix type raises[SuccessType, ErrorType <: Exception] =
  Tactic[ErrorType] ?=> SuccessType
```

This is a type alias, so any appearance of `A raises B` can
be substituted for `Tactic[B] ?=> A` without changing the semantics.

### Context Functions

The `?=>` indicates a _context function_ type. Context Functions are a very
powerful feature introduced in Scala 3. And while they are more apparent on
the definition-side of error handling, they are absolutely foundational
throughout.

Any Scala programmer should be familiar with functions. They are objects
which can be invoked, taking values as input, and producing a value as output.
Unlike methods which are members of objects, functions are representations of
methods which are themselves values. And Scala frequently and seamlessly
converts methods into function values.

The transformation between a method definition and its function-value
equivalent can be seen in this example:
```amok
syntax  scala
transform
  before   method definition
  after    function value
  replace  (year: Year): Boolean =  : Year => Boolean = year =>
  replace  def leapYear  val leapYear
##
case class Year(value: Int)

def leapYear(year: Year): Boolean =
  val n = year.value
  n%400 == 0 || n%4 == 0 && n%100 != 0
```

The implementation of `leapYear` is indeed different, but
any invocation of `leapYear(y)` for a year, `y`, will behave the same. In
almost every case we prefer a method definition (with `def`) for clarity.

But a method's parameters may also be contextual, as indicated by the
`using` keyword. In Scala 2, these
were called `implicit` parameters. There is an equivalent functional value
representation of a method taking a contextual parameter, using the `?=>` operator:

```amok
syntax  scala
transform
  before   Method definition
  after    function value
  replace  (using Year): Boolean  : Year ?=> Boolean
  replace  def leapYear  val leapYear
##
case class Year(value: Int)

def leapYear(using Year): Boolean =
  val n = summon[Year].value
  n%400 == 0 || n%4 == 0 && n%100 != 0
```

Given either variation of the definition, `leapYear` is an expression which
will evaluate to either `true` or `false`, provided there is a given instance
of `Year` in scope. For example,
```amok
syntax scala
##
given Year(1984)
Out.println(t"It ${if leapYear then t"is" else t"isn't a"} leap year.")
```

These two transformations from methods to functional values are similar, but
there was one difference which might not have been obvious at first glance:
When we converted to the context-functional form of `leapYear`, we did not
change the right-hand side implementation at all, whereas when we converted
the first variant, we had to introduce `year =>` to the right-hand side
because its type had changed into a lambda (and we would have had no
identifier for the lambda variable, otherwise).

Both methods are virtually the same in Java bytecode. Both functional values
are virtually the same in Java bytecode. And the transformations between them are
mirrors of each other. And the right-hand side of both is concretely a lambda.

But we are allowed to elide the explicit lambda variable (`year =>`) for the
context function because it can be inferred from the return type.

And this is true in general: any expression or block of code whose expected
type is `A ?=> B` will be implemented as a lambda, but requires no explicit
lambda variable to be specified. However, more importantly, that expression
or block can be written as if an instance `A` is given in its context; it is
silently injected into the scope.

It is as if we had written, an additional
`given` definition at the start of the block, like this:
```amok
syntax scala
##
val leapYear: Year ?=> Boolean = y =>
  given Year = y
  val n = summon[Year].value
  n%400 == 0 || n%4 == 0 && n%100 != 0
```

It is safe precisely because the type of the expression, as a context
function, ensures it: an instance of `A ?=> B` may only be used in a scope
where a contextual instance of `A` is present—just as we can only compute
the result of a function `A => B` by passing it an instance of `A`. The
difference with context functions is that they may be composed like
functions, but without explicit reference to their parameters at the
term-level.

With this knowledge, we can show the equivalence of the two implementations
of `Json.parse` from earlier:
- The return type `Json raises ParseError` is syntax sugar for
  `raises[Json, ParseError]`
- `raises[Json, ParseError]` dealiases to `Tactic[ParseError] ?=> Json`
- `Tactic[ParseError] ?=> Json` is equivalent to a return type of `Json` and
  an additional `using` parameter of type `Tactic[ParseError]`

### Effective Error-handling `Tactic`s

It is contextual `Tactic` instances, like `Tactic[ParseError]` and
`Tactic[IoError]` which confer both the _need_ and the _capability_ of raising
a certain error type.

Supplementing a method's return type with `raises IoError`
confers a given contextual `Tactic[IoError]` into the body of the method,
while at the same time requiring that the method may only be invoked in a
context where there is a given `Tactic[IoError]`.

It is context functions which make it possible to define `mend`/`within`.
Although macros are required to implement `mend`, the parameter to `within`,
where the happy evaluation path is expressed, is a context function which
provides certain `Tactic`s.

To make this clearer, let's show how this mechanism works with a specific
`mend`/`within` example. We can avoid thinking about the full complexity of
the macro by considering just one particular expansion of it.

```amok
syntax  scala
##
mend:
  case ParseError(line, _, _) => Json(t"Parse error")
  case PathError(_, reason)   => Json(t"Path error")
  case IoError(_)             => Json(t"I/O error")
.within(Json.parse(path.decode[Path].as[File]))
```

Different `mend` blocks will infer different `within` definitions, but for this
particular example, the signature of `within` will be the following:
```amok
syntax scala
##
def within
    (body: (Tactic[ParseError], Tactic[PathError], Tactic[IoError]) ?=> Json)
        : Json
```

Thus, `within` is a method taking a single parameter, a context function taking
three input values, `Tactic[ParseError]`, `Tactic[PathError]` and
`Tactic[IoError]` return type `Json`. And `Json` is also the return type of
`within`, representing the successful computation of its `body` parameter.

At the call-site, we can invoke `within` as if instances of each of these
three `Tactic`s are present. So we are permitted to call `Json.parse` because,
and only because, the type of the `body` parameter infers its presence.

More generally, every error type irrefutably matched by the cases in the `mend`
block will produce a corresponding `Tactic` parameter to the `body` context
function. And every `Tactic` parameter will provide a `given` instance to the
`within` expression.

The implementation of `within` is not shown, but it's worth considering
parametrically. Its implementation has an instance of `body`, an instance of a
context function passed in from the call-site. Invoking the context function is
its only means of constructing the `Json` instance, which is required for its
return value.

The `body` context function can be invoked with three `Tactic` instances, but
these must be constructed. Their implementations correspond to the three cases
in the `mend` block, and this work is handled by the macro.

One key component of the implementation is the `boundary`/`break` syntax
which was [introduced in Scala 3.3.0](https://www.scala-lang.org/api/3.3.0/scala/util/boundary$.html). The
macro invokes `body` inside a delimited `boundary`, and each of the `Tactic`
instances is defined to `break` to that boundary, invoking the right-hand side
of its corresponding `case` clause to get an alternative `Json` value.

Thankfully, in usage we rarely need to consider any of this. From a usage
perspective, we simply declare the errors we wish to handle to `mend`, and we
are liberated to invoke any expressions which raise those errors inside
`within`.

But it is worthwhile remembering that `Tactic`s are what expresses the
need and the capability of raising each error type.

## Raising Multiple Errors

In the interests of reusability, we might like to split the `run` method
of our example into several methods.

Let's rewind to our initial prototype version, without any
error-handling code, to see the transformation we would like to make:
```amok
syntax  scala
transform
  replace  @main
      def readJson(path: Text): Json = Json.parse(path.decode[path].as[File])

      @main

  replace  Json.parse..File])  readJson(path)
##
import strategies.uncheckedErrors

@main
def run(path: Text): Unit =
  val json = Json.parse(path.decode[Path].as[File])
  val data = json.as[List[Person]]
  val mean = data.map(_.bmi)/data.length
  Out.println(t"The average BMI is $mean")
```

Hopefully the extraction of the `readJson` method looks like a natural
refactoring.

But how much harder is this with error-handling in place? Rather than
having a simple answer, this introduces a new question: which method
should handle the errors? This is a question of good design.

We can leave the error-handling code in the `run` method, but in order
to compile the body of `readJson`, its signature needs to declare the
three error types in raises, like so:
```amok
syntax  scala
##
def readJson(path: Text)
        : Json raises ParseError raises PathError raises IoError =
  Json.parse(path.decode[Path].as[File])

@main
def run(path: Text): Unit = Out.println:
  mend:
    case ParseError(line, _, _) => t"There was a parsing error at $line."
    case PathError(_, reason)   => t"There was a path error because $reason."
    case IoError(_)             => t"There was an I/O error."

  .within:
    val json = readJson(path)
    val data = unsafely(json.as[List[Person])
    val mean = data.map(_.bmi)/data.length

    t"The average BMI is $mean"
```

The return type, `Json raises ParseError raises PathError raises IoError`
communicates the three error types raised by the method, but it is hardly
_succinct_. A hypothetical future version of Scala might introduce syntax
that would permit us to write,
`Json raises {ParseError, PathError, IoError}`, but at the time of writing,
that exists only as fantasy.

We might be content to accept this verbosity as one of our lesser problems.
Though unfortunately it only gets worse. We value the ability to compose
several simple expressions in a larger expression, and to hide its complexity
behind a method definition, so it might appear as a simple expression that
can be composed into a larger expression... and so on. The simplicity of
this composition is egregiously undermined if the type signature of
each method must grow with each composition, to include supplements for
every error type that might be raised for the expanded tree of computations.
This simply does not scale.

An alternative (but not the only one) is to transform errors of one type into
errors of another type. If we transform several different error types into the
same error type (or even fewer error types), then we can simplify the
type-explosion problem for each method definition, and ensure that the
supplement to each type is of a manageable size.

For our example, `ParseError`, `PathError` and `IoError` are all problems with
_reading_. We could introduce a new error type, `ReadError` to represent all of
these possible problems.

Let's define the simplest `ReadError` we can, as a subtype of Soundness's
`Error` type:
```amok
syntax scala
highlight  m".."  This is a Message literal, but it is essentially just text
##
case class ReadError()
extends Error(m"There was a read error.")
```

We can then use a new construct, `tend` to define the transformations. In code,
`tend` looks very similar to `mend`. It even rhymes! But whereas the
right-hand side of each `mend` case was an alternative _result value_, the
right-hand side of each `tend` case is an _error value_ to be raised.

Here is the full example. Hover over the highlighted parts of the code for more detail:

```amok
syntax  scala
highlight  Json raises ReadError  The return type is simpler
highlight  case ReadError()  Now we only have one error type to consider
highlight  tend:
    The tend block handles the three error types,
    but it now requires a Tactic[ReadError]
##
def readJson(path: Text): Json raises ReadError =
  tend:
    case ParseError(_, _, _) => ReadError()
    case PathError(_, _)     => ReadError()
    case IoError(_)          => ReadError()
  .within(Json.parse(path.decode[Path].as[File]))

@main
def run(path: Text): Unit = Out.println:
  mend:
    case ReadError() => t"There was a problem reading the file"
  .within:
    val json = readJson(path)
    val data = unsafely(json.as[List[Person])
    val mean = data.map(_.bmi)/data.length

    t"The average BMI is $mean"
```

We are now using `mend`, `tend` and `unsafely`, and our error handling code is
spread across both methods.

But it remains clean and clear, and we can reason about it in terms of
`Tactic`s:
- inside the first `within` block there are contextual `Tactic[ParseError]`,
  `Tactic[PathError]` and `Tactic[IoError]` instances
- the `tend`/`within` construct requires a contextual `Tactic[ReadError]`
  because other error types are transformed into `ReadError`s, which must be
  handled
- `readJson`'s return type is `Json raises ReadError`, which means a
  `Tactic[ReadError]` is available in its body
- the invocation of `readJson(path)` therefore requires a `Tactic[ReadError]`
- the `mend` block handles `ReadError`, and therefore provides a
  `Tactic[ReadError]` to its `within` block
- `unsafely` provides an arbitrary `Tactic` for any `Error` type

But not only can we reason about working code; we can get useful feedback on
code which does not compile. If we omitted one case from `tend` block, it
would cause a compile error, which would specify precisely the type of error
we have failed to handle. Likewise, if we forgot the supplement
`raises ReadError` in the definition of `readJson`, it's a compile error—but
we are told precisely what error type we haven't handled.

The value of this constraint of correctness should not be underestimated.
Next time, I'll elaborate some more on the confidence it gives us,
as developers, and I'll start to explore how we should _design_ errors.
