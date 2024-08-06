title  The Error Management Manifesto
path   error-handling-2
date   2024-07-27

description
    A declaration of twelve principles for a programming environment
    that's conducive to safe, elegant and maintainable code.
##
# A Manifesto for Error Management

**IN THE FIRST POST** of this series I [set the scene](/error-handling/) for
error handling in Scala. Our desire is to develop reliable programs; software
which does not crash unexpectedly; applications which give their users
accurate, precise and informative feedback in exceptional circumstances.

But we want to achieve that goal with expressive code; following a
continuum from a working prototype to production
durability; yet with the plasticity to adapt to change throughout development
and maintenance, without compromising safety.

I have attempted to encode these desires in a _draft manifesto_. This is a
statement of aspiration for the framework I want to write code in when I'm
developing an application, whether that be a command-line tool, a Scala.JS
script running in a browser, or a server responding to HTTP requests. But this
manifesto is made with the qualified hindsight that it's not just aspirational.
It's achievable.

## The Error Management Manifesto

The manifesto is expressed as twelve principles. I have written it with Scala
in mind, because that is my domain of expertise. But its concepts are more
widely applicable. However, the nature of the requirements means that many
programming languages—notably dynamically-typed languages—are quickly
excluded: they are foundationally incompatible. While for others we can remain
hopeful.

There is one brief note on terminology: we will use the verb _raises_ in
prose, and later in code, to describe the _possibility_ that an expression
will encounter a certain type of error when it is executed and needs to handle
it.

While any partial expression may encounter an error on some occasions and
return a successful value on others, we might be tempted to indicate this
uncertainty by saying that the expression _may_ raise an error or
_could_ raise an error.

But we consider _may_, _could_, _can_ and _might_ to be superfluous: an
expression which _may_ raise an error surely does _on some occasions_. And so
we simply say _it raises an error_, and understand implicitly that the error
is a possibility rather than a certainty.

### The Twelve Principles

#### I. Typed Values

Errors are represented as values, distinguishable from each other at
compiletime by their type and further at runtime by their field values,
which capture certain state relevant to understanding the error.

#### II. Static Analysis

Code is analysed during compilation to statically determine which error
types may arise within every expression or block, and expressed between the
programmer and the compiler through code and error messages.

#### III. Functional Totality

The compiler exhaustively guarantees that every error type raised during the
evaluation of an expression or block is handled: eliminated, transformed into
a different error type, or delegated to another handler.

#### IV. Suppressible Enforcement

Enforcement of error handling may be explicitly suppressed by type, allowing
compilation of expressions whose error cases are not handled, or which are
only partially handled.

#### V. Incremental Enhancement

Code can evolve gradually towards production-readiness as error-handling code
is added, incrementally and continuously, without any need to
rewrite significant amounts of code at any point along that continuum.

#### VI. Transparent Composition

The addition of error handling has no effect on the ability to
compose expressions verbatim. In particular, the principal type of each
expression is unaffected by error handling, though additional information
may be conveyed in an expression's type.

#### VII. Scoped Granularity

Error handling is applicable to structural regions of code varying in
granularity from simple localized expressions to large blocks, delimited as
lexical scopes.

#### VIII. Analytic Obviation

Error handling may be obviated when static analysis determines that an error
of some type can not be raised within an expression or block.

#### IX. Contextual Transmutation

Errors of some type may be routinely transformed into a more
meaningful error of a different type in a different context. Several
heterogeneous error types may be unified into a single homogeneous error
type.

#### X. Delimited Accrual

Errors do not necessarily terminate execution, and contexts may be
delimited to let subsequent errors be aggregated with the first, without
affecting the ultimate failure of the context.

#### XI. Imperishable Concurrency

Errors that occur in concurrent contexts are always handled and processed,
in the thread where they arise or elsewhere; a thread should not fail silently
without its error being handled somewhere.

#### XII. Configurable Diagnostics

The diagnostic information associated with an error, such as its stack
trace, is configurable and may be supplemented with contextual details.
In particular, expensive diagnostic details may be turned off for
performance.

### The Manifesto and Scala

These principles describe the programming environment—the language features and
library support—that I believe is necessary to follow a smooth development
process that leads to correct, maintainable software.

And the implementation is provided by Soundness and several features of Scala 3
which make it possible.

Scala 3's _static analysis (II)_ through its advanced typechecker is the
necessary
foundation of everything that follows, though Scala inherits its
representation of errors as _typed values (I)_ from Java. Scala's contextual
search system is
heavily utilized to enforce _functional totality (III)_ and, combined with
dependent
typing, is the means by which _analytic obviation (VIII)_ can be achieved. The
language's strong focus on nested lexical scopes makes
_scoped granularity (VII)_ the
only natural approach (but with important details enforced by capture
checking), and enables _incremental enhancement (V)_ and
_suppressible enforcement (IV)_.
The relatively new style of writing Scala, called
"direct-style Scala", is the philosophy by which context functions provide
_transparent composition (VI)_.

#### Contingency

The [Contingency](https://soundness.dev/contingency/) module of Soundness
implements macros that provide _contextual transmutation (IX)_ of errors
and _delimited accrual (X)_, as well as some facilities for enhanced,
_configurable diagnostics (XII)_.
And [Parasite](https://github.com/propensive/parasite/) implements structured
concurrency with specific attention to error handling for
_imperishable concurrency (XI)_.

Scala 3 and Contingency is not necessarily a unique combination. Riccardo
Cardin's [raise4s](https://github.com/rcardin/raise4s), which is still in its
infancy, provides similar functionality to Contingency with different syntax,
and later versions may go further. Other statically-typed languages like
Haskell provide a suitable foundation to accommodating every principle.

## Summary

Here, we have seen a set of principles that encodes the features of a desirable
programming framework for writing _safe_ code.

What this manifesto does not include (not directly, at least) is advice
on how to get the most out of these features. As much as possible, the
manifesto coerces developers to use good practices. But there
remains some flexibility within its constraints; flexibility to make superior
or inferior design choices.

That advice to encourage the superior choices
will be shared throughout the remainder of this blog series. But in the
[next installment](/error-handling-3/), I start exploring the real code that
makes the manifesto a reality.

- Read [Part 1: Effective Error Handling](/error-handling/)
- Read [Part 3: Migrating to Safety](/error-handling-3/)
