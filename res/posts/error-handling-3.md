title  Migrating to Safety
path   error-handling-3
date   2024-08-02

description
    An initial exploration of Contingency, and its approach to handling errors
    in Scala, and how it enables us to to write code that is both elegant and
    safe.

##
## Getting Started

**LAST TIME**, I presented a [draft manifesto](/error-handling-2/) for a
programming framework that facilitates code which evolves from unsafe to
safe; from prototype to production. But now, it is about time we started
writing some code, to add some concrete flesh to the manifesto's abstract
bones.

Let's start with a typical
prototype of an application written with [Soundness](https://soundness.dev/),
which reads some JSON data from a file and processes it. In particular, we will
be exploring [Contingency's](https://soundness.dev/contingency/) facilities for
error handling.

### The Prototype

Our prototype will include some definitions. We will be working with a dataset
about `Person`s, and we will use types from
[Quantitative](https://soundness.dev/quantitative/) to represent them:
```amok
syntax scala
highlight  Quantity..-2]]  This is a type representing a physical quantity
##
type Bmi = Quantity[Kilograms[1] & Metres[-2]]

case class Person
    (name:   Text,
     age:    Int,
     height: Quantity[Metres[1]],
     weight: Quantity[Kilograms[1]]):

  def bmi: Bmi = weight/(height*height)
```
The computation will happen in the `main` method.
Hover over the highlighted parts of the code to understand it better:
```amok
syntax scala
highlight  strategies.uncheckedErrors
    This import is necessary to avoid compilation errors
error      Json.parse      This raises a ParseError if the content isn't valid JSON
error      decode[Path]    This raises a PathError if the filename isn't valid
error      as[File]
    This raises an IoError if the file
    does not exist at the path
error      json.as[List[Person]]  Further errors are raised here
highlight  Out..mean    This expression does not raise any errors
##
import soundness.*
import strategies.uncheckedErrors

@main
def run(path: Text): Unit =
  val json = Json.parse(path.decode[Path].as[File])
  val data = json.as[List[Person]]
  val mean = data.map(_.bmi)/data.length
  Out.println(t"The average BMI is $mean")
```

Even without knowing the details of the APIs of the various Soundness
modules, this code should be easy to read.

Its `run` method, which is also the entrypoint to the program, as indicated by
the `@main` annotation:
 - reads a path string as input
 - accesses the file at this path
 - parses its content as JSON
 - reads the JSON as a list of `Person` instances
 - calculates the average BMI of all the `Person`s
 - prints out the result

There are several things that could go wrong here—errors—when running this
code:
 - the path may be in the wrong format
 - there may be no file at the path
 - the contents of the file may not be valid JSON
 - the JSON in the file might not represent a list of `Person`s
 - the list might be empty, making it impossible to calculate an average

Whereas, printing the final result has nothing particular about it that might
prevent it from working. Every line is still susceptible to global risks like
running out of memory, but with respect to the the types of error we have any hope of
addressing, this last line is _safe_.

Despite these potential errors, the code compiles and runs thanks only to the
import of `strategies.uncheckedErrors`. This makes every error an
_unchecked error_. It disables the compiler's checks that would otherwise stop
this code compiling.

If an error does occur, then it is
_thrown_ as an exception, and will propagate up the call stack until it is
caught, or in this example, reaches the application's `main` method. As this
is the
entrypoint to the application, it forces the JVM to exit, printing a stack
trace on the way out.

This is inherently unsafe, but it is exactly what we need for a prototype,
or a proof of concept. If we provide it with good input, then it will
_prove the concept_; if we provide bad input, it will fail meekly, but the
_concept_ will be neither proven nor disproven.

### `unsafely`

We can achieve the same effect as `strategies.uncheckedErrors` by
removing the import, and placing the body of `run` inside an `unsafely` block.

Compare the two variants by selecting ❶ or ❷:
```amok
syntax scala
transform
  replace  Unit =  Unit = unsafely:
  replace
    match
        import strategies.uncheckedErrors

    delete

  before  Global error suppression
  after   Localized error suppression
##
import strategies.uncheckedErrors

@main
def run(path: Text): Unit =
  val json = Json.parse(path.decode[Path].as[File])
  val data = json.as[List[Person]]
  val mean = data.map(_.bmi)/data.length
  Out.println(t"The average BMI is $mean")
```

The code remains unsafe, but is—at least—clearly labeled as such, and the label
is now more specific about which code is unsafe and which is, by default, safe.
This is an improvement to clarity.

We could go further and label just the two expressions which raise errors as
unsafe:

```amok
syntax  scala
transform
  replace  = unsafely:  =
  replace  json =..])  json = unsafely(Json.parse(path.decode[Path].as[File]))
  replace  data =..]]  data = unsafely(json.as[List[Person]])
  before   Whole-method scope
  after    Expression scope
##
@main
def run(path: Text): Unit = unsafely:
  val json = Json.parse(path.decode[Path].as[File])
  val data = json.as[List[Person]]
  val mean = data.map(_.bmi)/data.length
  Out.println(t"The average BMI is $mean")
```

This does not materially affect the semantics of the program: every raisable
error the compiler was aware of remains delimited within an `unsafely` block.

### `safely`

The `unsafely` method has a counterpart called `safely` which is, unremarkably,
_safer_. Like `unsafely`, it can wrap an entire block, or individual
expressions which raise errors.

However, it transforms every `Result` type into an `Optional[Result]`, that is,
`Result | Unset`. This is a contravention of the principle of
[transparent composition](/error-handling-2/#vi-transparent-composition),
and can be seen by the extent of the necessary non-local changes, here:
```amok
syntax  scala
transform
  replace  = unsafely:  =
  replace  json =..])  json = safely(Json.parse(path.decode[Path].as[File]))
  replace  data =..]]  data = safely(json.let(_.as[List[Person]]))
  replace  $mean       ${mean.or(t"error")}
  replace  data.m..th  data.let(_.map(_.bmi)/data.length)
  before   Using unsafely
  after    Using safely
##
@main
def run(path: Text): Unit = unsafely:
  val json = Json.parse(path.decode[Path].as[File])
  val data = json.as[List[Person]]
  val mean = data.map(_.bmi)/data.length
  Out.println(t"The average BMI is $mean")
```

Specifically, we have had to adapt `json.as[List[Person]]` into
`json.let(_.as[List[Person]])`, yet the manifesto requires that it be left
unchanged.

One way of achieving this without writing too much code is to address each
error as soon as it occurs.

Here's the same code with explicit types, showing how _direct-style_ can be
restored provided the `Unset` value of the `Optional` returned from every
`safely` block is eliminated immediately, using `or`:
```amok
syntax  scala
transform
  before   Indirect style
  after    Direct style
  replace  Optional[Json]  Json
  replace  Optional[L..n]]  List[Person]
  replace  Optional[Bmi]    Bmi
  replace  json.le..]])     json.as[List[Person]]
  replace  Person]])        Person]]).or(Nil)
  replace  File]))          File])).or(System.exit(1))
  replace  data.let..th)    data.map(_.bmi)/data.length
  replace  {mean..}         mean
##
@main
def run(path: Text): Unit =
  val json: Optional[Json] =
    safely(Json.parse(path.decode[Path].as[File]))

  val data: Optional[List[Person]] =
    safely(json.let(_.as[List[Person]]))

  val mean: Optional[Bmi] = data.let(_.map(_.bmi)/data.length)
  Out.println(t"The average BMI is ${mean.or(t"error")}")
```

For each `safely` block, we immediately provide an alternative. In the first
case, we terminate the JVM. And in the second case we fall back to an empty
list.

#### Failing Fast and Slow

This "solution" might raise some alarm bells! What happens when we try to
calculate the average of zero items?

Unchecked and unguarded from this possibility, the calculation of `mean`
(which is a `Bmi` instance with an underlying type of `Double`) is
nevertheless _total_: the calculation will give a result of `NaN` ("not a number") when
dividing zero by zero. While `NaN` is indeed not a number, it _is_ a valid
value for the `Bmi` type.

Any subsequent operations involving a `NaN` value will only ever yield more
`NaN` values. In some sense, `NaN` is a representation of erroneousness that is
built in to the `Double` type. That may seem _convenient_ because it means that
operations on `Double`s are _total_ and the type is _closed_. But this approach allows erroneous state to persist long after it occurred,
and we prefer to _fail fast_.

Why?

At the moment the error occurs, all inputs and state relevant to
the failure remain available to help with its diagnosis. But the bit patterns
of 64-bit IEEE 754 floating point numbers that represent `NaN` values cannot
convey enough information to be useful.

If we then allow
the error to go undetected, through many subsequent operations, we can only
speculate about which step in its long ancestry was the root cause. That could
turn a quick fix into a forensic investigation.

We will revisit this in a later post, comparing
`Double`s to `Int`s and the other primitive integral types, which throw _unchecked_ exceptions
when attempting to divide by zero. They fail fast, but they also fail _freely_.

We want to do better than this. But for now, we shall _circumvent_ any runtime
error, and reluctantly accept it is safe enough for us to use `NaN`.

### Recovery with `mend`

The use of `safely` allows us to specify an alternative value if an error
occurs, but without reference to the error's type or value.

The calculation of the `json` value raises `ParseError`s, `PathError`s and
`IoError`s, and we may wish to deal with each of these differently. Here is
a simplistic example which disambiguates by type, and exits the JVM with a
different status code in each case:

```amok
syntax  scala
transform
  replace  unsafely:..File])
      mend:
            case error: ParseError => ExitStatus.Fail(1).terminate()
            case error: PathError  => ExitStatus.Fail(2).terminate()
            case error: IoError    => ExitStatus.Fail(3).terminate()
          .within:
            Json.parse(path.decode[Path].as[File])
  before   Using unsafely
  after    Using mend
##
@main
def run(path: Text): Unit =
  val json =
    unsafely:
      Json.parse(path.decode[Path].as[File])

  val data = unsafely(json.as[List[Person]])
  val mean = data.map(_.bmi)/data.length

  Out.println(t"The average BMI is $mean")
```

In this example, `mend` has not really _mended_ anything at all; it just exited
the JVM in three different ways. `ExitStatus#terminate` is a method with the
return type `Nothing` (like throwing an exception). But the right-hand side of
each case can be any value that conforms to the same type as the body of
`within`. In this example, that's `Json`.

We could construct different `Json` values for each case. But to what end? It
only serves to provide a value for the next line, which tries to read it
as a `List[Person]`.
If our recovery strategy is to fall back to an empty `List`, why bother to
construct that empty list
_as a `Json` value_, only to subsequently parse it as
`Nil`? And why go through the motions of calculating an average of zero values
when we only wish to inform the user of a problem? There are more useful things we
can do with these spare clock cycles!

Instead, let's bring the calculation of `data` and `mean` inside the `within`
block, and have it return a `Text` value showing the average. Then let's
update the `mend` cases to provide alternative results—different strings
explaining the errors.

Here is the adapted code:

```amok
syntax  scala
highlight  unsafely
    This expression must still be marked as unsafe because
    it raises other errors not covered by the mend cases.
##
@main
def run(path: Text): Unit = Out.println:
  mend:
    case error: ParseError => t"There was a parsing error."
    case error: PathError  => t"There was a path error."
    case error: IoError    => t"There was an I/O error."

  .within:
    val json = Json.parse(path.decode[Path].as[File])
    val data = unsafely(json.as[List[Person])
    val mean = data.map(_.bmi)/data.length

    t"The average BMI is $mean"
```

We have unfinished business with the calculation of `data`, which is still
_unsafe_. But we have isolated the remaining unsafe expression, and the
absence of safety is clearly expressed.

Meanwhile, the three cases in the `mend` block are a partial function which
expresses, just as clearly, the types of the errors that are being handled.
Although a macro
provides some magic to make everything work, these cases behave like
cases in any other `match` expression. And the error types—`ParseError`,
`PathError` and `IoError`—are case classes.

Together, this means we can destructure them with pattern matching, like so:

```amok
syntax  scala
transform
  before  Type-based matching
  after   Destructured pattern matching
  replace  error: ParseError..=>
      ParseError(line, _, _) =>
  replace  error: PathError..=>
      PathError(_, reason)   =>
  replace  error: IoError..=>
      IoError(_)             =>
  replace  parsing error  parsing error at $line
  replace  path error     path error because $reason
##
mend:
  case error: ParseError => t"There was a parsing error."
  case error: PathError  => t"There was a path error."
  case error: IoError    => t"There was an I/O error."
```

This is an elegant and expressive way to handle errors, and is structurally
quite similar to a `try`/`catch` expression.

But the most critical difference is that the errors are typechecked. Each
error type in the `mend` block is safely handled, and any expression in its
`within` block is allowed to raise one of those error types. Conversely,
no expression may raise an error type which is **not** handled by the surrounding
code.

#### Syntactic Order

However, one curiosity about a `mend` expression's structure is that
the error handling code appears _before_ the happy path—the opposite order
from a `try`/`catch` expression. This is an implementation restriction in Scala
that constrains what is possible with the
`mend` macro. It requires that the handled error types must be known
syntactically prior to the typechecking of the happy path.

It seems unusual in comparison to `try`/`catch`, but it's more consistent with
respect to everything else in Scala: on every other occasion, the _context_ that
influences the typechecking of an expression is introduced _before_ the
expression.

So although the change in order was necessary because it was impossible to
replicate the structure of `try`/`catch`, we will justify and embrace it on
grounds of consistency with the rest of the language.

### Summary

I have demonstrated a few of the facilities offered by Soundness
(in its Contingency module) to handle errors, from the trivial—a global
import—through simple delimited error handling with `safely` and `unsafely`,
to the more precise and explicit `mend`/`within` construct.

These building blocks are the first steps in a journey which is taking us
from a prototype to something much more robust, resilient and maintainable.

In the next post, I will elaborate further, and explain the features of Scala
that make implementation of the Error Management Manifesto possible—context
functions and contextual values—and introduce a variation on `mend`, called
`tend`.

- Read [Part 2: The Error Management Manifesto](/error-handling-2/)
- Read [Part 4: The Mechanics of Migration](/error-handling-4/)
