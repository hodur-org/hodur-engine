[camel-snake-kebab]: https://github.com/qerub/camel-snake-kebab
[circleci-badge]: https://circleci.com/gh/hodur-org/hodur-engine.svg?style=shield&circle-token=aab9b5303cb66945f5048c516d6bbaad08dbba6b
[circleci]: https://circleci.com/gh/hodur-org/hodur-engine
[clojars-badge]: https://img.shields.io/clojars/v/hodur/engine.svg
[clojars]: http://clojars.org/hodur/engine
[clojure-spec]: https://clojure.org/guides/spec
[contentful]: https://www.contentful.com/
[datomic-cloud]: https://docs.datomic.com/cloud/index.html
[github-issues]: https://github.com/hodur-org/hodur-engine/issues
[graphql]: https://graphql.org/
[graphviz]: http://www.graphviz.org/
[hodur-contentful-schema]: https://github.com/hodur-org/hodur-contentful-schema
[hodur-datomic-schema]: https://github.com/hodur-org/hodur-datomic-schema
[hodur-graphviz-schema]: https://github.com/hodur-org/hodur-graphviz-schema
[hodur-lacinia-datomic-adapter]: https://github.com/hodur-org/hodur-lacinia-datomic-adapter
[hodur-lacinia-schema]: https://github.com/hodur-org/hodur-lacinia-schema
[hodur-spec-schema]: https://github.com/hodur-org/hodur-spec-schema
[hodur-visualizer-schema]: https://github.com/hodur-org/hodur-visualizer-schema
[lacinia]: https://github.com/walmartlabs/lacinia
[license-badge]: https://img.shields.io/badge/license-MIT-blue.svg
[license]: ./LICENSE
[logo]: ./docs/logo-tag-line.png
[motivation]: ./docs/MOTIVATION.org
[plugins]: https://github.com/hodur-org/hodur-engine#hodur-plugins
[status-badge]: https://img.shields.io/badge/project%20status-beta-brightgreen.svg

# Hodur Engine

[![CircleCI][circleci-badge]][circleci]
[![Clojars][clojars-badge]][clojars]
[![License][license-badge]][license]
![Status][status-badge]

![Logo][logo]

Hodur is a descriptive domain modeling approach and related collection
of libraries for Clojure.

By using Hodur you can define your domain model as data, parse and
validate it, and then either consume your model via an API making your
apps respond to the defined model or use one of the many plugins to
help you achieve mechanical, repetitive results faster and in a purely
functional manner.

> This repo is the Hodur core engine that parses your model
> definitions and exposes a meta-API around it. For a list of what you
> can do once your model is in Hodur [check here][plugins].

## Motivation

For a deeper insight into the motivations behind Hodur, check the
[motivation doc][motivation].

## Getting Started

Hodur has a highly modular architecture. Hodur Engine (this project)
is always required as it provides the meta-database functions and APIs
consumed by plugins.

Add `hodur-engine` as a dependency in your `deps.edn` file:

``` clojure
  {:deps {hodur/engine {:mvn/version "0.1.7"}}}
```

Either `require` Hodur as part of your `ns` definition or directly:

``` clojure
  (require '[hodur-engine.core :as hodur])
```

In order to initialize an `atom` representing the meta-database of
your model call function `hodur/init-schema`:

``` clojure
  (def meta-db (hodur/init-schema
                '[Person
                  [^String first-name
                   ^String last-name]]))
```

In the above example, we are defining a `Person` entity with a
`first-name` and a `last-name` both tagged as the scalar type
`String`.

Alternatively, Hodur can be initialized by raw EDN paths or from your
classpath using a `File` (i.e. `clojure.java.io/resource`):

``` clojure
  (def meta-db (-> "schemas/person.edn"
                   io/resource
                   hodur/init-path))
```

Hodur's usefulness can be seen when used in conjunction with several
plugins that take care of the mechanical aspects of your
application. For the sake of getting started, we are also adding
`hodur-datomic-schema`, a plugin that creates Datomic Schemas out of
your model to the `deps.edn` file:

``` clojure
  {:deps {hodur/engine         {:mvn/version "0.1.5"}
          hodur/datomic-schema {:mvn/version "0.1.0"}}}
```

You should `require` it any way you see fit:

``` clojure
  (require '[hodur-datomic-schema.core :as hodur-datomic])
```

Let's expand our `Person` model above by "tagging" the `Person` entity
for Datomic. You can read more about the concept of tagging for
plugins in the sessions below but, in short, this is the way we, model
designers, use to specify which entities we want to be exposed to
which plugins.

``` clojure
  (def meta-db (hodur/init-schema
                '[^{:datomic/tag-recursive true}
                  Person
                  [^String first-name
                   ^String last-name]]))
```

The `hodur-datomic-schema` plugin exposes a function called `schema`
that generates your model as a Datomic schema payload:

``` clojure
  (def datomic-schema (hodur-datomic/schema meta-db))
```

When you inspect `datomic-schema`, this is what you have:

``` clojure
  [{:db/ident       :person/first-name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :person/last-name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}]
```

Assuming the Datomic client API is bound to `datomic`, and your
connection to the Database cluster is bound to `db-conn`, you can
simply transact your schema like this:

``` clojure
  (datomic/transact db-conn {:tx-data datomic-schema})
```

Several other plugins are available and you can also write your
own. The following sections detail not only how to model your domain
but also these options in further detail.

## Hodur Plugins

For visualization/documentation:

- [hodur-graphviz-schema][hodur-graphviz-schema]: generates beautiful
  [GraphViz][graphviz] diagrams of your domain
- [hodur-visualizer-schema][hodur-visualizer-schema]: generates a
  dynamically, hot-reloaded version of your domain on a web browser

Schemas for persistent systems:

- [hodur-datomic-schema][hodur-datomic-schema]: generates [Datomic
  Cloud][datomic-cloud] compatible schemas
- [hodur-contentful-schema][hodur-contentful-schema]: generates
  [Contentful][contentful] compatible schemas

Schemas for inbound interfaces:

- [hodur-lacinia-schema][hodur-lacinia-schema]: generates
  [Lacinia][lacinia] ([GraphQL][graphql]) schemas

Schemas for validation/data-generation:

- [hodur-spec-schema][hodur-spec-schema]: generates [Clojure
  Spec][clojure-spec] schemas

Experimental adapters:

- [hodur-lacinia-datomic-adapter][hodur-lacinia-datomic-adapter]:
  experimental utilities for bridging GraphQL queries and mutations
  into Datomic

## Model Definition

### Entities, fields and parameters

In Hodur *Entities* are the highest level representation of a
model. An *entity* has any number of *fields* that qualify such
entity.

For instance, an `employee` entity may have an `employee-number`, a
`name` and a `salary` as three distinct fields. An `entity` can have
as many fields as you need.

*Fields* can have any number of *parameters*. *Parameters* qualify the
field. For instance, a hypothetical `height` field could have a
parameter specifying which `unit` to use when interpreting this
*field* (`CENTIMETERS` or `FEET` for instance).

### Basic structure

Hodur can be initialized by either a series of EDN files (using
function `init-path`) or vectors (using function `init-schema`).

A domain model is a vector of tuples of symbols and sub-vectors. The
symbols represent entity names and the sub-vectors represent fields.

An `Employee` entity with `name` and `salary` as fields could be
defined as:

``` clojure
  [Employee
   [name
    salary]]
```

With this setup we are not specifying what `name` and `salary` are. It
might be a good idea to do something like this:

``` clojure
  [Employee
   [^String name
    ^Float  salary]]
```

Types are defined using a meta payload to the symbol that represents
the field or the parameter. You can read more about scalar types
below.

Types can also be represented by the more explicit meta object:

``` clojure
  [Employee
   [^{:type String} name
    ^{:type Float}  salary]]
```

Entities are also considered types therefore, if an `Employee` has a
`supervisor` who's also an `Employee` you might write:

``` clojure
  [Employee
   [^String   name
    ^Float    salary
    ^Employee supervisor]]
```

You could want a `height` field that can return the employee's height
in a particular unit:

``` clojure
  [Employee
   [^String   name
    ^Float    salary
    ^Employee supervisor
    ^Integer  height [^Unit unit]]

   ^{:enum true}
   Unit
   [CENTIMETERS FEET]]
```

There's quite a bit going on here that you can explore in detail in
the sections below. But here's a summary. First we've added the field
`height` to the `Employee` entity. It returns an `Integer` and it also
expects a parameter called `unit` of the type `Unit`.

We've defined `Unit` separately as an enum (you can see more details
in the sections below). `Unit` can be either `CENTIMETER` or `FEET`.

### Scalar types

Hodur has five primitive scalar types that can be composed with your
own entities to design your model. Four of them are quite
self-explanatory: `String`, `Float`, `Integer` and `Boolean`.

The last two are highly opinionated and are `DateTime` and `ID`.

Hodur's plugins must have reasonable defaults to represent each one of
these scalar types. Plugins may also expose finer grained controls to
manage type precision (for instance 32bit integers vs 64bit integers).

### Cardinalities

One employee may have a series of reportees. This kind of cardinality
is defined with the `:cardinality` meta marker:

``` clojure
  [Employee
   [^{:type String}       name
    ^{:type Float}        salary
    ^{:type Employee
      :cardinality [0 n]} reportees]]
```

In this example we are telling Hodur that `reportees` can be anywhere
from `0` employees to `n` employees.

You can be as specific as you want. A cardinality of `[4]` means
exactly `4` entries; `[3 5]` means `3` to `5`. If `:cardinality` is
unspecified, it's assumed as `[1]`.

### Optional fields and parameters

Fields and parameters are required by default. In other words, plugins
must implement mechanisms to avoid `null` problems if a field or
parameter is mandatory.

If you want to make a field optional, use the `:optional` meta marker
on the field:

``` clojure
  [Employee
   [^{:type String}    first-name
    ^{:type String
      :optional true}  middle-name
    ^{:type String}    last-name]]
```

If you want to make a parameter optional, use the `:optional` meta
marker on the parameter:

``` clojure
  [QueryRoot
   [employees [^{:type String
                 :optional true} search-term]]]
```

A common pattern is to make a parameter optional while also assigning
a default value to it with `:default`:

``` clojure
  [QueryRoot
   [employees-by-location [^{:type String
                             :optional true
                             :default "HQ"} location]]]
```

### Special entity markers

#### Interfaces and Implementations

Entities can be marked as `:interface` which can be used by plugins
that explore such a concept. Entities that implement an interface use
the `:implements` marker to indicate which interface(s) they
implement:

``` clojure
  [^{:interface true}
   Pet
   [^String name]

   ^{:implements Pet}
   Dog
   [^String bark]

   ^{:implements Pet}
   Cat
   [^String mewow]]
```

The `:implements` marker also accepts a vector with a series of
interfaces that the entity implements.

#### Enums

Enums are special kind of entities. They can assume one of the values
defined as fields. Enum fields do not support parameters.

Enums are marked with `:enum`:

``` clojure
  [Employee
   [^String   name
    ^Float    salary
    ^Employee supervisor
    ^Integer  height [^Unit unit]]

   ^{:enum true}
   Unit
   [CENTIMETERS FEET]]
```

#### Unions

Unions are very similar to interfaces, but they don't get to
specify any common fields between the types. They are useful when
a certain field or parameter can be any one of the specified
entities within the union.

In the following example the `search` field of the `QueryRoot` entity
returns a collection of `SearchItem` which are unions of `Employee`
and `Company`:

``` clojure
  [Employee
   [^String name
    ^Float  salary]

   Company
   [^String address]

   ^{:union true}
   SearchItem
   [Employee Company]
   
   QueryRoot
   [^{:type SearchItem
      :cardinality [0 n]}
    search [^String term]]]
```

### Documentation and deprecation

Entities, fields, and parameters can all be documented by using marker
`:doc`.

``` clojure
  [^{:doc "A representation of an Employee"}
   Employee
   [^{:type String
      :doc "The employee's name"}   name
    ^{:type Float
      :doc "The employee's salary"} salary]]
```

Entities, fields, and parameters can additionally be marked for
deprecation by using the marker `:deprecation`. Deprecation is a
string that describes the reasons for the deprecation as well as
points to alternatives.

``` clojure
  [^{:doc "A representation of an Employee"}
   Employee
   [^{:type String
      :doc "The employee's name"}
    name
    ^{:type Float
      :doc "The employee's salary"}
    salary
    ^{:type Float
      :deprecation "This field will be fully removed by December. Please use `name` instead."}
    first-name]]
```

### Tagging

In general, plugins should only process entities, fields, and
parameters that have been tagged for them. I.e. a `datomic` plugin
will have a particular tagging marker such as `:datomic/tag` that
needs to be added to each symbol you want the plugin to process.

The following example tags `Employee` and its fields `first-name` and
`last-name` for the `datomic` plugin.

``` clojure
  [^{:datomic/tag true}
   Employee
   [^{:type String
      :datomic/tag true} first-name
    ^{:type String
      :datomic/tag} last-name]

   Project
   [^{:type String} name]]
```

#### Recursive tagging

Tagging can be very repetitive so Hodur provides features for tagging
in a recursive fashion. The example above could be rewritten with:

``` clojure
  [^{:datomic/tag-recursive true}
   Employee
   [^{:type String} first-name
    ^{:type String} last-name]

   Project
   [^{:type String} name]]
```

This kind of scenario is ideal for entities that have several fields
and/or parameters.

The marker `:<plugin>/tag-recursive` can also have filters such as
`:only` and `:except`.

The following example will only tag the `Employee` entity and the
fields `first-name` and `last-name`:

``` clojure
  [^{:datomic/tag-recursive {:only [Employee first-name last-name]}}
   Employee
   [^{:type String} first-name
    ^{:type String} middle-name
    ^{:type String} last-name]]
```

The following example would achieve the same result as above but by
tagging everything but `middle-name`:

``` clojure
  [^{:datomic/tag-recursive {:except [middle-name]}}
   Employee
   [^{:type String} first-name
    ^{:type String} middle-name
    ^{:type String} last-name]]
```

#### Default tagging

Some times you just want to tag everything you are sending as part of
a group of entities. In these scenarios you need to first name the
very first symbol of your group `default` and then mark it. Hodur will
apply whatever you mark on `default` to all items in the group.

In the following example, Hodur will tag everything for the `datomic`
plugin:

``` clojure
  [^{:datomic/tag true}
   default
   
   Employee
   [^{:type String} first-name
    ^{:type String} last-name]

   Project
   [^{:type String} name]]
```

The special `default` symbol can also be used to carry other markers
down into the group's items but the general usage is for tagging.

### Naming conventions

Hodur does not care about naming conventions. However, it does
delegate naming choices fully to plugins. The way Hodur achieves this
is by internally converting whatever naming convention was used in the
symbols into several options. This is done by leveraging
[camel-snake-kebab][camel-snake-kebab].

## Meta API

Once your model gets parsed, Hodur will retain an in-memory
meta-database that can be queried by either plugins or your
implementation proper.

The API is exposed as a DataScript API atom and DataScript proper is a
dependency of Hodur. Therefore, you can require DataScript and use its
query directly.

The example below uses both `pull` and a Datalog query to return all
the items which are marked with a `:datomic/tag`.

``` clojure
  (require '[datascript.core :as d])

  (d/q '[:find [(pull ?e [*]) ...]
         :where
         [?e :datomic/tag true]]
       @c)
```

Attributes are named with qualified keywords in four different
categories:

1. `:type/...`: all entities (AKA types)
2. `:field/...`: all fields
3. `:param/...`: all parameters
4. `<plugin>/...`: plugin names should qualify keywords (see
   `:datomic/tag` above)

### Naming

For entities, fields, and parameters the provided name in the model is
exposed as either `:type/name`, `:field/name`, and
`:param/name`. Additionally, Hodur generates indexes with:

- `/kebab-case-name`
- `/PascalCaseName`
- `/camelCaseName`
- `/snake_case_name`

### Entity Markers API

Entities have Boolean attributes for interfaces, enums and unions:
`:type/interface`, `:type/enum`, and `:type/union` respectively.

### Field Markers API

TBD: `:field/type` and `:field/parent` (`:field/_parent`) `:field/cardinality`

### Param Markers API

TBD: `:param/type` and `:param/parent` (`:param/_parent`) `:param/cardinality`

### Authoring Plugins

TBD: choose naming convention, use d/q, filter by <plugin>/tag, do your thing

### Bugs

If you find a bug, submit a
[GitHub issue][github-issues]

## Help!

This project is looking for team members who can help this project
succeed! If you are interested in becoming a team member please open
an issue.

## License

Copyright © 2018 Tiago Luchini

Distributed under the MIT License (see [LICENSE][license]).
