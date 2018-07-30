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
  (let [res (init-and-pull
             '[Animal [species]
               Herbivore [food-preference]
               ^{:implements Animal}
               Cat [pet-name]]
             '[* {:type/implements [*]}]
             [:type/name "Cat"])]
    (is (= (-> res :type/implements first :type/name)
           "Animal")))
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
