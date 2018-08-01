(ns hodur-engine.test-core
  (:require [clojure.test :refer :all]
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

(deftest test-simple-type-and-field
  (let [res (init-and-pull
             '[Person [name]]
             '[* {:field/_parent [*]}]
             [:type/name "Person"])]
    (is (= "Person" (-> res :type/name)))
    (is (= "name" (-> res :field/_parent first :field/name)))))

(deftest test-primitive-types
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
      (is (= t
             (->> res
                  (filter #(= (:field/name %) n))
                  first
                  :field/type
                  :type/name))))))

(deftest test-implements
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

(deftest test-type-relationships-on-field
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
  ;; Specified arity
  (let [res (init-and-pull
             '[Person
               [^{:type Person
                  :arity [0 n]}
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
           (-> field :field/arity)))))

(deftest test-type-relationships-on-params
  (let [res (init-and-query
             '[Location
               [^Float
                distance [^Unit unit]

                ^{:type String
                  :arity [0 n]}
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
           (-> friends :field/arity)))
    (is (= 2
           (-> friends :param/_parent count)))
    (is (= "Float"
           (-> distance-param :param/type :type/name)))
    (is (= "String"
           (-> name-param :param/type :type/name)))
    (is (= true
           (-> name-param :param/optional)))))

(deftest test-type-markers
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

(deftest test-field-markers
  (let [res (init-and-pull
             '[Type
               [^{:doc "This is the doc"
                  :deprecation "This is the deprecation note"}
                doc
                ^{:type String
                  :arity [4]}
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
           (-> exactly-four :field/arity)))))

(deftest test-namespaced-markers 
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

(deftest test-multiple-schemas
  (let [c1 (engine/init-schema '[A [] B [] C [] D []])
        c2 (engine/init-schema '[A [] B []] '[C [] D []])]
    (is (= @c1 @c2))))

(deftest test-multiple-schemas-different-defaults
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
        (is (:field/name d) "f1")

        (:param/name d)
        (is (:param/name d) "p1")))
    (doseq [d sql]
      (cond
        (:type/name d)
        (is (or (= (:type/name d) "C")
                (= (:type/name d) "D")))
        
        (:field/name d)
        (is (:field/name d) "f2")

        (:param/name d)
        (is (:param/name d) "p2")))))

#_(deftest test-path)

#_(deftest test-multiple-path)

#_(deftest test-tagging-recursively)
