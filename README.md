# Compiler
Programming exercise in the course of the practical lecture _Fundamentals of Programming_ (IN0002) at the Technical University of Munich.

![alt text](interpreter.jpg)

## Supported Grammar
```
<program>     ::=   <function>*

<function>    ::=   <type> <name> (<params>) { <decl>* <stmt>* }

<params>      ::=   ∈ | (<type> <name>)(, <type> <name>)*

<decl>        ::=   <type> <name> (, <name> )* ;

<type>        ::=   int | int[]

<stmt>        ::=   ; 
                    | <name> = <expr>; 
                    | <name> [ <expr> ] = <expr>; 
                    | <name> = read(); 
                    | write( <expr> ); 
                    | if ( <cond> ) <stmt> 
                    | if ( <cond> ) <stmt> else <stmt>
                    | while ( <cond> ) <stmt>
                    | return <expr>;
                    
<expr>        ::=   <number>
                    | <name>
                    | new int [ <expr> ]
                    | <expr> [ <expr> ]
                    | <name> ( (∈ | <expr>(, <expr>)*) )
                    | length ( <expr> )
                    | ( <expr> )
                    | <unop> <expr>
                    | <expr> <binop> <expr>
                    
<unop>        ::=   -

<binop>       ::=   - | + | * | / | %

<cond>        ::=   true | false
                    | ( <cond> )
                    | <expr> <comp> <expr>
                    | <bunop> ( <cond> )
                    | <cond> <bbinop> <cond>
                    
<comp>        ::=   == | != | <= | < | >= | >

<bunop>       ::=   !

<bbinop>      ::=   && | ||
```
