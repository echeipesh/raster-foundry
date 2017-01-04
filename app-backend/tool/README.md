# Raster Foundry Tools

This package contains classes needed to create composiable transformations on tiles, tools. The classes in 'op'
directory are in `geotrellis.raster.op` package as they are preview code and will migrate into GeoTrellis library as
their use solidifies.


## JSON Format

First we need to be able to specify the how to  construct arbitraty ops from JSON.
A JSON node represents a construction of an `Op`. 
Until we have some way to parse numeric operations on primitives like `band1 + band2` we have to assume that we have
access to a library of standard map algebra functions like: `+`, `-`, `/`, etc.

Each JSON object of type "op" is a _definition_ of function application. 
Function definition has:

- name
- parameters - declaration of what the function will need
- composition/body - calling other functions
- arguments - passing own parameters to other functions


### Examples
Looking for cleanly recursive structure.


#### Binary

```json
{
    "definition": "ndvi",
    "params": ["red", "nir"],
    "apply": "/",
    "args": [
        { "apply": "-", "args": ["red", "nir"] },
        { "apply": "+", "args": ["red", "nir"] }
    ]
}
```

```json
{
    "definition": "ndvi",
    "params": ["red", "nir"],
    "result": {   
        "apply" :"/",
        "args": [
            { "apply": "-", "args": ["red", "nir"] },
            { "apply": "+", "args": ["red", "nir"] }
        ]
    }
}
```


```json
{
    "definition": "ndvi2",
    "params": ["notread", "notnir"],
    "apply": "ndvi",
    "args": {"red": "notnir", "nir": "notred"}
}

{
    "apply" :"/",
    "args": [
        { "apply": "-", "args": ["notnir", "notred"] },
        { "apply": "+", "args": ["notnir", "notred"] }
    ]
}
```

 - `definition` is optiona, can be used to refer to this operations in the rest of the file in `fn` field
 - `in` array are reduced from left to right if `fn` is binary. If `fn` is unary the in must not be an array

#### Unary

```json
{
    "apply": "mask",
    "args": ["layer", [23, 44, 56, 65]]
}
```
 - A string in the `in` list refers to a definitiond argument to the funciton.
 - Other type of paramters are parsed based on `fn` value.
 - # this seems hoky .. what if the order if flipped?
 - TODO: How does this look as an actual tile Op?


#### Multiband Output

```json
{
    "definition": "ndvi",
    "params": ["red", "nir"],
    "result": [
        { 
        "apply": "/",
        "args": [
            { "apply": "-", "args": ["red", "nir"] },
            { "apply": "+", "args": ["red", "nir"] }]
        },
        { 
        "apply": "/",
        "args": [
            { "apply": "+", "args": ["red", "nir"] },
            { "apply": "-", "args": ["red", "nir"] }]
        }
   ]
}
```

- In normal function we may combine multiple bands to produces a single band output.
- In multiband operation we are producing multiband output by running multiple functions in parallel.
- Note: object is a single band => array is multiband, how do we turn object into array?

things to figure out:

- How do inputs tie with outputs when you re-use functions ?
- Do names matter? How doe named and position arguments co-exit
  
## Multiband Input

We are expecting the input tile to be a multiband tile, this implis that band index has some kind of predefined meaning.

We can introduce index notation for that parameters:

```json
{
    "definition": "multiband_ndvi",
    "params": ["LC8"],
    "result": { 
        "apply": "/",
        "args": [
            { "apply": "-", "args": ["LC8[4]", "LC8[5]"] },
            { "apply": "+", "args": ["LC8[4]", "LC8[5]"] }
        ]
    }
}
```

One problem with above there is that its not very convenient to to repeat indicies.
We can introduce a function that wraps the variable assignment:
```json
{
    "definition": "LC8_ndvi",
    "params": ["LC8"],
    "result": { 
        "apply": "ndvi",
        "args": ["LC8[4]", "LC8[5]"]
    }
}
```
How is this handled during parsing ?
If the function is referenced as above there are only two choices:
    - perform textural substituation on JSON
    - rebuild the tree with variable renames
    - lets assume its the second

### Function Reuse

We have the ability to define a functions so they can be reused.
What syntax can we use to perform such definitions.
Lets limit it and say it only makes sense to envoke this feature in a `definition` block.
Further, root of the ML Tool must be a `definition` block as it define params.
We will call this the `include` block. Possibly it will be able to refer to function
libraries by their URI as well as full definition in this example:

```json
{
    "definition": "LC8_ndvi",
    "params": ["LC8"],
    "include": [
        {
            "definition": "ndvi",
            "params": ["red", "nir"],
            "apply": "/",
            "args": [
                { "apply": "-", "args": ["red", "nir"] },
                { "apply": "+", "args": ["red", "nir"] }
            ]
        }
    ],
    "result": { 
        "apply": "ndvi",
        "args": ["LC8[4]", "LC8[5]"]
    }
}
```

This means that nested `definition` blocks semantically create scopes.


# Thoughts

When parsing JSON can we lean on HList represetnation of the parameters?
Not sure how this is possible since there will be no input at compile time, so what are we checking?
Parse function can return `Op :: Any :: HNil` eh, not useful.

The only way in which this is useful is if the `HList` is used to assemble the parser implemintation.
But the parser itself must implement some fixed type interface.
