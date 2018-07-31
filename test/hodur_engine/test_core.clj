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
    (is (= (-> res :type/name) "Person"))
    (is (= (-> res :field/_parent first :field/name) "name"))))

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
      (is (= (->> res
                  (filter #(= (:field/name %) n))
                  first
                  :field/type
                  :type/name)
             t)))))

(deftest test-implements
  ;; Only one implement
  (let [res (init-and-pull
             '[Animal [species]
               Herbivore [food-preference]
               ^{:implements Animal}
               Cat [pet-name]]
             '[* {:type/implements [*]}]
             [:type/name "Cat"])]
    (is (= (-> res :type/implements first :type/name)
           "Animal")))
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
    (is (->> res
             :field/_parent
             (filter #(= (:field/name %) "boss"))
             first
             :field/type
             :type/name)
        "Person"))
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
    (is (-> field :field/type :type/name)
        "Person")
    (is (-> field :field/arity)
        '[0 n])))

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
    (is (-> distance :field/type :type/name)
        "Unit")
    (is (-> distance :field/type :field/_parent count)
        2)
    (is (-> unit-param :param/type :type/enum)
        true)
    (is (-> friends :field/type :type/name)
        "String")
    (is (-> friends :field/arity)
        '[0 n])
    (is (-> friends :param/_parent count)
        2)
    (is (-> distance-param :param/type :type/name)
        "Float")
    (is (-> name-param :param/type :type/name)
        "String")
    (is (-> name-param :param/optional)
        true)))

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
    (is (-> interface :type/interface)
        true)
    (is (-> enum :type/enum)
        true)
    (is (-> union :type/union)
        true)
    (is (-> doc :type/doc)
        "This is the doc")
    (is (-> doc :type/deprecation)
        "This is the deprecation note")))

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
    (is (-> doc :field/doc)
        "This is the doc")
    (is (-> doc :field/deprecation)
        "This is the deprecation note")
    (is (-> exactly-four :field/arity)
        [4])))
