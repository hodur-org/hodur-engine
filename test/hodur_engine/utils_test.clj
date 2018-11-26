(ns hodur-engine.utils-test
  (:require [camel-snake-kebab.core :refer [->camelCaseKeyword
                                            ->PascalCaseKeyword
                                            ->kebab-case-keyword
                                            ->snake_case_keyword]]
            [clojure.test :refer :all]
            [datascript.core :as d]
            [hodur-engine.core :as engine]
            [hodur-engine.utils :as utils]))

(deftest acyclical-default-topological-sort
  (let [meta-db (engine/init-schema
                 '[A
                   [^String af1
                    ^B af2
                    ^C af3
                    [^Integer af3p1
                     ^C af3p2]]

                   B
                   [^C bf1]

                   ^{:implements Int}
                   C
                   [^B cf1]

                   ^:union
                   U
                   [A B]

                   ^:interface
                   Int
                   [^String intf]])
        sorted (utils/topological-sort meta-db)
        nodes (d/pull-many @meta-db '[:node/type
                                      :field/name
                                      :type/name
                                      :param/name]
                           sorted)]

    (is (= [{:node/type :type, :type/name "A"}
            {:node/type :type, :type/name "String"}
            {:node/type :type, :type/name "Boolean"}
            {:node/type :type, :type/name "ID"}
            {:node/type :type, :type/name "Integer"}
            {:node/type :type, :type/name "Float"}
            {:node/type :type, :type/name "DateTime"}
            {:node/type :type, :type/name "Int"}
            {:node/type :field, :field/name "intf"}
            {:node/type :type, :type/name "C"}
            {:node/type :field, :field/name "af3"}
            {:node/type :param, :param/name "af3p1"}
            {:node/type :param, :param/name "af3p2"}
            {:node/type :type, :type/name "B"}
            {:node/type :field, :field/name "bf1"}
            {:node/type :field, :field/name "cf1"}
            {:node/type :field, :field/name "af2"}
            {:node/type :type, :type/name "U"}
            {:node/type :field, :field/name "B"}
            {:node/type :field, :field/name "A"}
            {:node/type :field, :field/name "af1"}]
           nodes))))

(deftest acyclical-custom-topological-sort
  (let [meta-db (engine/init-schema
                 '[A
                   [^String af1
                    ^B af2
                    ^C af3
                    [^Integer af3p1
                     ^C af3p2]]

                   B
                   [^C bf1]

                   ^{:implements Int}
                   C
                   [^B cf1]

                   ^:union
                   U
                   [A B]

                   ^:interface
                   Int
                   [^String intf]])
        sorted (utils/topological-sort
                meta-db
                {:direction {:type->field-children :rtl
                             :field->param-children :rtl
                             :type->field-return :rtl
                             :type->param-return :rtl
                             :interface->type :rtl
                             :union->type :rtl}})
        nodes (d/pull-many @meta-db '[:node/type
                                      :field/name
                                      :type/name
                                      :param/name]
                           sorted)]
    (clojure.pprint/pprint nodes)
    (is (= [{:node/type :field, :field/name "B"}
            {:node/type :type, :type/name "Boolean"}
            {:node/type :field, :field/name "bf1"}
            {:node/type :field, :field/name "intf"}
            {:node/type :param, :param/name "af3p1"}
            {:node/type :type, :type/name "ID"}
            {:node/type :field, :field/name "cf1"}
            {:node/type :type, :type/name "Integer"}
            {:node/type :type, :type/name "Float"}
            {:node/type :field, :field/name "A"}
            {:node/type :field, :field/name "af2"}
            {:node/type :type, :type/name "DateTime"}
            {:node/type :param, :param/name "af3p2"}
            {:node/type :field, :field/name "af3"}
            {:node/type :type, :type/name "C"}
            {:node/type :type, :type/name "Int"}
            {:node/type :type, :type/name "B"}
            {:node/type :type, :type/name "U"}
            {:node/type :field, :field/name "af1"}
            {:node/type :type, :type/name "A"}
            {:node/type :type, :type/name "String"}]
           nodes))))

(deftest acyclical-tagged-custom-topological-sort
  (let [meta-db (engine/init-schema
                 '[^:myapp/tag
                   default

                   A
                   [^String af1
                    ^B af2
                    ^C af3
                    [^Integer af3p1
                     ^C af3p2]]

                   B
                   [^C bf1]

                   ^{:implements Int}
                   C
                   [^B cf1]

                   ^:union
                   U
                   [A B]

                   ^:interface
                   Int
                   [^String intf]])
        sorted (utils/topological-sort
                meta-db
                {:direction {:type->field-children :rtr
                             :field->param-children :rtr
                             :type->field-return :ltr
                             :type->param-return :ltr
                             :interface->type :ltr
                             :union->type :ltr}
                 :tag :myapp/tag})
        nodes (d/pull-many @meta-db '[:node/type
                                      :field/name
                                      :type/name
                                      :param/name]
                           sorted)]
    (clojure.pprint/pprint nodes)
    (is (= [{:node/type :type, :type/name "A"}
            {:node/type :field, :field/name "intf"}
            {:node/type :param, :param/name "af3p1"}
            {:node/type :field, :field/name "A"}
            {:node/type :type, :type/name "Int"}
            {:node/type :type, :type/name "C"}
            {:node/type :field, :field/name "bf1"}
            {:node/type :field, :field/name "af3"}
            {:node/type :param, :param/name "af3p2"}
            {:node/type :type, :type/name "B"}
            {:node/type :field, :field/name "B"}
            {:node/type :field, :field/name "cf1"}
            {:node/type :field, :field/name "af2"}
            {:node/type :type, :type/name "U"}
            {:node/type :field, :field/name "af1"}]
           nodes))))

(deftest cyclical-tagged-custom-topological-sort
  (let [meta-db (engine/init-schema
                 '[^:myapp/tag
                   default

                   A
                   [^String af1
                    ^B af2
                    ^C af3
                    [^Integer af3p1
                     ^C af3p2]]

                   B
                   [^C bf1]

                   ^{:implements Int}
                   C
                   [^B cf1]

                   ^:union
                   U
                   [A B]

                   ^:interface
                   Int
                   [^String intf]])
        sorted (utils/topological-sort
                meta-db
                {:direction {:type->field-children :ltr
                             :field->param-children :ltr
                             :type->field-return :rtl
                             :type->param-return :rtl
                             :interface->type :rtl
                             :union->type :rtl}
                 :tag :myapp/tag})
        nodes (d/pull-many @meta-db '[:node/type
                                      :field/name
                                      :type/name
                                      :param/name]
                           sorted)]
    (clojure.pprint/pprint nodes)
    (is (= []
           nodes))))
