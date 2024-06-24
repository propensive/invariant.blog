## Characteristica Universalis

Polymorphism and generic programming make it possible to write _reusable code_.
We can write classes and methods parameterized with types, and their behavior
at each call-site can be dependent on the choice of compiletime type
parameters. That is to say, the compiler will produce different bytecode,
taking different code paths, for different invocations.

For example, in Soundness, `List[Text]` and a `List[Teletype]` both represent
lists of textual elements. The return type of the `head` method of either of
these lists will be dependent on their type parameter.

```
val 

This is an enormous benefit of a type system as expressive as Scala's: we 

- abstract; mention polymorphism
- dependently-typed language
- generic first
- modularity
- language of types
