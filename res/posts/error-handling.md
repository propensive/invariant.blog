# Effective Error Handling

## Abstract

**THROUGH THIS SERIES** I will explore the state of the art in error
handling in Scala 3, built upon the evolution of my own experience of writing
_direct-style Scala 3_ for the last three years, as well as sixteen years of
Scala development before that, during which I was never fully satisfied with
the state of error handling in Scala.

I'll present a manifesto for library support for idealized error handling, and
show how we can get close to achieving it. I'll present a couple
of components of [Soundness](https://soundness.dev/) which make it possible,
with a couple of caveats, and show how the future promises to eliminate those
caveats.

Our goal is to write code which is _expressive_ and _safe_, combining to give
the programmer superior _confidence_ to read and write code for _reliable_
software, and to maintain that reliability _consistently_ throughout
maintenance and change.

This first post sets the scene and establishes the goals, but later posts will
dive into more concrete code examples.

## Prototypes and Production

What is the difference between prototype- and production-quality software?

Perhaps the biggest difference is how errors are handled. A prototype can
_crash_. And that's fine because it is only a prototype.

But when it does, we
must satisfy ourselves with whatever standard information is revealed in the
crash—often just a stack trace—to diagnose the problem, fix the code, the
inputs or the environment, and try it again. And we must be content to repeat
this as many times as necessary.

But for production software, this is not acceptable: the program must keep calm
and carry on in the face of adversity. It must continue reliably and provide a
consistent level of service to its audience who are, in general, not developers,
and who only have _blackbox_ insight into the problem. They may be able to
follow instructions to fix, mitigate or avoid the problem, but they don't know,
and shouldn't need to know how the software works.

So to bridge the chasm between error and user, the error must be diagnosed in
terms that are meaningful and useful for the user, within the application
domain.

For example, it should be unacceptable to tell an ordinary user that there has
been a _buffer overflow_, because no _ordinary_ user understands the first
thing about buffering. (And even if they did, there is little they could deduce
about how to fix it.) But if the user could be told to adjust a system setting to make the buffer overflow less likely, this would be useful and actionable by
the user.

### Error Handling

When production software deviates from a "happy path", it needs to find its
way back, with or without the user's involvement, if the goal is to be reached.
But before co-opting the user into helping to fix the problem, or even
providing them with a meaningful reason why they can't continue, the software
itself must identify each possible error that may occur and present it to the
user through some channel so they are aware of the problem.

Error handling code is everything that performs this diagnosis and mitigation.
For a prototype in Scala, it might be nothing more than the default handler
that catches exceptions thrown in the `main` method and prints out a stack
trace. In this case the entire program is just one single happy path.

But for a complex production system, it's not unusual for a significant
majority of the codebase to be reachable only through error-handling code.

Error-handling code should therefore adhere to the same standards we apply to
other code we write: it should be typesafe; impossible states should be
unrepresentable; errors should be immutable values.

We should never, for
example, have to _parse_ an error message string to identify the nature of the
error, or its detail.

### Gradualism

Most development starts with a prototype. It may be fragile and bug-ridden,
but it's something for developers to improve iteratively; progressively;
continually. (The alternative approach would be to develop the end-product from
a blank slate—nothing works until, one day, everything works.)

A gradual approach to software development allows for continual testing,
feature development, performance enhancements, and—relevant to us—error
handling. It enables these streams of work to be parallelized, and reduces risk.

And the more gradual this development can be, the more flexibility it provides
developers to choose which errors to handle and how to handle them.

So it is desirable that a system for handling errors should facilitate
incremental error-handling, progressively adding more safety and better
diagnosis.

An example trajectory from prototype to production might start with crashing
upon every error; then reporting generic failures to the user, without crashing;
evolving to presenting precise error diagnostics to the user; before giving the
user no more or less informative detail than they need to address the errors
they can. Different parts of the code could advance through this trajectory at
different times.

## What are Errors?

Let's make it clear what is meant by an _error_. The term is vague, and
especially dependent on context.

Some errors may seem innocuous, like an email address without an `@`; or
catastrophic, like the signal from a satellite in orbit going
dead.

An error may be predictable, like a full disk when writing a file, or
unpredictable like an out-of-memory error when allocating a new object.

An
error may occur frequently, like dropping a network packet in a mobile app, or
infrequently like the complete failure of a hard disk.

An error may be
diagnosed easily, like input that falls outside an expected range, or less
easily, like a bit becoming flipped in transit within a block of binary data.

Some errors may be detected early, like when a stream of data is interrupted.
While others may go a long time evading detection, like a null value retrieved
from a database, incorrectly stored there, years before.

All of these can be considered _errors_, though not all are equal. But each is
erroneous in its relation to a particular goal; a goal we can't reach
because of the error.

An error, for our purposes, is the manifest inability to reach a goal.

### Exceptions

Errors are more general than _exceptions_, which are just one possible
representation for errors on the Java Virtual Machine (JVM). Exceptions are
supported natively in the runtime, with their own dedicated bytecode
operations; and also native to the Scala language, through the `try`, `catch`
and `finally` keywords.

An exception encodes the execution state at the point of error in the form of a
stack trace, and provides good _general_ information about the error's context,
albeit in a form that's too technical for most end-users. But by default, no
incidental state is recorded in an exception, except explicitly in its
construction.

### Stack Traces

Stack traces are familiar to anyone working with the JVM. They
are often a bad sign in production code, because they represent an error which
was not handled in some better way, and as a last resort, its stack was
printed to logs, or elsewhere.

A stack trace is very useful in a world where expressions compose and methods
recurse arbitrarily, because it shows the exact point in the code where the
exception was thrown, as well as every
method that was started, but not completed, leading up to the error.

If we think of execution as a tree of tasks which delegate to subtasks, and
which themselves delegate to further subtasks, a stack trace closely
corresponds to the _relevant_ slice of the task tree from inception to the
point of failure.

But a stack trace is computationally expensive to construct. It requires
strings corresponding to every frame (or method call) in the stack
(one hundred stack
frames is not atypical) to be constructed with details of the method, source
file and line number included in each one.

The cost is not _prohibitive_ for many applications, though: it may be
insignificant to a service running on a single machine that isn't under load.
But for a distributed system, under load and making extensive use of
exceptions, the construction of their stack traces may represent a significant
share of their total computation resources.

To be truly useful for general-purpose and performance-critical representation
of errors, exceptions need to be constructable _configurably_, with or without
stack traces.

#### Checked and unchecked exceptions

Java makes a distinction between _checked_ and _unchecked_ exceptions
(represented by subtypes of `RuntimeException`).

In Java, you are prohibited from calling a method which throws a checked
exception unless you explicitly write code to handle or declare that exception
type. And unfortunately it offers a misleading amount of additional safety:
throwing a checked exception and throwing an unchecked exception abort the
happy path _equally_, so it is no less important to handle unchecked exceptions
and no easier to recover from them.

But for _convenience_, the decision was made for some of the most common
exceptions to be _unchecked_.

The justification for the distinction is that it is cumbersome to write the
code to handle exceptions, such as `ArrayIndexOutOfBoundsException`. And because
we _often_ know, by reasoning about the code in ways that the Java compiler
can't, that it's not possible such an exception would ever be thrown—and we
can convince ourselves that it's safe.

In Scala, by default, all exceptions are unchecked. This removes the arbitrary
distinction, but also removes the remaining safety of "checked" exceptions.

### Universal, Unexpected Errors

In reality, any method might fail for reasons we can't easily predict, such as
running out of memory or suffering a power outage. Within an isolated system—a
program running on a single computer—we do not have as many ways to recover
from such errors in code: often the entire runtime is compromised, and can't
continue. At least, not for long. And they can occur _anywhere_ with little
predictability.

On the JVM, this includes exceptions such as `OutOfMemoryError`, which occurs
any time we need to allocate more memory than we have available, and
`NoSuchMethodException`, which is the runtime manifestation of a mistake in the
way the application was built or configured; but since it wasn't already
detected before starting the software, we have already missed the easiest
opportunity to address it!

So usually, we will do our best to avoid errors like these, by ensuring we run
our program on a computer with sufficient memory, and avoiding running it in
unusual or untested configurations and environments. And in various other ways
for
similar errors. But if they _do_ occur while the software is running, we usually
have no choice but to give up.

It's normal to handle them globally, and mitigate their knock-on effects by
exiting the application as gracefully as possible. These will be called
_unexpected errors_.

Distributed systems can, however, _accommodate_
unexpected errors. The philosophy
endorsed by [Akka](https://akka.io/) is _let it crash_. More specifically, let
a single machine crash, but design the rest of the system around that inherent
unreliability.

This is good advice, with the proviso that we should still try to avoid any
isolated machine within the distributed system from crashing. But the various
methods for keeping distributed systems running smoothly and consistently are
out of scope for this discussion; we shall constrain ourselves to a single JVM;
to keeping isolated systems running smoothly and consistently.

### Scoping

Most errors can be anticipated to some degree. We know that
if we try to access a remote URL, it might be unavailable sometimes; and that
if we allow a user to type in an email address, we may get a value that isn't
in the right format.

With foresight we can focus our attention on addressing errors precisely where
they occur. This ensures that the mitigation of an error—specifically, its
diagnosis and recovery—may be more narrow in scope.

Lexical scopes are important because they delimit a working set of values that
are relevant to the current point of execution. They nest recursively, so
when we enter a new scope, the set of values, methods and capabilities we can
access only grows, and when we leave a scope, it shrinks.

This set of values, methods and capabilities in scope mirrors
both the _need_ and _ability_ to handle errors.

For example, if a method
which raises a `DiskError` is available in the current scope, then should only
be able to call it if we can handle the `DiskError` it may raise. And likewise,
if handling that `DiskError` uses, for example, a `Log` instance, then that
instance must remain accessible.

Conversely, it would make no sense to have error-handling coverage in a region
of the execution path where the partial method is only _sometimes_ accessible,
or where the values needed to handle it are only sometimes available.

Therefore, a lexical scope is perfectly congruent with our needs, and
facilitates the granular delimitation of a region of code for error handling.

## Monads

Besides exceptions, there are other ways to represent errors in Scala. We
can use monadic types such as `Option`, `Either` and `Try` to encode both
successful results and errors using an instance of a single hybrid type
that represents either a successful result, or its absence—with a
different value representing failure.

There are advantages to working with container types such as these.
From the perspective of a runtime like the JVM which has the capability of
returning _abnormally_ with exceptions, methods designed to use monadic return
values are _total_: they always return _normally_ with a value and do not throw
exceptions. Their errors are values, just like their success results.

It is one of the cornerstones of Functional Programming (FP) that every
function (or method) should return a value, so in an FP environment, we can
represent all errors as values, and disregard all handling of exceptions.

These types are _monadic_, which means it is possible to combine them in
standard ways, with methods such as `map`, `flatMap` and other higher-order
combinators like `traverse`. And Scala provides special syntax, the `for` and
`yield` keywords, for the most common compositions.

### Composability

But container types have a significant disadvantage: this composition is
neverthelss more complex than before: whereas we could operate directly on the
result of a partial method, we can now only do so with the aid of
for-comprehensions or additional methods.

So if `f` and `g` are partial methods which throw exceptions, we can compose
them to call `f(g)`. But if that partiality were encoded using monads, then we
have to write `f.flatMap(g)`—and its result must also be `map`ped or
`flatMap`ped accordingly.

Likewise, `a.b` becomes `a.map(_.b)` or `a.flatMap(_.b)`.

These two fundamental operations, selection with a `.` and application with
`(` and `)`, are elegant in their levity; a small burden of one or two
characters. But that explodes to at least six and upto twelve additional
characters when `map` or `flatMap` is required.

This is genuine syntactic
friction which is paid at every composition site.

But this FP philosophy of representing errors as return values has nonetheless
seen widespread adoption, and has been shown to be so beneficial that it has
given rise to a wealth of
combinators, syntactic machinery and terminology to make code like this easier
to work with.

And it's entirely possible for the Functional style to become natural for an
experienced programmer. Though the mental overhead to reading and writing it is
real, and that burden falls more heavily on beginners.

### Happy Paths

In most programming languages, we use subroutines, which are called _methods_
in Scala.
Each method has a goal—to construct a result—and in clear, well-written code,
its name
typically reflects that goal in some way, and its _happy path_ leads directly
to it.

We might think about happy paths in the context of an entire program, or in the
finer-grained context of a method. But in the abstract, a program is no
different from a method, with inputs and outputs. We can think of a program as
just its `main` method, _mutatis mutandis_.

The "happiness" of a path is a reflection of the progress of that path
towards its nominal goal.

Along the way, the execution path may branch conditionally on the input, and the
goal may be reached and a result returned. Or we may be diverted onto an
_unhappy path_, not directly able to return to the happy path that yields
success; and the method must exhibit failure in some way.

But an alternative perspective is that the there are no _happy_ or _unhappy_
paths, just different paths; there is no _diversion_, only _conditional
branching_; there are no
_success_ and _failure_ results, only _different_ results; and all routes
through the method are equal.

This is an exaggerated view which few programmers would genuinely hold. But FP
takes an approach that's closer to this extreme, by eschewing some
language features to favor the desired happy path
over the less desirable alternative paths.

Leo Tolstoy didn't quite say,

> “All happy paths are alike; each unhappy path is unhappy in its own way.”

but the [Anna Karenina
Principle](https://en.wikipedia.org/wiki/Anna_Karenina_principle) nevertheless
states that:

> A deficiency in any one of a number of factors dooms an endeavor to failure.

There are many ways to deviate from happy path, but there is only one way to
reach the end goal. The happy path is the unique route which chains together an
uninterrupted sequence of successes to get there.

And this distinction is significant. The happy path _is_ distinguished from the
others because it's unique. And for the clarity of the programmer who writes
the code, and for their
coworker who reads it later, the code's structure _should_
reflect its privileged status in making progress towards the goal, expressed
through the name of the method.

The use of _exceptions_ to represent diversions onto an unhappy path is
consistent with this: they are intended to represent _exceptional_ cases, with
their own exit routes from a method, leaving the full bandwidth of the method's
return type to be devoted to the success value.

This is the essence of _direct-style Scala_.

## The Solution

It would be easy to conclude that we must choose between _composable but unsafe_
code, and _safe but verbose_ code. Through a combination of features in Scala 3,
this is now a false dichotomy: _composable and safe_ code is within reach,
without compromises.

The rest of this series will describe the full expressivity, composibility,
safety and power of direct-style Scala.

Having set the stage, in the next post
we will start exploring real-world error-handling in Scala 3 code with
Soundness.
