# Effective Error Handling

A crucial difference between prototype- and production-quality software is the
way it handles errors. If a prototype hits an error at runtime, we must
satisfy ourselves with whatever information we have (like a stack trace) to
diagnose the problem, fix the code, inputs or environment, and try it
again—repeating as many times as necessary.

But for production software, this is not acceptable: we need to present a
reliable and informative experience for users who only have blackbox insight. So
the equivalent of our interactive diagnosis and remedy must be included as part
of the implementation, embedded within the code and ready to handle any
deviation from the happy path.

Error handling code is everything that performs the diagnosis and mitigation.
For a prototype in Scala, it might be nothing more than the default handler
that catches exceptions thrown in the `main` method and prints out a stack
trace; for a complex production system, it's not unusual for 90% of the
codebase to be reachable only through an error handler.

> “All happy paths are alike; each unhappy path is unhappy in its own way.”

In this series of blogposts, I will explore the state of the art in error
handling in Scala 3, built upon my experience of writing direct-style Scala 3
for the last three years. I'll present a manifesto for ideal error handling
facilities, and show how we can get very close to achieving it. I'll present
a couple of small libraries from [Soundness](https://soundness.dev/) which make
it easy to handle errors in a direct style.

## Errors

### What is an error?

First, let's make it clear what is meant by an _error_. The term is especially
dependent on context.

Some errors may seem innocuous, like reading an email address without an `@`; or
catastrophic, like discovering the signal from a satellite in orbit has gone
dead. An error may be predictable, like a full disk when writing a file, or
unpredictable like an out-of-memory error. An error may occur frequently, like
dropping a network packet in a mobile app, or infrequently like the complete
failure of a hard disk. An error may be diagnosed easily, like receiving input
outside of an expected range, or less easily, like a bit becoming flipped in a
block of binary data.

All of these can be considered _errors_, though not all are equal. But each is
erroneous only in the context of a particular goal; a goal we can't reach
because of the error. This goal defines what we consider to be the _happy path_
through the code; the sequence of computations that leads to the result.

In most programming languages, we use subroutines, usually called _methods_ in
Scala. Each method has a goal—to construct a result—and its name typically
reflects that goal in some way.

We start each method along a happy path towards the goal. Along the way, the
execution path may branch, according to the input, and the goal may
be reached and a result returned. Or we may end up on an _unhappy path_, and
never able to return to the happy path that returns a result; and the method
will have to fail in some way. We call methods which might fail in this was
_partial_ and methods which do not fail _total_.

### Universal errors

In reality, all methods might fail for reasons we can't easily predict, such as
running out of memory or suffering a power outage. We do not have as many ways
to recover from errors like these in code, and they can occur _anywhere_. On the
JVM, this includes exceptions such as `OutOfMemoryError`, which occurs any time
we need to allocate more memory than we have available, and
`NoSuchMethodException`, which is the runtime manifestation of a mistake in the
way the application was built or configured; but since it wasn't detected before
starting the software, we have already missed the opportunity to address it.

So usually, we will do our best to avoid errors like these, by ensuring we run
our software on a computer with sufficient memory, and avoiding running it in
unusual or untested configurations and environments, and various other ways for
similar errors. But if they _do_ occur while the software is running, they are
usually difficult or impossible to recover from. And thus, it's normal to
handle them globally, and mitigate their knock-on effects by exiting the
application. These will be called _unexpected errors_, but since our options for
handling them are limited, we won't devote much time to them.

Most errors can instead be anticipated, to some degree. We know that if we try
to access a remote URL, it might be unavailable sometimes; and
that if we allow a user to type in an email address, we may get a value
that isn't even in the right format. And with this foresight, we can focus
our attention on addressing errors precisely where they occur. This ensures that
the mitigation from the error—specifically, its diagnosis and recovery—may be
more narrow in scope. There are fewer distinct errors to diagnose, and less
divergence from the happy path to recover from.

### Exceptions

Errors should not be confused with _exceptions_ which are just one possible
representation for errors. Exceptions are native to the Java runtime, with
their own dedicated bytecode operations; and also native to the Scala language,
through the `try`, `catch` and `finally` keywords. An exception encodes the
execution state at the point of error in the form of a stack trace, and
provides good general information about the error's context, but in a form
that's too technical for most end-users.

#### Checked and unchecked exceptions

Java makes an arbitrary distinction between _checked_ and
_unchecked_ exceptions (represented by subtypes of `RuntimeException`). Java
will not allow you to call a method which throws a
checked exception unless you write code to handle or declare that exception
type. And unfortunately it's misleading and rarely helpful:
throwing a checked exception and throwing an unchecked exception abort the happy
path _equally_, so it is no less important to handle unchecked exceptions and
no easier to recover from them.

The justification for the distinction is that
it is cumbersome to handle unchecked exceptions, such as
`ArrayIndexOutOfBoundsException`, when we _often_ know, by reasoning about the
context in ways that the Java compiler can't, that it's not possible it would
ever be thrown.

In Scala, by default, all exceptions are unchecked. This removes the arbitrary
distinction, but also removes the remaining safety of checked exceptions.

## Monadic container types

In addition to exceptions, there are other ways to represent errors in Scala. We
can use types such as `Option` and `Try` to encode both successful results and
errors in an instance of a single hybrid type whose structure allows it to
represent either a success or a failure.

There are advantages to working with container types like these. From the
perspective of a runtime with exceptions, these methods are _total_ because
their errors are values, just like success results, and exceptions are never
_thrown_. It is one of the cornerstones of functional programming that every
function (or method) should return a value, so in a functional programming
environment, we can represent all errors as values, and disregard all handling
of exceptions.

### Compositionality

But container types have a significant disadvantage: they can no longer be
composed as easily as before. A calculation such as,

```scala
def calc(a: Int, b: Int, c: Int): Int = a/c + b/c
```

can fail with a runtime exception if `c` is zero, since the `Int` type does not
have a representation of infinity—instead it throws an exception. But
presented in this form, it's easy to read and understand.

And expressions compose: the expression, `factorial(a/c + b/c)`, includes the
expression from the previous example, verbatim. If the `factorial` method throws
an exception for negative arguments, then its partiality would compose with the partiality of its argument, and now two different types of exception might be
thrown.

But without looking at the implementations, we wouldn't know that we need to
handle either of them, and the compiler would not—by default—offer any help.
Nor would we know that we _don't_ need to handle some arbitrary third exception.

If we are developing a prototype, it may be acceptable. For a proof-of-concept,
our goal is only to prove the concept; to demonstrate the happy path, and we
can start by providing input which leads to success, without a care for input
which will cause failure. Without any error handling, a failure will propagate
up the call stack until it reaches the program's entry point, typically a
`main` method. And unable to do anything better, the JVM will exit, printing a
stack trace. As developers, we can analyse that stack trace, cross-reference it
against the source code, and make some changes before trying again.

However, the division expression written using the `Try` container type could be
expressed as,

```scala
val result: Try[Int] = Try(a/c).flatMap { v => Try(v + b/c) }
```

or using a for-comprehension as,

```scala
val result: Try[Int] = for v <- Try(a/c); w <- Try(v + b/c) yield w
```

It should be clear only how unclear this is, compared to the original
expression. And the syntactic burden is similar for other container types like
`Either` and `Option`.

But representing errors as return values has been shown to be so beneficial
that it has given rise to a wealth of combinators, syntactic machinery and
terminology to make code like this easier to work with. It's possible for this
functional-programming style to become natural for an experienced
programmer, but there is a mental overhead to reading and writing it, and that
burden falls more heavily on beginners.

## The Future

It would be easy to conclude that we must choose between composable but unsafe
code, and safe but verbose code. Through a combination of features in Scala 3,
this is a false dichotomy: composable and safe code is entirely possible,
without compromises. The rest of this blog series will describe the full
expressivity, composibility, safety and power of direct-style Scala.

Using a combination of [Soundness](https://soundness.dev/) libraries, it is
possible to write the expression, `factorial(a/c + b/c)`, and if you choose to,
have the Scala compiler enforce handling of both its exceptions,
`DivisionError` and `FactorialError`—where to omit either one would fail
compilation.

Here is how that looks using the `quell` and `within` constructs:

```scala
quell:
  case DivisionError()   => UserError("c cannot be zero")
  case FactorialError(n) => UserError("a + b must be greater than zero")
.within(factorial(a/c + b/c))
```

And crucially, if we were to omit either of the cases, it would be an error at
compiletime.

## A Manifesto

A desirable error handling system should adhere to the following:

1.
2. Errors should be composable

- bias towards the happy path
- straightforward migration path from unchecked to fully-checked code
- fine-grained control of errors
- composable
- scoped
- checked
