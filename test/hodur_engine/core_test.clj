(ns hodur-engine.core-test
  (:require [camel-snake-kebab.core :refer [->camelCaseKeyword
                                            ->PascalCaseKeyword
                                            ->kebab-case-keyword
                                            ->snake_case_keyword]]
            [clojure.test :refer :all]
            [datascript.core :as d]
            [hodur-engine.core :as engine]))

(defn ^:private init-and-pull
  [schema selector eid]
  (-> schema
      engine/init-schema
      deref
      (d/pull selector eid)))

(defn ^:private init-and-query
  [schema query]
  (->> schema
       engine/init-schema
       deref
       (d/q query)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest simple-type-and-field
  (let [res (init-and-pull
             '[PersonEntity [the-name]]
             '[* {:field/_parent [*]}]
             [:type/name "PersonEntity"])]
    (is (= "PersonEntity"
           (-> res :type/name)))
    (is (= :PersonEntity
           (-> res :type/PascalCaseName)))
    (is (= :person-entity
           (-> res :type/kebab-case-name)))
    (is (= :person_entity
           (-> res :type/snake_case_name)))
    (is (= :personEntity
           (-> res :type/camelCaseName)))
    (is (= "the-name"
           (-> res :field/_parent first :field/name)))
    (is (= :TheName
           (-> res :field/_parent first :field/PascalCaseName)))
    (is (= :the-name
           (-> res :field/_parent first :field/kebab-case-name)))
    (is (= :theName
           (-> res :field/_parent first :field/camelCaseName)))
    (is (= :the_name
           (-> res :field/_parent first :field/snake_case_name)))))

(deftest simple-type-field-param
  (let [res (init-and-pull
             '[PersonEntity
               [the-name [arg_a arg_b]]]
             '[* {:field/_parent
                  [* {:param/_parent [*]}]}]
             [:type/name "PersonEntity"])]
    (is (= 2 (count (-> res :field/_parent first :param/_parent))))    
    (is (= "arg_a"
           (-> res :field/_parent first :param/_parent first :param/name)))
    (is (= :ArgA
           (-> res :field/_parent first :param/_parent first :param/PascalCaseName)))
    (is (= :arg-a
           (-> res :field/_parent first :param/_parent first :param/kebab-case-name)))
    (is (= :argA
           (-> res :field/_parent first :param/_parent first :param/camelCaseName)))
    (is (= :arg_a
           (-> res :field/_parent first :param/_parent first :param/snake_case_name)))))

(deftest primitive-types
  (let [res (init-and-query
             '[Person
               [^String name
                ^Float height
                ^Integer finger-count
                ^Boolean is-employed?
                ^DateTime dob
                ^ID id]]
             '[:find [(pull ?f [* {:field/type [*]}]) ...]
               :where
               [?f :field/name]])]
    (doseq [[n t] [["name" "String"]
                   ["height" "Float"]
                   ["finger-count" "Integer"]
                   ["is-employed?" "Boolean"]
                   ["dob" "DateTime"]
                   ["id" "ID"]]]
      (let [{:keys [type/name type/nature
                    type/camelCaseName
                    type/PascalCaseName
                    type/kebab-case-name
                    type/snake_case_name]}
            (->> res
                 (filter #(= (:field/name %) n))
                 first
                 :field/type)]
        (is (= t name))
        (is (= (->camelCaseKeyword name)
               camelCaseName))
        (is (= (->PascalCaseKeyword name)
               PascalCaseName))
        (is (= (->kebab-case-keyword name)
               kebab-case-name))
        (is (= (->snake_case_keyword name)
               snake_case_name))
        (is (= :primitive nature))))))

(deftest implements
  ;; Only one implement
  (let [res (init-and-pull
             '[Animal [species]
               Herbivore [food-preference]
               ^{:implements Animal}
               Cat [pet-name]]
             '[* {:type/implements [*]}]
             [:type/name "Cat"])]
    (is (= "Animal"
           (-> res :type/implements first :type/name))))
  ;; More implements
  (let [res (init-and-pull
             '[Animal [species]
               Herbivore [food-preference]
               ^{:implements [Animal Herbivore]}
               Human [surname]]
             '[* {:type/implements [*]}]
             [:type/name "Human"])]
    (is (not (nil? (->> res
                        :type/implements
                        (filter #(= (:type/name %) "Animal"))
                        first))))
    (is (not (nil? (->> res
                        :type/implements
                        (filter #(= (:type/name %) "Herbivore"))
                        first))))))

(deftest type-relationships-on-field
  ;; Only one
  (let [res (init-and-pull
             '[Person
               [^Person boss]]
             '[* {:field/_parent
                  [* {:field/type [*]}]}]
             [:type/name "Person"])]
    (is (= "Person"
           (->> res
                :field/_parent
                (filter #(= (:field/name %) "boss"))
                first
                :field/type
                :type/name))))
  ;; Specified cardinality
  (let [res (init-and-pull
             '[Person
               [^{:type Person
                  :cardinality [0 n]}
                friends]]
             '[* {:field/_parent
                  [* {:field/type [*]}]}]
             [:type/name "Person"])
        field (->> res
                   :field/_parent
                   (filter #(= (:field/name %) "friends"))
                   first)]
    (is (= "Person"
           (-> field :field/type :type/name)))
    (is (= '[0 n]
           (-> field :field/cardinality)))))

(deftest type-relationships-on-params
  (let [res (init-and-query
             '[Location
               [^Float
                distance [^Unit unit]

                ^{:type String
                  :cardinality [0 n]}
                friends [^Float
                         distance
                         ^{:type String
                           :optional true}
                         name-starting-with]]

               ^{:enum true}
               Unit [IMPERIAL METRIC]]
             '[:find [(pull ?t [* {:field/_parent
                                   [* {:field/type [*]
                                       :param/_parent
                                       [* {:param/type [*]}]}]}]) ...]
               :where
               [?t :type/name]])
        location (->> res
                      (filter #(= (:type/name %) "Location"))
                      first)
        distance (->> location
                      :field/_parent
                      (filter #(= (:field/name %) "distance"))
                      first)
        unit-type (->> res
                       (filter #(= (:type/name %) "Unit"))
                       first)
        unit-param (->> distance
                        :param/_parent
                        (filter #(= (:param/name %) "unit"))
                        first)
        friends (->> location
                     :field/_parent
                     (filter #(= (:field/name %) "friends"))
                     first)
        distance-param (->> friends
                            :param/_parent
                            (filter #(= (:param/name %) "distance"))
                            first)
        name-param (->> friends
                        :param/_parent
                        (filter #(= (:param/name %) "name-starting-with"))
                        first)]
    (is (= "Float"
           (-> distance :field/type :type/name)))
    (is (= 2
           (-> unit-type :field/_parent count)))
    (is (= true
           (-> unit-param :param/type :type/enum)) )
    (is (= "String"
           (-> friends :field/type :type/name)))
    (is (= '[0 n]
           (-> friends :field/cardinality)))
    (is (= 2
           (-> friends :param/_parent count)))
    (is (= "Float"
           (-> distance-param :param/type :type/name)))
    (is (= "String"
           (-> name-param :param/type :type/name)))
    (is (= true
           (-> name-param :param/optional)))))

(deftest type-markers
  (let [res (init-and-query
             '[^:interface InterfaceType []
               ^:enum EnumType []
               ^:union UnionType []
               ^{:doc "This is the doc"
                 :deprecation "This is the deprecation note"}
               DocType []]
             '[:find [(pull ?t [* {:field/_parent
                                   [* {:field/type [*]
                                       :param/_parent
                                       [* {:param/type [*]}]}]}]) ...]
               :where
               [?t :type/name]])
        interface (->> res
                       (filter #(= (:type/name %) "InterfaceType"))
                       first)
        enum (->> res
                  (filter #(= (:type/name %) "EnumType"))
                  first)
        union (->> res
                   (filter #(= (:type/name %) "UnionType"))
                   first)
        doc (->> res
                 (filter #(= (:type/name %) "DocType"))
                 first)]
    (is (= true
           (-> interface :type/interface)))
    (is (= true
           (-> enum :type/enum)))
    (is (= true
           (-> union :type/union)))
    (is (= "This is the doc"
           (-> doc :type/doc)))
    (is (= "This is the deprecation note"
           (-> doc :type/deprecation)))))

(deftest field-markers
  (let [res (init-and-pull
             '[Type
               [^{:doc "This is the doc"
                  :deprecation "This is the deprecation note"}
                doc
                ^{:type String
                  :cardinality [4]}
                exactly-four]]
             '[* {:field/_parent
                  [* {:field/type [*]
                      :param/_parent
                      [* {:param/type [*]}]}]}]
             [:type/name "Type"])
        doc (->> res
                 :field/_parent
                 (filter #(= (:field/name %) "doc"))
                 first)
        exactly-four (->> res
                          :field/_parent
                          (filter #(= (:field/name %) "exactly-four"))
                          first)]
    (is (= "This is the doc"
           (-> doc :field/doc)))
    (is (= "This is the deprecation note"
           (-> doc :field/deprecation)))
    (is (= [4]
           (-> exactly-four :field/cardinality)))))

(deftest namespaced-markers
  (let [res (init-and-pull
             '[^{:lacinia/identifier "query"
                 :graphviz/color "aquamarine"}
               QueryRoot
               [^{:graphviz/color "blue"}
                blue-field [^{:sql/type "decimal"} decimal-param]]]
             '[* {:field/_parent
                  [* {:field/type [*]
                      :param/_parent
                      [* {:param/type [*]}]}]}]
             [:type/name "QueryRoot"])]
    (is (= "query"
           (-> res :lacinia/identifier)))
    (is (= "aquamarine"
           (-> res :graphviz/color)))
    (is (->> res
             :field/_parent
             (filter #(= (:field/name %) "blue-field"))
             first
             :graphviz/color)
        "blue")
    (is (= "decimal"
           (->> res
                :field/_parent
                (filter #(= (:field/name %) "blue-field"))
                first
                :param/_parent
                first
                :sql/type))))
  ;; alternative queries
  (let [res (init-and-query
             '[^{:lacinia/identifier "query"
                 :graphviz/color "aquamarine"}
               QueryRoot
               [^{:graphviz/color "blue"}
                blue-field [^{:sql/type "decimal"} decimal-param]]]
             '[:find (pull ?e [*]) .
               :where
               [?e :graphviz/color "blue"]])]
    (is (= "blue"
           (-> res :graphviz/color)))
    (is (= "blue-field"
           (-> res :field/name))))
  (let [res (init-and-query
             '[^{:lacinia/identifier "query"
                 :graphviz/color "aquamarine"}
               QueryRoot
               [^{:graphviz/color "blue"}
                blue-field [^{:sql/type "decimal"} decimal-param]]]
             '[:find (pull ?e [*]) .
               :where
               [?e :sql/type]])]
    (is (= "decimal"
           (-> res :sql/type)))
    (is (= "decimal-param"
           (-> res :param/name)))))

(deftest multiple-schemas
  (let [c1 (engine/init-schema '[A [] B [] C [] D []])
        c2 (engine/init-schema '[A [] B []] '[C [] D []])]
    (is (= @c1 @c2))))

(deftest multiple-schemas-different-defaults
  (let [c (engine/init-schema
           '[^{:datomic/tag true}
             default
             A [f1 [p1]] B [f1 [p1]]]
           '[^{:sql/tag true}
             default
             C [f2 [p2]] D [f2]])
        datomic
        (d/q '[:find [(pull ?e [*]) ...]
               :where
               [?e :datomic/tag true]]
             @c)
        sql
        (d/q '[:find [(pull ?e [*])...]
               :where
               [?e :sql/tag true]]
             @c)]
    (is (= 6 (count datomic)))
    (is (= 5 (count sql)))
    (doseq [d datomic]
      (cond
        (:type/name d)
        (is (or (= (:type/name d) "A")
                (= (:type/name d) "B")))

        (:field/name d)
        (is (= "f1" (:field/name d)))

        (:param/name d)
        (is (= "p1" (:param/name d)))))
    (doseq [d sql]
      (cond
        (:type/name d)
        (is (or (= (:type/name d) "C")
                (= (:type/name d) "D")))

        (:field/name d)
        (is (= "f2" (:field/name d)))

        (:param/name d)
        (is (= "p2" (:param/name d)))))))

(deftest multiple-defaults-on-vector
  (let [c (engine/init-schema
           '^{:datomic/tag true}
           [A [f1 [p1]] B [f1 [p1]]]
           '^{:sql/tag true}
           [C [f2 [p2]] D [f2]])
        datomic
        (d/q '[:find [(pull ?e [*]) ...]
               :where
               [?e :datomic/tag true]]
             @c)
        sql
        (d/q '[:find [(pull ?e [*])...]
               :where
               [?e :sql/tag true]]
             @c)]
    (is (= 6 (count datomic)))
    (is (= 5 (count sql)))
    (doseq [d datomic]
      (cond
        (:type/name d)
        (is (or (= (:type/name d) "A")
                (= (:type/name d) "B")))

        (:field/name d)
        (is (= "f1" (:field/name d)))

        (:param/name d)
        (is (= "p1" (:param/name d)))))
    (doseq [d sql]
      (cond
        (:type/name d)
        (is (or (= (:type/name d) "C")
                (= (:type/name d) "D")))
        
        (:field/name d)
        (is (= "f2" (:field/name d)))

        (:param/name d)
        (is (= "p2" (:param/name d)))))))

(deftest tagging-recursively
  (let [c (engine/init-schema
           '[^{:datomic/tag-recursive true}
             A [af [afp]]
             ^{:sql/tag-recursive true}
             B [bf [bfp1 bfp2]]
             C
             [^{:lacinia/tag-recursive true}
              cf [cfp]]])
        datomic (d/q '[:find [(pull ?e [*]) ...]
                       :where
                       [?e :datomic/tag true]] @c)
        sql (d/q '[:find [(pull ?e [*]) ...]
                   :where
                   [?e :sql/tag true]] @c)
        lacinia (d/q '[:find [(pull ?e [*]) ...]
                       :where
                       [?e :lacinia/tag true]] @c)]
    (is (= 3 (count datomic)))
    (is (= 4 (count sql)))
    (is (= 2 (count lacinia)))
    (doseq [d datomic]
      (cond
        (:type/name d)
        (is (= "A" (:type/name d)))
        
        (:field/name d)
        (is (= "af" (:field/name d)))

        (:param/name d)
        (is (= "afp" (:param/name d)))))
    (doseq [d sql]
      (cond
        (:type/name d)
        (is (= "B" (:type/name d)))
        
        (:field/name d)
        (is (= "bf" (:field/name d)))

        (:param/name d)
        (is (or (= "bfp1" (:param/name d))
                (= "bfp2" (:param/name d))))))
    (doseq [d lacinia]
      (cond
        (:field/name d)
        (is (= "cf" (:field/name d)))

        (:param/name d)
        (is (= "cfp" (:param/name d)))))))

(deftest tagging-recursively-with-finer-control
  (let [c (engine/init-schema
           '[^{:datomic/tag-recursive {:only [af1 af3 af3p]}}
             A [af1 [af1p]
                af2
                af3 [af3p]]
             ^{:sql/tag-recursive {:except [bf3]}}
             B [bf1 [bf1p]
                bf2
                bf3]])
        datomic (d/q '[:find [(pull ?e [*]) ...]
                       :where
                       [?e :datomic/tag true]] @c)
        sql (d/q '[:find [(pull ?e [*]) ...]
                   :where
                   [?e :sql/tag true]] @c)]
    (is (= 3 (count datomic)))
    (is (= 4 (count sql)))
    (doseq [d datomic]
      (cond
        (:field/name d)
        (is (or (= "af1" (:field/name d))
                (= "af3" (:field/name d))))

        (:param/name d)
        (is (= "af3p" (:param/name d)))))
    (doseq [d sql]
      (cond
        (:type/name d)
        (is (= "B" (:type/name d)))
        
        (:field/name d)
        (is (or (= "bf1" (:field/name d))
                (= "bf2" (:field/name d))))

        (:param/name d)
        (is (= "bf1p" (:param/name d)))))))

(deftest tagging-recursively-with-finer-control-and-defaults
  (let [c (engine/init-schema
           '[^{:graphviz/tag true}
             default
             ^{:datomic/tag-recursive {:only [af1 af3 af3p]}}
             A [af1 [af1p]
                af2
                af3 [af3p]]
             ^{:sql/tag-recursive {:except [bf3]}}
             B [bf1 [bf1p]
                bf2
                bf3]
             C []])
        graphviz
        (d/q '[:find [(pull ?e [*]) ...]
               :where
               [?e :graphviz/tag true]] @c)
        graphviz-datomic
        (d/q '[:find [(pull ?e [*]) ...]
               :where
               [?e :datomic/tag true]
               [?e :graphviz/tag true]] @c)
        graphviz-sql
        (d/q '[:find [(pull ?e [*]) ...]
               :where
               [?e :sql/tag true]
               [?e :graphviz/tag true]] @c)]
    (is (= 12 (count graphviz)))
    (is (= 3 (count graphviz-datomic)))
    (is (= 4 (count graphviz-sql)))))

(deftest tagging-with-override-instructions
  (let [c (engine/init-schema
           '[^{:sql/tag true}
             default

             B
             [bf1 [^{:sql/tag false} bfp]
              bf2]

             ^{:sql/tag-recursive {:only [cfp]}}
             C
             [cf1 [cfp]
              cf2]

             ^{:sql/tag-recursive {:except [df2]}}
             D
             [df1 [dfp]
              df2]])
        sql
        (d/q '[:find [(pull ?e [*]) ...]
               :where
               [?e :sql/tag true]] @c)]
    (is (= 7 (count sql)))
    (doseq [d sql]
      (cond
        (:type/name d)
        (is (or (= "B" (:type/name d))
                (= "D" (:type/name d))))
        
        (:field/name d)
        (is (or (= "bf1" (:field/name d))
                (= "bf2" (:field/name d))
                (= "df1" (:field/name d))))

        (:param/name d)
        (is (or (= "cfp" (:param/name d))
                (= "dfp" (:param/name d))))))))

(deftest path
  (let [c (engine/init-path "test/schemas/basic")
        datomic
        (d/q '[:find [?e ...]
               :where
               [?e :datomic/tag true]]
             @c)
        graphviz
        (d/q '[:find [?e ...]
               :where
               [?e :graphviz/color]]
             @c)
        types
        (d/q '[:find [?e ...]
               :where
               [?e :type/name]
               [?e :type/nature :user]]
             @c)
        fields
        (d/q '[:find [?e ...]
               :where
               [?e :field/name]]
             @c)
        params
        (d/q '[:find [?e ...]
               :where
               [?e :param/name]]
             @c)
        enums
        (d/q '[:find [?e ...]
               :where
               [?e :type/enum true]]
             @c)
        interfaces
        (d/q '[:find [?e ...]
               :where
               [?e :type/interface true]]
             @c)
        unions
        (d/q '[:find [?e ...]
               :where
               [?e :type/union true]]
             @c)]
    (is (= 10 (count datomic)))
    (is (= 4 (count graphviz)))
    (is (= 7 (count types)))
    (is (= 18 (count fields)))
    (is (= 3 (count params)))
    (is (= 2 (count enums)))
    (is (= 1 (count interfaces)))
    (is (= 1 (count unions)))))

(deftest multiple-path
  (let [lacinia-c (engine/init-path "test/schemas/several/lacinia"
                                    "test/schemas/several/shared")
        datomic-c (engine/init-path "test/schemas/several/datomic"
                                    "test/schemas/several/shared")]
    (is (= 23 (count (d/q '[:find [?e ...]
                            :where
                            [?e :node/type]]
                          @lacinia-c))))
    (is (= 25 (count (d/q '[:find [?e ...]
                            :where
                            [?e :node/type]]
                          @datomic-c))))))

(deftest same-name-fields
  (let [c (engine/init-schema
           '[^{:lacinia/tag true}
             A
             [^{:type String
                :lacinia/tag true}
              name
              ^{:type Integer
                :datomic/tag true}
              name]])
        both
        (d/q '[:find [(pull ?f [* {:field/type [*]}]) ...]
               :where
               [?f :field/name]]
             @c)
        datomic
        (d/q '[:find [(pull ?f [* {:field/type [*]}]) ...]
               :where
               [?f :field/name]
               [?f :datomic/tag true]]
             @c)
        lacinia
        (d/q '[:find [(pull ?f [* {:field/type [*]}]) ...]
               :where
               [?f :field/name]
               [?f :lacinia/tag true]]
             @c)]
    (is (= 2 (count both)))
    (is (= "Integer"
           (-> datomic first :field/type :type/name)))
    (is (= "String"
           (-> lacinia first :field/type :type/name)))))

(deftest field-type-cardinality
  (are [cardinality schema]
      (= cardinality
         (-> (init-and-query
              schema
              '[:find [(pull ?f [*]) ...]
                :where
                [?f :field/name]])
             first
             :field/cardinality))

    ;;[0 n] => [0 'n]
    [0 'n] '[Person
             [^{:type Person
                :cardinality [0 n]}
              friends]]

    ;;n => '[n n]
    '[n n] '[Person
             [^{:type Person
                :cardinality n}
              friends]]

    ;;[2 5] => [2 5]
    [2 5] '[Person
            [^{:type Person
               :cardinality [2 5]}
             friends]]

    ;;none => [1 1]
    [1 1] '[Person
            [^{:type Person}
             friends]]
    
    ;;6 => [6 6]
    [6 6] '[Person
            [^{:type Person
               :cardinality 6}
             friends]]))

(deftest param-type-cardinality
  (are [cardinality schema]
      (= cardinality
         (-> (init-and-query
              schema
              '[:find [(pull ?f [*]) ...]
                :where
                [?f :param/name]])
             first
             :param/cardinality))

    ;;[0 n] => [0 'n]
    [0 'n] '[A [f [^{:cardinality [0 n]} p]]]

    ;;n => '[n n]
    '[n n] '[A [f [^{:cardinality n} p]]]

    ;;[2 5] => [2 5]
    [2 5] '[A [f [^{:cardinality [2 5]} p]]]

    ;;none => [1 1]
    [1 1] '[A [f [p]]]

    ;;6 => [6 6]
    [6 6] '[A [f [^{:cardinality 6} p]]]))

(deftest node-types
  (let [meta-db (engine/init-schema
                 '[A
                   [^String af1
                    ^B af2
                    ^C af3
                    [^Integer af3p1
                     ^C af3p2]]

                   B
                   [^C bf1]

                   C
                   [^B cf1]])]
    (are [node-count node-type]
        (= node-count
           (->> @meta-db
                (d/q '[:find [?e ...]
                       :in ?type $
                       :where
                       [?e :node/type ?type]]
                     node-type)
                count))
      9 :type
      5 :field
      2 :param)))

(deftest union-field-types
  (let [union-fields
        (init-and-query
         '[A
           [af1]

           B
           [bf1]

           ^:union
           C
           [A B]]
         '[:find [(pull ?f [:field/name
                            {:field/union-type [:type/name]}]) ...]
           :where
           [?f :field/union-type]])]
    (is (= 2 (count union-fields)))
    (is (= (-> union-fields first :field/name)
           (-> union-fields first :field/union-type :type/name)))
    (is (= (-> union-fields last  :field/name)
           (-> union-fields last  :field/union-type :type/name)))))
