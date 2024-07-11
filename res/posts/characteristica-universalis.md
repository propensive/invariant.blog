# Characteristica Universalis

Polymorphism and generic programming make it possible to write _reusable code_.
We can write classes and methods parameterized with types, and their behavior
at each call-site can be dependent on the choice of compiletime type
parameters.

That is to say, the compiler will potentially produce different bytecode,
taking different code paths, for each invocation.

For example, in Soundness, `List[Text]` and a `List[Teletype]` both represent
lists of different kinds of textual elements, and the return type of the
`head` method of either of these lists will be dependent on their type
parameter. This is possible because `List` is a generic type, and the
information carried around statically
in the value's type makes it possible for the Scala compiler to generate
bytecode that correctly casts the `head` value into one type or the other when
it's accessed, even though the bytecode representation of the `List` itself
does not specify its elements' type.

This goes further.

To get the length of the `head` element, we can call `head.length`, whether it
is a `Text` value or a `Teletype` value. This is possible because `length` is
an _extension method_ that is made available on any type for which there is a
corresponding `Textual` instance, which itself defines the `length` method.

Here, not only is the compiler producing bytecode to cast the `head`, but it
will search the scope and relevant companion objects for a contextual instance
of `Text is Textual` for the `List[Text]` or `Teletype is Textual` for the
`List[Teletype]`, and the bytecode will invoke one `length` method or the
other, according to the `List`'s type.

This means that we can call `length` on the `head` of a `List[Text]`,
```scala
def getLength(xs: List[Text]): Int = xs.head.length
```
or of a `List[Teletype]`,
```scala
def getLength(xs: List[Teletype]): Int = xs.head.length
```
and the same expression, `xs.head.length`, will invoke different bytecode in
each case.

And we can even call `length` on a `List` of some unknown type, but only if we
have the appropriate `Textual` instance corresponding to that type—because
that value needs to be available to provide the concrete implementation of
`length`:
```scala
def getLength[TextType: Textual](xs: List[TextType]): Int = xs.head.length
```

## Scala 3.5 Modularity

The `Textual` values corresponding to the types `Text` and `Teletype` were
typed as `Text is Textual` and `Teletype is Textual`, and they take advantage
of Scala 3.5's new _modularity_ feature. This can be enabled with the import:
```scala
import language.experimental.modularity
```

These new typeclass types have the form, `ValueType is TypeclassType`,
and may look unusual at first, when we're more familiar with writing
`TypeclassType[ValueType]`. But they quickly start to feel quite natural. And
the type itself is nothing more than an aesthetic type alias for a familiar
kind of type, `Typeclass { type Self = ValueType }`. That is, a `Typeclass'
with the refinement of a type member, `Self`, equal to `ValueType`.

The name of the type member, `Self`, is not a choice: it's part of the
definition of `is`, shown here in slightly simplified form:
```scala
infix type is[SelfType, TypeclassType <: { type Self }] =
  TypeclassType { type Self = SelfType }
```

The `infix` modifier of the `type` alias makes it possible to write `is`
between its first and second type parameters. So `Text is Textual` is
equivalent to `is[Text, Textual]`, which dealiases to
`Textual { type Self = Text }`, which is allowed only because `Textual` defines
an abstract type member called `Self`.

While type members and type parameters are different concepts in Scala's type
system, for a particular typeclass definition like `Textual`, they are not
interchangeable. But they _do_ have similar semantics, and it is a choice in
the design of the `Textual` typeclass whether to have a type _parameter_, and
to write `Textual[Text]`, or to use a type _member_ and write
`Text is Textual`.

That choice is nuanced, but embracing Scala 3.5 and type members makes a good
default. And one which has been adopted extensively throughout Soundness.

### Universal Access

One of the foremost goals of Soundness is to make it easy for programmers to
understand the code they write. This is hardly an unusual goal: almost every
other API or library was designed with the same intention! So how does
Soundness achieve this?


One example of this is the `Readable` and `Writable` typeclasses in Soundness.

```
val

This is an enormous benefit of a type system as expressive as Scala's: we

- abstract; mention polymorphism
- dependently-typed language
- generic first
- modularity
- language of types
